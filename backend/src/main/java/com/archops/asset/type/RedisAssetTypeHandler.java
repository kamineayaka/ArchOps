package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetQueryResponse;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.common.exception.BusinessException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redis asset: TCP + RESP PING probe; Query shell allows PING / GET / INFO only.
 */
@Component
public class RedisAssetTypeHandler extends AbstractAssetTypeHandler {

    private static final int TIMEOUT_MS = 5_000;

    @Override
    public String type() {
        return AssetKind.REDIS.name();
    }

    @Override
    public int defaultPort() {
        return 6379;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }

    @Override
    public ConnectAction connectAction() {
        return ConnectAction.QUERY;
    }

    @Override
    public void validateCreate(AssetRequest req) {
        validateCommon(req);
        requireHost(req);
    }

    @Override
    public void validateUpdate(AssetRequest req) {
        validateCommon(req);
        requireHost(req);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectivityContext ctx) {
        long started = System.nanoTime();
        try {
            String reply = respCommand(ctx, List.of("PING"));
            if ("PONG".equalsIgnoreCase(reply.trim())) {
                return new TestConnectionResponse(true, elapsedMs(started), "Redis PING → PONG");
            }
            return new TestConnectionResponse(false, elapsedMs(started), "意外响应: " + reply);
        } catch (BusinessException e) {
            return new TestConnectionResponse(false, elapsedMs(started), e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new TestConnectionResponse(false, elapsedMs(started), "连接失败: " + msg);
        }
    }

    @Override
    public AssetQueryResponse executeReadonlyQuery(ConnectivityContext ctx, String statement) {
        try {
            List<String> parts = tokenize(statement);
            if (parts.isEmpty()) {
                return AssetQueryResponse.failure("请输入 Redis 命令（PING / GET / INFO）");
            }
            String cmd = parts.getFirst().toUpperCase(Locale.ROOT);
            if (!cmd.equals("PING") && !cmd.equals("GET") && !cmd.equals("INFO")) {
                return AssetQueryResponse.failure("仅允许只读命令: PING, GET, INFO");
            }
            if (cmd.equals("GET") && parts.size() < 2) {
                return AssetQueryResponse.failure("GET 需要 key");
            }
            String reply = respCommand(ctx, parts);
            return AssetQueryResponse.text(reply);
        } catch (Exception e) {
            return AssetQueryResponse.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String respCommand(ConnectivityContext ctx, List<String> args) throws Exception {
        if (!StringUtils.hasText(ctx.host())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NO_HOST", "请填写主机地址");
        }
        int port = ctx.port() != null && ctx.port() > 0 ? ctx.port() : defaultPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ctx.host().trim(), port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            if (StringUtils.hasText(ctx.secret())) {
                writeResp(out, List.of("AUTH", ctx.secret()));
                String auth = readResp(in);
                if (auth != null && auth.startsWith("ERR")) {
                    throw new IllegalStateException("AUTH 失败: " + auth);
                }
            }
            writeResp(out, args);
            return readResp(in);
        }
    }

    private static void writeResp(BufferedOutputStream out, List<String> args) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.size()).append("\r\n");
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(bytes.length).append("\r\n");
            sb.append(arg).append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readResp(BufferedInputStream in) throws Exception {
        int prefix = in.read();
        if (prefix < 0) {
            return "";
        }
        if (prefix == '+') {
            return readLine(in);
        }
        if (prefix == '-') {
            return "ERR " + readLine(in);
        }
        if (prefix == '$') {
            String lenLine = readLine(in);
            int len = Integer.parseInt(lenLine.trim());
            if (len < 0) {
                return "(nil)";
            }
            byte[] buf = in.readNBytes(len);
            in.read(); // \r
            in.read(); // \n
            return new String(buf, StandardCharsets.UTF_8);
        }
        if (prefix == '*') {
            String countLine = readLine(in);
            int count = Integer.parseInt(countLine.trim());
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                parts.add(readResp(in));
            }
            return String.join("\n", parts);
        }
        if (prefix == ':') {
            return readLine(in);
        }
        return readLine(in);
    }

    private static String readLine(BufferedInputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static List<String> tokenize(String statement) {
        List<String> parts = new ArrayList<>();
        if (statement == null) {
            return parts;
        }
        for (String p : statement.trim().split("\\s+")) {
            if (!p.isBlank()) {
                parts.add(p);
            }
        }
        return parts;
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
