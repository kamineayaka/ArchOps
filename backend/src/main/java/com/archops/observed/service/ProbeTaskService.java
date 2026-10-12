package com.archops.observed.service;

import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.common.exception.BusinessException;
import com.archops.common.json.PersistentJson;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.ProbeTask;
import com.archops.observed.dto.CreateProbeTaskRequest;
import com.archops.observed.dto.ProbeTaskResponse;
import com.archops.observed.mapper.ProbeTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private static final TypeReference<JsonNode> JSON_NODE = new TypeReference<>() {
    };

    private final ProbeTaskMapper probeTaskMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final PersistentJson persistentJson;

    public ProbeTaskService(
            ProbeTaskMapper probeTaskMapper,
            CuratedObjectMapper curatedObjectMapper,
            PersistentJson persistentJson
    ) {
        this.probeTaskMapper = probeTaskMapper;
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
        JsonNode expected = readExpected(task);
        return new ProbeTaskResponse(
                task.getId(),
                task.getHostId(),
                task.getCommand(),
                task.getIntervalSeconds(),
                expected,
                JUDGMENT_HOLLOW,
                null,
                true,
                null,
                null
        );
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
