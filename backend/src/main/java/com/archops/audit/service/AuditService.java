package com.archops.audit.service;

import com.archops.audit.domain.AuditLog;
import com.archops.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit log with SHA-256 hash chain for tamper detection.
 * Each entry references the previous entry's hash and stores its own hash,
 * so any modification breaks the chain and is detectable by {@link #verifyChain()}.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog record(AuditEntry entry) {
        AuditLog last = repository.findTopByOrderByIdDesc();
        String prevHash = last != null ? last.getCurrHash() : "GENESIS";

        AuditLog log = new AuditLog();
        log.setActorId(entry.actorId());
        log.setActorName(entry.actorName());
        log.setAction(entry.action());
        log.setResource(entry.resource());
        log.setRiskLevel(entry.riskLevel());
        log.setStatus(entry.status());
        log.setDetail(entry.detail() == null || entry.detail().isBlank() ? "{}" : entry.detail());
        log.setIpAddress(entry.ipAddress());
        log.setUserAgent(entry.userAgent());
        log.setPrevHash(prevHash);
        log.setCreatedAt(Instant.now());
        log.setCurrHash(computeHash(prevHash, log));
        return repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public boolean verifyChain() {
        String expectedPrev = "GENESIS";
        for (AuditLog log : repository.findAllByOrderByIdAsc()) {
            if (!expectedPrev.equals(log.getPrevHash())) {
                return false;
            }
            if (!computeHash(log.getPrevHash(), log).equals(log.getCurrHash())) {
                return false;
            }
            expectedPrev = log.getCurrHash();
        }
        return true;
    }

    private String computeHash(String prevHash, AuditLog log) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = prevHash + "|" + log.getAction() + "|" + log.getResource() + "|"
                    + log.getStatus() + "|" + log.getDetail() + "|" + log.getCreatedAt();
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute audit hash", ex);
        }
    }

    public record AuditEntry(
            Long actorId,
            String actorName,
            String action,
            String resource,
            String riskLevel,
            String status,
            String detail,
            String ipAddress,
            String userAgent) {}
}
