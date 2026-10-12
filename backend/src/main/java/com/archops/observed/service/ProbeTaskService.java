package com.archops.observed.service;

import com.archops.agent.dto.AgentHeartbeatRequest;
import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.common.exception.BusinessException;
import com.archops.common.json.PersistentJson;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.ProbeJudgment;
import com.archops.observed.domain.ProbeTask;
import com.archops.observed.dto.CreateProbeTaskRequest;
import com.archops.observed.dto.ProbeTaskResponse;
import com.archops.observed.mapper.ProbeJudgmentMapper;
import com.archops.observed.mapper.ProbeTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Probe tasks live on the control plane. The curated structured value is the only normal.
 * A task with no returned result is 观测空洞, not a conflict.
 */
@Service
public class ProbeTaskService {

    static final String JUDGMENT_HOLLOW = "HOLLOW";
    static final String JUDGMENT_NORMAL = "NORMAL";

    private static final TypeReference<JsonNode> JSON_NODE = new TypeReference<>() {
    };

    private final ProbeTaskMapper probeTaskMapper;
    private final ProbeJudgmentMapper probeJudgmentMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final PersistentJson persistentJson;

    public ProbeTaskService(
            ProbeTaskMapper probeTaskMapper,
            ProbeJudgmentMapper probeJudgmentMapper,
            CuratedObjectMapper curatedObjectMapper,
            PersistentJson persistentJson
    ) {
        this.probeTaskMapper = probeTaskMapper;
        this.probeJudgmentMapper = probeJudgmentMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.persistentJson = persistentJson;
    }

    @Transactional
    public ProbeTaskResponse create(CreateProbeTaskRequest request) {
        CuratedObject host = requireHost(request.hostId().trim());
        if (request.curatedExpected().isNull() || request.curatedExpected().isMissingNode()) {
            throw new BusinessException("PROBE_EXPECTED_REQUIRED", "Probe task requires a curated structured value");
        }
        Instant now = Instant.now();
        ProbeTask task = new ProbeTask();
        task.setId("probe-" + UUID.randomUUID());
        task.setHostId(host.getId());
        task.setCommand(request.command().trim());
        task.setExpectedJson(persistentJson.write(request.curatedExpected()));
        task.setIntervalSeconds(request.intervalSeconds());
        task.setCreatedAt(now);
        probeTaskMapper.insert(task);
        return toResponse(task);
    }

    @Transactional
    public void acceptResults(String hostId, List<AgentHeartbeatRequest.ProbeResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (AgentHeartbeatRequest.ProbeResult result : results) {
            ProbeTask task = requireTaskOnHost(result.taskId(), hostId);
            JsonNode value = result.value();
            boolean hasValue = value != null && !value.isNull() && !value.isMissingNode();
            if (result.readable() && hasValue && readExpected(task).equals(value)) {
                saveJudgment(task.getId(), JUDGMENT_NORMAL, persistentJson.write(value), result.evidence(), null, now);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AgentHeartbeatResponse.ProbeTaskAssignment> assignmentsForHost(String hostId) {
        return probeTaskMapper.selectList(new LambdaQueryWrapper<ProbeTask>()
                        .eq(ProbeTask::getHostId, hostId)
                        .orderByAsc(ProbeTask::getCreatedAt))
                .stream()
                .map(this::toAssignment)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProbeTaskResponse get(String id) {
        ProbeTask task = probeTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("PROBE_TASK_NOT_FOUND", "Probe task not found: " + id);
        }
        return toResponse(task);
    }

    private AgentHeartbeatResponse.ProbeTaskAssignment toAssignment(ProbeTask task) {
        return new AgentHeartbeatResponse.ProbeTaskAssignment(
                task.getId(),
                task.getCommand(),
                task.getIntervalSeconds(),
                readExpected(task)
        );
    }

    private ProbeTaskResponse toResponse(ProbeTask task) {
        ProbeJudgment judgment = probeJudgmentMapper.selectById(task.getId());
        boolean hollow = judgment == null || JUDGMENT_HOLLOW.equals(judgment.getOutcome());
        JsonNode observed = hollow || judgment.getObservedJson() == null
                ? null
                : persistentJson.read(judgment.getObservedJson(), JSON_NODE, null);
        return new ProbeTaskResponse(
                task.getId(),
                task.getHostId(),
                task.getCommand(),
                task.getIntervalSeconds(),
                readExpected(task),
                hollow ? JUDGMENT_HOLLOW : judgment.getOutcome(),
                hollow ? null : judgment.getConflictId(),
                hollow,
                observed,
                judgment == null ? null : judgment.getEvidence()
        );
    }

    private void saveJudgment(
            String taskId,
            String outcome,
            String observedJson,
            String evidence,
            String conflictId,
            Instant now
    ) {
        ProbeJudgment existing = probeJudgmentMapper.selectById(taskId);
        if (existing == null) {
            ProbeJudgment created = new ProbeJudgment();
            created.setTaskId(taskId);
            created.setOutcome(outcome);
            created.setObservedJson(observedJson);
            created.setEvidence(evidence);
            created.setConflictId(conflictId);
            created.setJudgedAt(now);
            probeJudgmentMapper.insert(created);
            return;
        }
        probeJudgmentMapper.update(null, new LambdaUpdateWrapper<ProbeJudgment>()
                .eq(ProbeJudgment::getTaskId, taskId)
                .set(ProbeJudgment::getOutcome, outcome)
                .set(ProbeJudgment::getObservedJson, observedJson)
                .set(ProbeJudgment::getEvidence, evidence)
                .set(ProbeJudgment::getConflictId, conflictId)
                .set(ProbeJudgment::getJudgedAt, now));
    }

    private ProbeTask requireTaskOnHost(String taskId, String hostId) {
        ProbeTask task = probeTaskMapper.selectById(taskId);
        if (task == null || !task.getHostId().equals(hostId)) {
            throw new BusinessException("PROBE_TASK_NOT_FOUND", "Probe task not found on host: " + taskId);
        }
        return task;
    }

    private JsonNode readExpected(ProbeTask task) {
        return persistentJson.read(task.getExpectedJson(), JSON_NODE, null);
    }

    private CuratedObject requireHost(String hostId) {
        CuratedObject host = curatedObjectMapper.selectById(hostId);
        if (host == null || host.getKind() != CuratedObjectKind.PHYSICAL_HOST) {
            throw new BusinessException("CURATED_HOST_NOT_FOUND", "Physical host not found: " + hostId);
        }
        return host;
    }
}
