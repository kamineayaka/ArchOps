package com.archops.knowledge.service;

import com.archops.common.exception.BusinessException;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.service.ArchitectureProposalService;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.repository.WorkLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes a work log into an architecture proposal (only allowed promotion path).
 */
@Service
public class WorkLogPromotionService {

    private final WorkLogRepository workLogRepository;
    private final ArchitectureProposalService proposalService;
    private final ObjectMapper objectMapper;

    public WorkLogPromotionService(
            WorkLogRepository workLogRepository,
            ArchitectureProposalService proposalService,
            ObjectMapper objectMapper) {
        this.workLogRepository = workLogRepository;
        this.proposalService = proposalService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ArchitectureProposal createProposalFromWorkLog(Long workLogId, Long requesterId) {
        WorkLog log = workLogRepository.findById(workLogId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "WORK_LOG_NOT_FOUND", "工作日志不存在"));

        String partitionKey = resolvePartitionKey(log);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("workLogId", log.getId());
        evidence.put("conversationId", log.getConversationId());
        evidence.put("level", log.getLevel());
        evidence.put("summary", log.getSummary());
        if (log.getAssetIds() != null && !log.getAssetIds().isEmpty()) {
            evidence.put("assetId", log.getAssetIds().getFirst());
        }

        String evidenceJson;
        try {
            evidenceJson = objectMapper.writeValueAsString(List.of(evidence));
        } catch (Exception ex) {
            evidenceJson = "[{\"workLogId\":" + log.getId() + "}]";
        }

        String summary = "Promoted from work log #" + log.getId() + ": "
                + (log.getSummary() != null ? log.getSummary() : "");

        return proposalService.createFromTool(
                partitionKey,
                summary,
                List.of(),
                evidenceJson,
                requesterId,
                log.getConversationId(),
                ProposalStatus.PENDING_REVIEW,
                log.getLevel() != null ? log.getLevel() : "MEDIUM",
                null);
    }

    private static String resolvePartitionKey(WorkLog log) {
        if (log.getAssetIds() != null && !log.getAssetIds().isEmpty()) {
            return PartitionKeys.asset(log.getAssetIds().getFirst());
        }
        if (log.getGroupIds() != null && !log.getGroupIds().isEmpty()) {
            return PartitionKeys.group(log.getGroupIds().getFirst());
        }
        return PartitionKeys.GLOBAL;
    }
}
