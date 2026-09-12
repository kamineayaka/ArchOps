package com.archops.observed.service;

import com.archops.agent.dto.AgentHeartbeatRequest;
import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.common.exception.BusinessException;
import com.archops.common.json.PersistentJson;
import com.archops.curated.service.UnboundDraftService;
import com.archops.observed.domain.IdentityLostMark;
import com.archops.observed.domain.UnboundBindMemory;
import com.archops.observed.domain.UnboundObservationCandidate;
import com.archops.observed.domain.UnboundReason;
import com.archops.observed.dto.IdentityLostResponse;
import com.archops.observed.dto.UnboundCandidateResponse;
import com.archops.observed.mapper.IdentityLostMarkMapper;
import com.archops.observed.mapper.UnboundBindMemoryMapper;
import com.archops.observed.mapper.UnboundObservationCandidateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Unbound-candidate listing, identity-lost GET, and ingest mapping for unmatched snapshots.
 * Heartbeat / actual-where stay on ObservedTruthService.
 */
@Service
public class UnboundObservationService {

    private static final TypeReference<Map<String, String>> LABEL_MAP = new TypeReference<>() {
    };

    private final UnboundObservationCandidateMapper unboundMapper;
    private final UnboundBindMemoryMapper unboundBindMemoryMapper;
    private final IdentityLostMarkMapper identityLostMarkMapper;
    private final UnboundDraftService unboundDraftService;
    private final PersistentJson persistentJson;

    public UnboundObservationService(
            UnboundObservationCandidateMapper unboundMapper,
            UnboundBindMemoryMapper unboundBindMemoryMapper,
            IdentityLostMarkMapper identityLostMarkMapper,
            UnboundDraftService unboundDraftService,
            PersistentJson persistentJson
    ) {
        this.unboundMapper = unboundMapper;
        this.unboundBindMemoryMapper = unboundBindMemoryMapper;
        this.identityLostMarkMapper = identityLostMarkMapper;
        this.unboundDraftService = unboundDraftService;
        this.persistentJson = persistentJson;
    }

    @Transactional(readOnly = true)
    public List<UnboundCandidateResponse> listUnbound() {
        Set<String> consumedKeys = new HashSet<>();
        for (UnboundBindMemory memory : unboundBindMemoryMapper.selectList(null)) {
            consumedKeys.add(hostRuntimeKey(memory.getSourceHostId(), memory.getRuntimeId()));
        }
        return excludeConsumed(unboundMapper.selectList(new LambdaQueryWrapper<UnboundObservationCandidate>()
                        .orderByDesc(UnboundObservationCandidate::getObservedAt)), consumedKeys)
                .stream()
                .map(row -> toListItem(row, parseLabels(row.getLabelsJson())))
                .toList();
    }

    @Transactional(readOnly = true)
    public IdentityLostResponse getIdentityLost(String curatedObjectId) {
        IdentityLostMark mark = identityLostMarkMapper.selectById(curatedObjectId);
        if (mark == null) {
            throw new BusinessException("IDENTITY_LOST_NOT_FOUND",
                    "No identity-lost mark for object: " + curatedObjectId);
        }
        return new IdentityLostResponse(
                mark.getCuratedObjectId(),
                mark.getReason(),
                mark.getMarkedAt(),
                mark.getSourceAgentId(),
                mark.getSourceHostId(),
                Boolean.TRUE.equals(mark.getUpgradeChainPromised())
        );
    }

    AgentHeartbeatResponse.UnboundCandidate upsertUnbound(
            String agentId,
            String hostId,
            AgentHeartbeatRequest.SnapshotContainer container,
            Map<String, String> labels,
            UnboundReason reason,
            Instant now
    ) {
        String runtimeId = container.runtimeId();
        UnboundObservationCandidate existing = findUnboundByHostAndRuntime(hostId, runtimeId);
        if (existing != null) {
            applyUnboundSnapshot(existing, agentId, container, labels, reason, now);
            unboundMapper.updateById(existing);
            return toHeartbeatSummary(existing);
        }
        UnboundObservationCandidate row = new UnboundObservationCandidate();
        row.setId(newId("unb"));
        row.setSourceHostId(hostId);
        row.setRuntimeId(runtimeId);
        applyUnboundSnapshot(row, agentId, container, labels, reason, now);
        unboundMapper.insert(row);
        return toHeartbeatSummary(row);
    }

    /**
     * 命中即消费：删除该策展对象上的绑定记忆，以及这些记忆键与本次命中
     * ({@code reportingHostId}, {@code runtimeId}) 对应的未绑定候选行。
     */
    void consumeAfterLabelMatch(String curatedObjectId, String reportingHostId, String runtimeId) {
        List<UnboundBindMemory> memories = unboundBindMemoryMapper.selectList(
                new LambdaQueryWrapper<UnboundBindMemory>()
                        .eq(UnboundBindMemory::getCuratedObjectId, curatedObjectId));
        Set<String> candidateIds = new HashSet<>();
        Set<String> hostRuntimeKeys = new HashSet<>();
        for (UnboundBindMemory memory : memories) {
            hostRuntimeKeys.add(hostRuntimeKey(memory.getSourceHostId(), memory.getRuntimeId()));
            UnboundObservationCandidate row = findUnboundByHostAndRuntime(
                    memory.getSourceHostId(), memory.getRuntimeId());
            if (row != null) {
                candidateIds.add(row.getId());
            }
        }
        if (runtimeId != null && !runtimeId.isBlank()) {
            hostRuntimeKeys.add(hostRuntimeKey(reportingHostId, runtimeId));
            UnboundObservationCandidate hitRow = findUnboundByHostAndRuntime(reportingHostId, runtimeId);
            if (hitRow != null) {
                candidateIds.add(hitRow.getId());
            }
        }
        unboundDraftService.voidOpenUnboundAfterLabelMatch(curatedObjectId, candidateIds, hostRuntimeKeys);
        for (UnboundBindMemory memory : memories) {
            deleteUnboundCandidate(memory.getSourceHostId(), memory.getRuntimeId());
        }
        unboundBindMemoryMapper.delete(new LambdaQueryWrapper<UnboundBindMemory>()
                .eq(UnboundBindMemory::getCuratedObjectId, curatedObjectId));
        deleteUnboundCandidate(reportingHostId, runtimeId);
    }

    /**
     * 带快照的心跳是该宿主的完整现场清单。未再报告的 runtime 上的绑定记忆已过期：
     * 释放记忆并删除该候选行，但不清该策展对象的失联标（没有人认回，也没有人断言它不存在）。
     */
    void releaseStaleBindMemory(String reportingHostId, Set<String> reportedRuntimeIds) {
        List<UnboundBindMemory> memories = unboundBindMemoryMapper.selectList(
                new LambdaQueryWrapper<UnboundBindMemory>()
                        .eq(UnboundBindMemory::getSourceHostId, reportingHostId));
        for (UnboundBindMemory memory : memories) {
            if (reportedRuntimeIds.contains(memory.getRuntimeId())) {
                continue;
            }
            deleteUnboundCandidate(memory.getSourceHostId(), memory.getRuntimeId());
            unboundBindMemoryMapper.deleteById(memory.getId());
        }
    }

    /** 观测消失释放该对象上的绑定记忆，不删仍在现场的未绑定候选行。 */
    void releaseBindMemoryForObject(String curatedObjectId) {
        unboundBindMemoryMapper.delete(new LambdaQueryWrapper<UnboundBindMemory>()
                .eq(UnboundBindMemory::getCuratedObjectId, curatedObjectId));
    }

    static String hostRuntimeKey(String sourceHostId, String runtimeId) {
        return sourceHostId + "\0" + runtimeId;
    }

    static AgentHeartbeatResponse.UnboundCandidate toHeartbeatSummary(UnboundObservationCandidate row) {
        return new AgentHeartbeatResponse.UnboundCandidate(
                row.getId(),
                row.getReason().name(),
                row.getRuntimeId(),
                row.getName(),
                false
        );
    }

    static UnboundCandidateResponse toListItem(UnboundObservationCandidate row, Map<String, String> labels) {
        return new UnboundCandidateResponse(
                row.getId(),
                row.getSourceAgentId(),
                row.getSourceHostId(),
                row.getRuntimeId(),
                row.getName(),
                labels,
                row.getReason(),
                Boolean.TRUE.equals(row.getUpgradeChainPromised()),
                row.getObservedAt()
        );
    }

    static List<UnboundObservationCandidate> excludeConsumed(
            List<UnboundObservationCandidate> rows,
            Set<String> consumedKeys
    ) {
        return rows.stream()
                .filter(row -> !consumedKeys.contains(hostRuntimeKey(row.getSourceHostId(), row.getRuntimeId())))
                .toList();
    }

    private void applyUnboundSnapshot(
            UnboundObservationCandidate row,
            String agentId,
            AgentHeartbeatRequest.SnapshotContainer container,
            Map<String, String> labels,
            UnboundReason reason,
            Instant now
    ) {
        row.setSourceAgentId(agentId);
        row.setName(container.name());
        row.setLabelsJson(persistentJson.write(labels));
        row.setReason(reason);
        row.setUpgradeChainPromised(false);
        row.setObservedAt(now);
    }

    private UnboundObservationCandidate findUnboundByHostAndRuntime(String hostId, String runtimeId) {
        if (runtimeId == null || runtimeId.isBlank()) {
            return null;
        }
        return unboundMapper.selectOne(new LambdaQueryWrapper<UnboundObservationCandidate>()
                .eq(UnboundObservationCandidate::getSourceHostId, hostId)
                .eq(UnboundObservationCandidate::getRuntimeId, runtimeId));
    }

    private void deleteUnboundCandidate(String hostId, String runtimeId) {
        if (runtimeId == null || runtimeId.isBlank()) {
            return;
        }
        unboundMapper.delete(new LambdaQueryWrapper<UnboundObservationCandidate>()
                .eq(UnboundObservationCandidate::getSourceHostId, hostId)
                .eq(UnboundObservationCandidate::getRuntimeId, runtimeId));
    }

    private Map<String, String> parseLabels(String labelsJson) {
        return persistentJson.read(labelsJson, LABEL_MAP, Map.of());
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
