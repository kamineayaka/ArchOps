package com.archops.knowledge.service;

import com.archops.knowledge.domain.ArchitectureSnapshot;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.dto.ArchitectureRequest;
import com.archops.knowledge.dto.WorkLogResponse;
import com.archops.knowledge.indexing.RagIndexTrigger;
import com.archops.knowledge.repository.ArchitectureSnapshotRepository;
import com.archops.knowledge.repository.WorkLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private final ArchitectureSnapshotRepository snapshotRepository;
    private final WorkLogRepository workLogRepository;
    private final WorkLogWriter workLogWriter;
    private final RagIndexTrigger ragIndexTrigger;

    public KnowledgeService(
            ArchitectureSnapshotRepository snapshotRepository,
            WorkLogRepository workLogRepository,
            WorkLogWriter workLogWriter,
            RagIndexTrigger ragIndexTrigger) {
        this.snapshotRepository = snapshotRepository;
        this.workLogRepository = workLogRepository;
        this.workLogWriter = workLogWriter;
        this.ragIndexTrigger = ragIndexTrigger;
    }

    @Transactional
    public ArchitectureSnapshot updateArchitecture(ArchitectureRequest request, Long actorId, String actorName) {
        long nextVersion = snapshotRepository.findTopByOrderByVersionDesc()
                .map(s -> s.getVersion() + 1)
                .orElse(1L);

        ArchitectureSnapshot snapshot = new ArchitectureSnapshot();
        snapshot.setVersion(nextVersion);
        snapshot.setContent(request.content());
        snapshot.setSummary(request.summary());
        snapshotRepository.save(snapshot);

        WorkLog log = new WorkLog();
        log.setLogType("ARCHITECTURE_UPDATE");
        log.setActorId(actorId);
        log.setActorName(actorName);
        log.setUserId(actorId);
        log.setSummary(request.summary() != null ? request.summary() : "Architecture updated to v" + nextVersion);
        log.setDiff(request.content());
        log.setSource("manual");
        workLogWriter.save(log);

        ragIndexTrigger.indexArchitectureAfterCommit(snapshot.getId());
        return snapshot;
    }

    @Transactional(readOnly = true)
    public ArchitectureSnapshot latestArchitecture() {
        return snapshotRepository.findTopByOrderByVersionDesc().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkLogResponse> recentLogs() {
        return workLogRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkLogResponse> findWorkLogs(Long conversationId, Long assetId, Long groupId) {
        if (conversationId == null && assetId == null && groupId == null) {
            return recentLogs();
        }
        return workLogRepository.findFiltered(conversationId, assetId, groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    private WorkLogResponse toResponse(WorkLog l) {
        return new WorkLogResponse(
                l.getId(),
                l.getLogType(),
                l.getActorName(),
                l.getSummary(),
                l.getCreatedAt(),
                l.getConversationId(),
                l.getUserId(),
                l.getAssetIds(),
                l.getGroupIds(),
                l.getLevel(),
                l.isHypothesis(),
                l.getSource());
    }
}
