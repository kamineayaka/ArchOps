package com.archops.asset.dbquery;

import com.archops.approval.domain.Approval;
import com.archops.approval.domain.ApprovalStatus;
import com.archops.approval.domain.RiskLevel;
import com.archops.approval.service.ApprovalService;
import com.archops.approval.service.RiskClassifier;
import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.user.domain.User;
import com.archops.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbQueryService {

    public static final int DEFAULT_MAX_ROWS = 500;
    public static final int DEFAULT_TIMEOUT_SEC = 30;

    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final CredentialCipher credentialCipher;
    private final DbEngineRegistry engineRegistry;
    private final SqlAccessClassifier sqlAccessClassifier;
    private final RiskClassifier riskClassifier;
    private final ApprovalService approvalService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AssetAclService assetAclService;

    public DbQueryService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            DbEngineRegistry engineRegistry,
            SqlAccessClassifier sqlAccessClassifier,
            RiskClassifier riskClassifier,
            ApprovalService approvalService,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            AssetAclService assetAclService) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.engineRegistry = engineRegistry;
        this.sqlAccessClassifier = sqlAccessClassifier;
        this.riskClassifier = riskClassifier;
        this.approvalService = approvalService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.assetAclService = assetAclService;
    }

    /**
     * Query console entry: READ executes immediately; WRITE requires prior APPROVED approval.
     */
    public DbQueryResponse submit(
            Long userId, Collection<String> roles, Long assetId, String sql, Long approvalId) {
        // Check before creating an approval so unauthorized assets cannot be probed.
        assetAclService.requireAssetAccess(userId, roles, assetId);
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        SqlAccessKind access = sqlAccessClassifier.classify(sql);
        String argsJson = argsJson(assetId, sql);
        RiskLevel risk = riskClassifier.classify("db_query", argsJson);
        boolean mutating = access == SqlAccessKind.WRITE;

        if (mutating) {
            if (approvalId == null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("tool", "db_query");
                payload.put("arguments", argsJson);
                payload.put("source", "query_console");
                Approval pending = approvalService.createPending(
                        userId, "tool:db_query", "asset:" + assetId, risk, payload);
                audit(user, assetId, sql, access, risk, "PENDING_APPROVAL", pending.getId(), null);
                return DbQueryResponse.pending(
                        pending.getId(), risk.name(), "写操作已提交审批，通过后请携带 approvalId 再次执行");
            }
            assertApprovedWrite(userId, assetId, sql, approvalId);
        }

        return executeAndAudit(user, roles, assetId, sql, access, risk);
    }

    /** Agent tool / post-approval execution (gate already applied by ToolExecutorService). */
    public DbQueryResponse runForTool(
            Long userId, Collection<String> roles, Long assetId, String sql) {
        assetAclService.requireAssetAccess(userId, roles, assetId);
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        SqlAccessKind access = sqlAccessClassifier.classify(sql);
        RiskLevel risk = riskClassifier.classify("db_query", argsJson(assetId, sql));
        return executeAndAudit(user, roles, assetId, sql, access, risk);
    }

    private DbQueryResponse executeAndAudit(
            User user,
            Collection<String> roles,
            Long assetId,
            String sql,
            SqlAccessKind access,
            RiskLevel risk) {
        // Re-check immediately before execution in case access changed while approval was pending.
        assetAclService.requireAssetAccess(user.getId(), roles, assetId);
        long started = System.nanoTime();
        try {
            DbEngine.DbQueryResult result = executeJdbc(assetId, sql, access);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            String msg = result.truncated()
                    ? "结果已截断至 " + DEFAULT_MAX_ROWS + " 行"
                    : (access == SqlAccessKind.WRITE
                            ? "执行成功，影响行数 " + result.updateCount()
                            : "查询成功");
            audit(user, assetId, sql, access, risk, "SUCCESS", null, result.rowCount());
            return DbQueryResponse.executed(
                    access == SqlAccessKind.WRITE,
                    risk.name(),
                    result.columns(),
                    result.rows(),
                    result.rowCount(),
                    result.truncated(),
                    result.updateCount(),
                    elapsedMs,
                    msg);
        } catch (BusinessException ex) {
            audit(user, assetId, sql, access, risk, "FAILED", null, null);
            throw ex;
        } catch (Exception ex) {
            audit(user, assetId, sql, access, risk, "FAILED", null, null);
            String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DB_QUERY_FAILED", "数据库查询失败: " + msg);
        }
    }

    private DbEngine.DbQueryResult executeJdbc(Long assetId, String sql, SqlAccessKind access) throws Exception {
        Asset asset = loadDatabaseAsset(assetId);
        SshCredential credential = sshCredentialRepository
                .findByAssetIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST, "CREDENTIAL_REQUIRED", "请先为该 DATABASE 资产配置凭证"));
        if (!StringUtils.hasText(credential.getUsername())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CREDENTIAL_REQUIRED", "数据库用户名未配置");
        }
        if (credential.getAuthType() != null
                && credential.getAuthType() != com.archops.asset.domain.SshAuthType.PASSWORD) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "DB_AUTH_UNSUPPORTED",
                    "DATABASE 仅支持 PASSWORD 认证，请更新凭证");
        }
        if (!StringUtils.hasText(asset.getHost())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NO_HOST", "请填写主机地址");
        }
        int port = asset.getPort() != null && asset.getPort() > 0 ? asset.getPort() : 5432;
        String secret = credentialCipher.decrypt(credential.getSecretCipher(), credential.getSecretIv());
        String database = readMeta(asset.getMetadata(), "database");
        String engineId = readMeta(asset.getMetadata(), "engine");
        DbEngine engine = engineRegistry.resolve(engineId);

        try (Connection connection = engine.open(
                asset.getHost().trim(), port, database, credential.getUsername().trim(), secret)) {
            // Defense in depth: mark READ sessions read-only so mutations fail even if classifier misses.
            if (access == SqlAccessKind.READ) {
                connection.setReadOnly(true);
            }
            return engine.execute(connection, sql, DEFAULT_MAX_ROWS, DEFAULT_TIMEOUT_SEC);
        }
    }

    private void assertApprovedWrite(Long userId, Long assetId, String sql, Long approvalId) {
        Approval approval = approvalService.getRequired(approvalId);
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "APPROVAL_NOT_APPROVED", "审批未通过，无法执行写操作");
        }
        if (!userId.equals(approval.getRequesterId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "APPROVAL_OWNER_MISMATCH", "只能使用本人的审批单执行");
        }
        Map<String, Object> payload = parsePayload(approval.getPayload());
        if (!"db_query".equals(String.valueOf(payload.get("tool")))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "APPROVAL_TOOL_MISMATCH", "审批单工具不匹配");
        }
        Map<String, Object> args = parseArgs(payload.get("arguments"));
        Long approvedAsset = asLong(args.get("assetId"));
        String approvedSql = args.get("sql") != null ? String.valueOf(args.get("sql")) : null;
        if (approvedAsset == null || !approvedAsset.equals(assetId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "APPROVAL_ASSET_MISMATCH", "审批单资产不匹配");
        }
        if (approvedSql == null || !approvedSql.equals(sql)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "APPROVAL_SQL_MISMATCH", "审批单 SQL 不匹配");
        }
    }

    private Asset loadDatabaseAsset(Long assetId) {
        Asset asset = assetRepository
                .findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
        if (asset.getKind() != AssetKind.DATABASE) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSET_NOT_DATABASE", "仅 DATABASE 资产支持 SQL 查询");
        }
        return asset;
    }

    private void audit(
            User user,
            Long assetId,
            String sql,
            SqlAccessKind access,
            RiskLevel risk,
            String status,
            Long approvalId,
            Integer rowCount) {
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("assetId", assetId);
            detail.put("access", access.name());
            detail.put("sqlPreview", previewSql(sql));
            if (approvalId != null) {
                detail.put("approvalId", approvalId);
            }
            if (rowCount != null) {
                detail.put("rowCount", rowCount);
            }
            auditService.record(new AuditService.AuditEntry(
                    user.getId(),
                    user.getUsername(),
                    "db.query",
                    "asset:" + assetId,
                    risk.name(),
                    status,
                    objectMapper.writeValueAsString(detail),
                    null,
                    null));
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private String argsJson(Long assetId, String sql) {
        try {
            return objectMapper.writeValueAsString(Map.of("assetId", assetId, "sql", sql));
        } catch (Exception ex) {
            return "{\"assetId\":" + assetId + "}";
        }
    }

    private static String previewSql(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 240 ? oneLine.substring(0, 240) + "…" : oneLine;
    }

    private String readMeta(String metadata, String field) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode value = node.get(field);
            return value != null && !value.isNull() ? value.asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(Object argumentsRaw) {
        if (argumentsRaw instanceof String argsJson) {
            try {
                return objectMapper.readValue(argsJson, new TypeReference<>() {});
            } catch (Exception ex) {
                return Map.of();
            }
        }
        if (argumentsRaw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
