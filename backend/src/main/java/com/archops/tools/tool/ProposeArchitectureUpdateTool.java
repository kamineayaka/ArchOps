package com.archops.tools.tool;

import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.service.ArchitectureProposalService;
import com.archops.tools.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Agent tool that creates an architecture proposal (PENDING_REVIEW) from discovered facts.
 */
@Component
public class ProposeArchitectureUpdateTool implements AgentTool {

    private final ArchitectureProposalService proposalService;
    private final ObjectMapper objectMapper;

    public ProposeArchitectureUpdateTool(ArchitectureProposalService proposalService, ObjectMapper objectMapper) {
        this.proposalService = proposalService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "propose_architecture_update";
    }

    @Override
    public String description() {
        return "Propose an architecture knowledge update for review. Use when you discover roles "
                + "(namenode/datanode/hive/spark), topology, or other durable facts. "
                + "Never write architecture SSOT directly — always propose. "
                + "partitionKey must be global / group:{id} / asset:{id}.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"partitionKey":{"type":"string","description":"global | group:{id} | asset:{id}"},"summary":{"type":"string","description":"Human-readable proposal summary"},"facts":{"type":"array","items":{"type":"object","properties":{"factType":{"type":"string"},"subject":{"type":"string"},"predicate":{"type":"string"},"object":{"type":"string"},"assetId":{"type":"integer"},"confidence":{"type":"number"}},"required":["factType","subject","predicate","object"]}},"evidence":{"type":"object","properties":{"command":{"type":"string"},"stdoutSummary":{"type":"string"},"assetId":{"type":"integer"},"conversationId":{"type":"integer"}}}},"required":["partitionKey","summary"]}""";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments, ExecutionContext context) throws Exception {
        String partitionKey = String.valueOf(arguments.get("partitionKey"));
        PartitionKeys.validate(partitionKey);
        String summary = String.valueOf(arguments.get("summary"));

        List<Map<String, Object>> facts = new ArrayList<>();
        Object rawFacts = arguments.get("facts");
        if (rawFacts instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    facts.add((Map<String, Object>) map);
                }
            }
        }

        Object rawEvidence = arguments.get("evidence");
        Map<String, Object> evidence = rawEvidence instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        if (!evidence.containsKey("conversationId") && context.conversationId() != null) {
            evidence.put("conversationId", context.conversationId());
        }

        String evidenceJson = objectMapper.writeValueAsString(List.of(evidence));

        ArchitectureProposal proposal = proposalService.createFromTool(
                partitionKey,
                summary,
                facts,
                evidenceJson,
                context.userId(),
                context.conversationId(),
                ProposalStatus.PENDING_REVIEW,
                "MEDIUM",
                averageConfidence(facts));

        return "Created architecture proposal id=" + proposal.getId()
                + " status=" + proposal.getStatus()
                + " partitionKey=" + proposal.getPartitionKey();
    }

    private static Double averageConfidence(List<Map<String, Object>> facts) {
        double sum = 0;
        int n = 0;
        for (Map<String, Object> fact : facts) {
            Object c = fact.get("confidence");
            if (c instanceof Number number) {
                sum += number.doubleValue();
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }
}
