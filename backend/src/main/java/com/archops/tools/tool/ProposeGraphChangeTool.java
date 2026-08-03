package com.archops.tools.tool;

import com.archops.graph.dto.GraphPlanRequest;
import com.archops.graph.dto.GraphPlanResponse;
import com.archops.graph.service.GraphPlanService;
import com.archops.knowledge.architecture.dto.ProposalCreateRequest;
import com.archops.knowledge.architecture.dto.ProposalResponse;
import com.archops.knowledge.architecture.service.ArchitecturePartitionService;
import com.archops.knowledge.architecture.service.ArchitectureProposalService;
import com.archops.tools.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Plans a graph ChangeSet and opens an architecture proposal for human review.
 * Never merges into Neo4j / SSOT directly.
 */
@Component
public class ProposeGraphChangeTool implements AgentTool {

    private final GraphPlanService graphPlanService;
    private final ArchitectureProposalService proposalService;
    private final ArchitecturePartitionService partitionService;
    private final ObjectMapper objectMapper;

    public ProposeGraphChangeTool(
            GraphPlanService graphPlanService,
            ArchitectureProposalService proposalService,
            ArchitecturePartitionService partitionService,
            ObjectMapper objectMapper) {
        this.graphPlanService = graphPlanService;
        this.proposalService = proposalService;
        this.partitionService = partitionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "propose_graph_change";
    }

    @Override
    public String description() {
        return "Propose an inventory topology change (nodes/edges/credentials) for human review. "
                + "Pass GraphOp ops (NODE_CREATE, NODE_UPDATE, NODE_SOFT_DELETE, REL_CREATE, REL_UPDATE, REL_DELETE, "
                + "TAG_ADD/REMOVE, plus pgSideEffects like CREDENTIAL_UPSERT_REF). "
                + "REL_CREATE types: MEMBER_OF, RUNS_ON, DEPENDS_ON, CONNECTS_VIA, HAS_TAG — "
                + "endpoint kinds are validated (e.g. CONNECTS_VIA requires SERVER→SERVER). "
                + "Optional edge properties.description is a hover remark, not topology SSOT. "
                + "Compiles a ChangeSet via plan mode and creates a PENDING architecture proposal — never merges directly. "
                + "For knowledge facts/roles use propose_architecture_update instead.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"summary":{"type":"string","description":"Human-readable change summary"},"ops":{"type":"array","description":"GraphOp list (same shape as graph workbench drafts)","items":{"type":"object"}},"pgSideEffects":{"type":"array","items":{"type":"object"},"description":"Optional PG side-effect ops"}},"required":["summary","ops"]}
                """;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments, ExecutionContext context) throws Exception {
        String summary = String.valueOf(arguments.get("summary"));
        List<Map<String, Object>> ops = new ArrayList<>();
        Object rawOps = arguments.get("ops");
        if (rawOps instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    ops.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        if (ops.isEmpty()) {
            return "Error: ops must be a non-empty array of GraphOp objects.";
        }

        List<Map<String, Object>> sideEffects = new ArrayList<>();
        Object rawSide = arguments.get("pgSideEffects");
        if (rawSide instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    sideEffects.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }

        GraphPlanResponse plan = graphPlanService.plan(new GraphPlanRequest(
                summary,
                ops,
                sideEffects.isEmpty() ? null : sideEffects));

        String partitionKey = plan.partitionKey();
        long baseVersion = partitionService.currentVersion(partitionKey);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source", "propose_graph_change");
        if (context.conversationId() != null) {
            evidence.put("conversationId", context.conversationId());
        }
        evidence.put("estimatedRisk", plan.estimatedRisk());
        evidence.put("warnings", plan.warnings());
        evidence.put("preview", plan.preview());
        String evidenceJson = objectMapper.writeValueAsString(List.of(evidence));

        String planJson = objectMapper.writeValueAsString(Map.of(
                "baseGraphVersion", plan.baseGraphVersion(),
                "partitionBaseVersion", plan.partitionBaseVersion(),
                "partitionKey", partitionKey,
                "estimatedRisk", plan.estimatedRisk(),
                "warnings", plan.warnings() != null ? plan.warnings() : List.of(),
                "preview", plan.preview() != null ? plan.preview() : Map.of()));

        ProposalCreateRequest request = new ProposalCreateRequest(
                partitionKey,
                summary,
                null,
                List.of(),
                plan.changeSetJson(),
                planJson,
                evidenceJson,
                plan.estimatedRisk() != null ? plan.estimatedRisk() : "MEDIUM",
                null,
                context.conversationId(),
                null,
                baseVersion,
                plan.baseGraphVersion(),
                "agent_tool_graph");

        ProposalResponse proposal = proposalService.create(request, context.userId());

        return "Created graph change proposal id=" + proposal.id()
                + " status=" + proposal.status()
                + " partitionKey=" + proposal.partitionKey()
                + " baseGraphVersion=" + plan.baseGraphVersion()
                + " estimatedRisk=" + plan.estimatedRisk()
                + " warnings=" + (plan.warnings() != null ? plan.warnings().size() : 0);
    }
}
