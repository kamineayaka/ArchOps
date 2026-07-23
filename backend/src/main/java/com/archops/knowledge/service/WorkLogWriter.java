package com.archops.knowledge.service;

import com.archops.knowledge.classifier.ChangeLevel;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.indexing.RagIndexTrigger;
import com.archops.knowledge.repository.WorkLogRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central entry point for persisting work logs and triggering RAG indexing.
 */
@Service
public class WorkLogWriter {

    private final WorkLogRepository workLogRepository;
    private final RagIndexTrigger ragIndexTrigger;

    public WorkLogWriter(WorkLogRepository workLogRepository, RagIndexTrigger ragIndexTrigger) {
        this.workLogRepository = workLogRepository;
        this.ragIndexTrigger = ragIndexTrigger;
    }

    @Transactional
    public WorkLog save(WorkLog workLog) {
        WorkLog saved = workLogRepository.save(workLog);
        ragIndexTrigger.indexWorkLogAfterCommit(saved.getId());
        return saved;
    }

    @Transactional
    public WorkLog appendAgentToolLog(
            Long conversationId,
            Long userId,
            String toolName,
            String summary,
            ChangeLevel level,
            List<Long> assetIds,
            List<Long> groupIds,
            String diffJson) {
        WorkLog log = new WorkLog();
        log.setLogType("AGENT_TOOL");
        log.setActorId(userId);
        log.setActorName("agent");
        log.setUserId(userId);
        log.setConversationId(conversationId);
        log.setSummary(summary != null ? summary : toolName);
        log.setLevel(level != null ? level.name() : null);
        log.setAssetIds(assetIds != null ? new ArrayList<>(assetIds) : new ArrayList<>());
        log.setGroupIds(groupIds != null ? new ArrayList<>(groupIds) : new ArrayList<>());
        log.setSource("agent");
        log.setHypothesis(level == ChangeLevel.L1 || level == ChangeLevel.L2);
        log.setDiff(diffJson != null ? diffJson : "{}");
        return save(log);
    }
}
