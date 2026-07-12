package com.cloudops.terminal;

import com.cloudops.terminal.pool.PooledSshHandle;
import com.cloudops.terminal.pool.SshConnectionPool;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.cloudops.common.config.WebSocketEndpoint;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Web SSH terminal bridged through the shared {@link SshConnectionPool}.
 */
@Component
@WebSocketEndpoint("/ws/terminal")
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    private final SshConnectionPool sshConnectionPool;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, TerminalSession> activeSessions = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(SshConnectionPool sshConnectionPool, ObjectMapper objectMapper) {
        this.sshConnectionPool = sshConnectionPool;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        Long assetId = parseAssetId(extractQuery(wsSession, "assetId"));
        Long userId = (Long) wsSession.getAttributes().get("userId");
        if (userId == null || assetId == null) {
            wsSession.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        PooledSshHandle pooled = null;
        try {
            pooled = sshConnectionPool.acquire(userId, assetId);
            ClientSession sshSession = pooled.session();

            ChannelShell channel = sshSession.createShellChannel();
            channel.setPtyType("xterm-256color");
            channel.setPtyColumns(120);
            channel.setPtyLines(30);
            channel.open().verify(15_000);

            OutputStream inputToShell = channel.getInvertedIn();
            InputStream remoteOut = channel.getInvertedOut();

            Thread reader = new Thread(() -> pumpRemoteOutput(wsSession, remoteOut), "ssh-reader-" + wsSession.getId());
            reader.setDaemon(true);
            reader.start();

            activeSessions.put(wsSession.getId(), new TerminalSession(pooled, channel, inputToShell, reader));
            log.info("Terminal session opened (pooled): {} user={} asset={}", wsSession.getId(), userId, assetId);
        } catch (Exception ex) {
            if (pooled != null) {
                pooled.close();
            }
            log.error("Failed to open SSH terminal for asset {}", assetId, ex);
            wsSession.sendMessage(new TextMessage("\r\n[CloudOps] SSH connection failed: " + ex.getMessage() + "\r\n"));
            wsSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void pumpRemoteOutput(WebSocketSession wsSession, InputStream remoteOut) {
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = remoteOut.read(buf)) > 0 && wsSession.isOpen()) {
                wsSession.sendMessage(new TextMessage(new String(buf, 0, n)));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        TerminalSession ts = activeSessions.get(wsSession.getId());
        if (ts == null) {
            return;
        }

        String payload = message.getPayload();
        if (payload.startsWith("{")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> control = objectMapper.readValue(payload, Map.class);
            if ("resize".equals(control.get("type"))) {
                int cols = ((Number) control.getOrDefault("cols", 120)).intValue();
                int rows = ((Number) control.getOrDefault("rows", 30)).intValue();
                ts.channel.setPtyColumns(cols);
                ts.channel.setPtyLines(rows);
                ts.channel.sendWindowChange(cols, rows, cols * 8, rows * 16);
                return;
            }
        }

        ts.inputToShell.write(message.asBytes());
        ts.inputToShell.flush();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        TerminalSession ts = activeSessions.remove(wsSession.getId());
        if (ts != null) {
            closeQuietly(ts);
            log.info("Terminal session closed: {}", wsSession.getId());
        }
    }

    private String extractQuery(WebSocketSession session, String key) {
        String query = session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private Long parseAssetId(String raw) {
        try {
            return raw != null ? Long.valueOf(raw) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void closeQuietly(TerminalSession ts) {
        try {
            ts.channel.close(true);
        } catch (Exception ignored) {
        }
        ts.reader.interrupt();
        ts.pooled.close();
    }

    private record TerminalSession(
            PooledSshHandle pooled,
            ChannelShell channel,
            OutputStream inputToShell,
            Thread reader) {}
}
