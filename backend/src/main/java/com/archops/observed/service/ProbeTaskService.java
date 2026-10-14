package com.archops.observed.service;

import com.archops.agent.dto.AgentHeartbeatRequest;
import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.common.exception.BusinessException;
import com.archops.common.json.PersistentJson;
import com.archops.conflict.service.ConflictDetectionService;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.ProbeAgentBinding;
import com.archops.observed.domain.ProbeJudgment;
import com.archops.observed.domain.ProbeTask;
import com.archops.observed.dto.CreateProbeTaskRequest;
import com.archops.observed.dto.ProbeOverdueScanResponse;
import com.archops.observed.dto.ProbeTaskResponse;
import com.archops.observed.mapper.ProbeAgentBindingMapper;
import com.archops.observed.mapper.ProbeJudgmentMapper;
import com.archops.observed.mapper.ProbeTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
    static final String JUDGMENT_CONFLICT = "CONFLICT";

    private static final TypeReference<JsonNode> JSON_NODE = new TypeReference<>() {
    };

    private final ProbeTaskMapper probeTaskMapper;
    private final ProbeJudgmentMapper probeJudgmentMapper;
    private final ProbeAgentBindingMapper probeAgentBindingMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final ConflictDetectionService conflictDetectionService;
    private final PersistentJson persistentJson;
    private final Clock clock;

    public ProbeTaskService(
            ProbeTaskMapper probeTaskMapper,
            ProbeJudgmentMapper probeJudgmentMapper,
            ProbeAgentBindingMapper probeAgentBindingMapper,
            CuratedObjectMapper curatedObjectMapper,
            ConflictDetectionService conflictDetectionService,
            PersistentJson persistentJson,
            Clock clock
    ) {
        this.probeTaskMapper = probeTaskMapper;
        this.probeJudgmentMapper = probeJudgmentMapper;
        this.probeAgentBindingMapper = probeAgentBindingMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.conflictDetectionService = conflictDetectionService;
        this.persistentJson = persistentJson;
        this.clock = clock;
    }

    /** Writes the task's curated structured value. */
    @Transactional
    public void writeCuratedExpected(String taskId, JsonNode curatedExpected) {
        if (curatedExpected == null || curatedExpected.isNull() || !curatedExpected.isObject()) {
            throw new BusinessException("PROBE_EXPECTED_REQUIRED", "Probe curated value must be a JSON object");
        }
        if (probeTaskMapper.selectById(taskId) == null) {
            throw new BusinessException("PROBE_TASK_NOT_FOUND", "Probe task not found: " + taskId);
        }
        probeTaskMapper.update(null, new LambdaUpdateWrapper<ProbeTask>()
                .eq(ProbeTask::getId, taskId)
                .set(ProbeTask::getExpectedJson, persistentJson.write(curatedExpected)));
    }

    /**
     * Compares the latest stored probe result with the task's current curated value.
     */
    @Transactional
    public void rejudgeLatestAgainstCurated(String taskId) {
        ProbeTask task = probeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("PROBE_TASK_NOT_FOUND", "Probe task not found: " + taskId);
        }
        ProbeJudgment judgment = probeJudgmentMapper.selectById(taskId);
        if (judgment == null || judgment.getObservedJson() == null || judgment.getObservedJson().isBlank()) {
            return;
        }
        JsonNode observed = persistentJson.read(judgment.getObservedJson(), JSON_NODE, null);
        JsonNode expected = readExpected(task);
        Instant now = Instant.now(clock);
        if (expected != null && expected.equals(observed)) {
            saveJudgment(taskId, JUDGMENT_NORMAL, judgment.getObservedJson(), judgment.getEvidence(), null, now);
            conflictDetectionService.onProbeCuratedWriteEqual(
                    taskId, task.getExpectedJson(), judgment.getObservedJson());
            return;
        }
        String conflictId = conflictDetectionService.openOrUpdateProbeConflict(
                task.getHostId(),
                task.getId(),
                task.getExpectedJson(),
                judgment.getObservedJson(),
                judgment.getEvidence()
        );
        saveJudgment(taskId, JUDGMENT_CONFLICT, judgment.getObservedJson(), judgment.getEvidence(), conflictId, now);
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
    public void acceptResults(String hostId, String agentId, List<AgentHeartbeatRequest.ProbeResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        requireThisProbeAgent(hostId, agentId);
        Instant now = Instant.now();
        for (AgentHeartbeatRequest.ProbeResult result : results) {
            judgeOne(requireTaskOnHost(result.taskId(), hostId), result, now);
        }
    }

    private void judgeOne(ProbeTask task, AgentHeartbeatRequest.ProbeResult result, Instant now) {
        JsonNode value = result.value();
        boolean hasValue = value != null && !value.isNull() && !value.isMissingNode();
        if (result.readable() && hasValue && readExpected(task).equals(value)) {
            String observedJson = persistentJson.write(value);
            saveJudgment(task.getId(), JUDGMENT_NORMAL, observedJson, result.evidence(), null, now);
            conflictDetectionService.onProbeBecameEqual(task.getId(), observedJson);
            return;
        }
        if (result.readable() && hasValue) {
            String observedJson = persistentJson.write(value);
            String conflictId = conflictDetectionService.openOrUpdateProbeConflict(
                    task.getHostId(),
                    task.getId(),
                    task.getExpectedJson(),
                    observedJson,
                    result.evidence()
            );
            saveJudgment(task.getId(), JUDGMENT_CONFLICT, observedJson, result.evidence(), conflictId, now);
            return;
        }
        saveJudgment(task.getId(), JUDGMENT_HOLLOW, null, result.evidence(), null, now);
        conflictDetectionService.onProbeBecameHollow(task.getId());
    }

    @Transactional
    public List<AgentHeartbeatResponse.ProbeTaskAssignment> assignmentsForHost(String hostId, String agentId) {
        List<ProbeTask> tasks = probeTaskMapper.selectList(new LambdaQueryWrapper<ProbeTask>()
                .eq(ProbeTask::getHostId, hostId)
                .orderByAsc(ProbeTask::getCreatedAt));
        Instant now = Instant.now(clock);
        if (tasks.isEmpty() || !claimOrMatchProbeAgent(hostId, agentId, now)) {
            return List.of();
        }
        for (ProbeTask task : tasks) {
            notePull(task, now);
        }
        return tasks.stream().map(this::toAssignment).toList();
    }

    @Transactional
    public ProbeOverdueScanResponse scanOverdue() {
        Instant now = Instant.now(clock);
        List<ProbeTask> due = probeTaskMapper.selectList(new LambdaQueryWrapper<ProbeTask>()
                .eq(ProbeTask::getAwaitingResult, true)
                .le(ProbeTask::getDueAt, now));
        List<String> hollowed = new ArrayList<>();
        for (ProbeTask task : due) {
            saveJudgment(task.getId(), JUDGMENT_HOLLOW, null, null, null, now);
            conflictDetectionService.onProbeBecameHollow(task.getId());
            hollowed.add(task.getId());
        }
        return new ProbeOverdueScanResponse(hollowed);
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
            clearAwaiting(taskId);
            return;
        }
        probeJudgmentMapper.update(null, new LambdaUpdateWrapper<ProbeJudgment>()
                .eq(ProbeJudgment::getTaskId, taskId)
                .set(ProbeJudgment::getOutcome, outcome)
                .set(ProbeJudgment::getObservedJson, observedJson)
                .set(ProbeJudgment::getEvidence, evidence)
                .set(ProbeJudgment::getConflictId, conflictId)
                .set(ProbeJudgment::getJudgedAt, now));
        clearAwaiting(taskId);
    }

    /**
     * First agent to pull tasks on a host becomes its only probe agent.
     * A different agent keeps a normal heartbeat but receives no tasks.
     */
    private boolean claimOrMatchProbeAgent(String hostId, String agentId, Instant now) {
        ProbeAgentBinding binding = probeAgentBindingMapper.selectById(hostId);
        if (binding == null) {
            ProbeAgentBinding created = new ProbeAgentBinding();
            created.setHostId(hostId);
            created.setAgentId(agentId);
            created.setBoundAt(now);
            probeAgentBindingMapper.insert(created);
            return true;
        }
        return binding.getAgentId().equals(agentId);
    }

    private void requireThisProbeAgent(String hostId, String agentId) {
        ProbeAgentBinding binding = probeAgentBindingMapper.selectById(hostId);
        if (binding != null && !binding.getAgentId().equals(agentId)) {
            throw new BusinessException(
                    "PROBE_AGENT_ALREADY_BOUND",
                    "This host already has a probe agent"
            );
        }
    }

    private void notePull(ProbeTask task, Instant now) {
        if (task.getAwaitingResult()) {
            return;
        }
        Instant due = now.plusSeconds(task.getIntervalSeconds());
        probeTaskMapper.update(null, new LambdaUpdateWrapper<ProbeTask>()
                .eq(ProbeTask::getId, task.getId())
                .set(ProbeTask::getAwaitingResult, true)
                .set(ProbeTask::getDueAt, due));
        task.setAwaitingResult(true);
        task.setDueAt(due);
    }

    private void clearAwaiting(String taskId) {
        probeTaskMapper.update(null, new LambdaUpdateWrapper<ProbeTask>()
                .eq(ProbeTask::getId, taskId)
                .set(ProbeTask::getAwaitingResult, false)
                .set(ProbeTask::getDueAt, null));
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
