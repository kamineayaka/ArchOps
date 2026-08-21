package com.archops.observed.service;

import com.archops.agent.dto.AgentHeartbeatRequest;
import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.common.exception.BusinessException;
import com.archops.conflict.service.ConflictDetectionService;
import com.archops.curated.CuratedObjectLabels;
import com.archops.curated.domain.CuratedFact;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.HostAgent;
import com.archops.observed.domain.IdentityLostMark;
import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.domain.UnboundObservationCandidate;
import com.archops.observed.domain.UnboundReason;
import com.archops.observed.dto.ActualWhereResponse;
import com.archops.observed.dto.AgentFreshnessResponse;
import com.archops.observed.dto.IdentityLostResponse;
import com.archops.observed.dto.UnboundCandidateResponse;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.observed.mapper.IdentityLostMarkMapper;
import com.archops.observed.mapper.ObservedFactMapper;
import com.archops.observed.mapper.UnboundObservationCandidateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ObservedTruthService {

    private static final TypeReference<Map<String, String>> LABEL_MAP = new TypeReference<>() {
    };

    private final HostAgentMapper hostAgentMapper;
    private final ObservedFactMapper observedFactMapper;
    private final UnboundObservationCandidateMapper unboundMapper;
    private final IdentityLostMarkMapper identityLostMarkMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final CuratedFactMapper curatedFactMapper;
    private final ConflictDetectionService conflictDetectionService;
    private final ObservationFreshnessService observationFreshnessService;
    private final ObjectMapper objectMapper;

    public ObservedTruthService(
            HostAgentMapper hostAgentMapper,
            ObservedFactMapper observedFactMapper,
            UnboundObservationCandidateMapper unboundMapper,
            IdentityLostMarkMapper identityLostMarkMapper,
            CuratedObjectMapper curatedObjectMapper,
            CuratedFactMapper curatedFactMapper,
            ConflictDetectionService conflictDetectionService,
            @Lazy ObservationFreshnessService observationFreshnessService,
            ObjectMapper objectMapper
    ) {
        this.hostAgentMapper = hostAgentMapper;
        this.observedFactMapper = observedFactMapper;
        this.unboundMapper = unboundMapper;
        this.identityLostMarkMapper = identityLostMarkMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.curatedFactMapper = curatedFactMapper;
        this.conflictDetectionService = conflictDetectionService;
        this.observationFreshnessService = observationFreshnessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentHeartbeatResponse ingestHeartbeat(AgentHeartbeatRequest request) {
        String agentId = request.agentId().trim();
        String hostId = request.hostId().trim();
        CuratedObject host = requireHost(hostId);
        Instant now = Instant.now();

        upsertHostAgent(agentId, host.getId(), now, request.snapshot() != null);

        List<AgentHeartbeatResponse.MatchedObserved> matched = new ArrayList<>();
        List<AgentHeartbeatResponse.AbsentObserved> absent = new ArrayList<>();
        List<AgentHeartbeatResponse.UnboundCandidate> unbound = new ArrayList<>();
        List<AgentHeartbeatResponse.IdentityLost> identityLost = new ArrayList<>();

        if (request.snapshot() != null) {
            processSnapshot(request, host, now, matched, absent, unbound, identityLost);
        }

        HostAgent agent = hostAgentMapper.selectById(agentId);
        return new AgentHeartbeatResponse(
                agentId,
                host.getId(),
                now,
                new AgentHeartbeatResponse.Freshness(agent.getLastHeartbeatAt(), agent.getLastSnapshotAt()),
                matched,
                absent,
                unbound,
                identityLost
        );
    }

    @Transactional(readOnly = true)
    public AgentFreshnessResponse freshness(String agentId) {
        HostAgent agent = hostAgentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException("AGENT_NOT_FOUND", "Unknown agent id: " + agentId);
        }
        return new AgentFreshnessResponse(
                agent.getAgentId(),
                agent.getHostId(),
                agent.getLastHeartbeatAt(),
                agent.getLastSnapshotAt()
        );
    }

    @Transactional(readOnly = true)
    public ActualWhereResponse actualWhere(String containerId) {
        CuratedObject container = requireContainer(containerId.trim());
        CuratedFact curatedRunsOn = findCuratedRunsOn(container.getId());
        if (curatedRunsOn == null) {
            throw new BusinessException("CURATED_RUNS_ON_NOT_FOUND",
                    "No curated 运行于 fact for container: " + container.getId());
        }
        CuratedObject curatedHost = curatedObjectMapper.selectById(curatedRunsOn.getTargetId());
        if (curatedHost == null) {
            throw new BusinessException("CURATED_HOST_NOT_FOUND",
                    "Physical host not found: " + curatedRunsOn.getTargetId());
        }

        ObservedFact observed = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, container.getId())
                .eq(ObservedFact::getRelationType, CuratedRelationType.RUNS_ON));
        IdentityLostMark lostMark = identityLostMarkMapper.selectById(container.getId());
        boolean identityLost = lostMark != null;

        return new ActualWhereResponse(
                "实际在哪",
                "OBSERVED",
                CuratedRelationType.RUNS_ON,
                CuratedRelationType.RUNS_ON.labelZh(),
                CuratedObjectResponse.from(container),
                observedAskValue(lostMark, observed),
                new ActualWhereResponse.CuratedHostValue(curatedHost.getId(), curatedHost.getName()),
                identityLost
        );
    }

    /**
     * 规范问法「实际在哪」投影。IDENTITY_LOST 只出现在此读模型，不写入 observed_fact.availability。
     */
    private ActualWhereResponse.ObservedValue observedAskValue(IdentityLostMark lostMark, ObservedFact observed) {
        if (lostMark != null) {
            return new ActualWhereResponse.ObservedValue("IDENTITY_LOST", null, null);
        }
        if (observed == null || observationFreshnessService.isObservedFactStale(observed)) {
            return new ActualWhereResponse.ObservedValue("HOLLOW", null, null);
        }
        if (observed.getAvailability() == ObservedAvailability.ABSENT) {
            return new ActualWhereResponse.ObservedValue("ABSENT", null, null);
        }
        CuratedObject observedHost = curatedObjectMapper.selectById(observed.getTargetId());
        return new ActualWhereResponse.ObservedValue(
                "PRESENT",
                observedHost != null ? observedHost.getId() : observed.getTargetId(),
                observedHost != null ? observedHost.getName() : null
        );
    }

    @Transactional(readOnly = true)
    public List<UnboundCandidateResponse> listUnbound() {
        return unboundMapper.selectList(new LambdaQueryWrapper<UnboundObservationCandidate>()
                        .orderByDesc(UnboundObservationCandidate::getObservedAt))
                .stream()
                .map(row -> new UnboundCandidateResponse(
                        row.getId(),
                        row.getSourceAgentId(),
                        row.getSourceHostId(),
                        row.getRuntimeId(),
                        row.getName(),
                        parseLabels(row.getLabelsJson()),
                        row.getReason(),
                        Boolean.TRUE.equals(row.getUpgradeChainPromised()),
                        row.getObservedAt()
                ))
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

    private void processSnapshot(
            AgentHeartbeatRequest request,
            CuratedObject host,
            Instant now,
            List<AgentHeartbeatResponse.MatchedObserved> matched,
            List<AgentHeartbeatResponse.AbsentObserved> absent,
            List<AgentHeartbeatResponse.UnboundCandidate> unbound,
            List<AgentHeartbeatResponse.IdentityLost> identityLost
    ) {
        AgentHeartbeatRequest.SnapshotPayload snapshot = request.snapshot();
        String agentId = request.agentId().trim();
        Map<String, ObservedFact> observedRunsOnAtStart = observedRunsOnBySubject();
        Set<String> matchedCuratedIds = new HashSet<>();
        Set<String> absentCuratedIds = new HashSet<>();

        List<AgentHeartbeatRequest.SnapshotContainer> containers =
                snapshot.containers() == null ? List.of() : snapshot.containers();
        for (AgentHeartbeatRequest.SnapshotContainer container : containers) {
            Map<String, String> labels = container.labels() == null ? Map.of() : container.labels();
            String objectId = labels.get(CuratedObjectLabels.OBJECT_ID_KEY);
            if (objectId == null || objectId.isBlank()) {
                unbound.add(upsertUnbound(agentId, host.getId(), container, labels, UnboundReason.MISSING_LABEL, now));
                continue;
            }
            String trimmedObjectId = objectId.trim();
            CuratedObject curated = findContainerByImmutableObjectId(trimmedObjectId);
            if (curated == null) {
                unbound.add(upsertUnbound(agentId, host.getId(), container, labels, UnboundReason.UNKNOWN_OBJECT_ID, now));
                continue;
            }
            matchedCuratedIds.add(curated.getId());
            upsertObservedPresent(curated, host, agentId, now);
            matched.add(new AgentHeartbeatResponse.MatchedObserved(
                    curated.getId(),
                    trimmedObjectId,
                    host.getId(),
                    CuratedRelationType.RUNS_ON.name()
            ));
        }

        List<String> absentIds = snapshot.absentObjectIds() == null ? List.of() : snapshot.absentObjectIds();
        for (String raw : absentIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            CuratedObject curated = findContainerByImmutableObjectId(raw.trim());
            if (curated == null) {
                continue;
            }
            absentCuratedIds.add(curated.getId());
            upsertObservedAbsent(curated, host, agentId, now);
            absent.add(new AgentHeartbeatResponse.AbsentObserved(
                    curated.getId(),
                    curated.getImmutableObjectId(),
                    ObservedAvailability.ABSENT.name()
            ));
        }

        List<String> lostIds = snapshot.identityLostObjectIds() == null ? List.of() : snapshot.identityLostObjectIds();
        for (String raw : lostIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            CuratedObject curated = findContainerByImmutableObjectId(raw.trim());
            if (curated == null) {
                curated = curatedObjectMapper.selectById(raw.trim());
                if (curated == null || curated.getKind() != CuratedObjectKind.DOCKER_CONTAINER) {
                    continue;
                }
            }
            CuratedFact curatedRunsOn = findCuratedRunsOn(curated.getId());
            if (curatedRunsOn == null
                    || !reportingHostInIdentityLostScope(
                            host.getId(),
                            curatedRunsOn,
                            observedRunsOnAtStart.get(curated.getId()))) {
                continue;
            }
            upsertIdentityLost(curated, host, agentId, now);
            identityLost.add(new AgentHeartbeatResponse.IdentityLost(
                    curated.getId(),
                    curated.getImmutableObjectId(),
                    false
            ));
        }

        inferIdentityLost(
                host,
                agentId,
                now,
                matchedCuratedIds,
                absentCuratedIds,
                observedRunsOnAtStart,
                identityLost
        );
    }

    private void inferIdentityLost(
            CuratedObject reportingHost,
            String agentId,
            Instant now,
            Set<String> matchedCuratedIds,
            Set<String> absentCuratedIds,
            Map<String, ObservedFact> observedRunsOnAtStart,
            List<AgentHeartbeatResponse.IdentityLost> identityLost
    ) {
        List<CuratedFact> curatedRunsOn = curatedFactMapper.selectList(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getRelationType, CuratedRelationType.RUNS_ON));
        Set<String> alreadyListed = new HashSet<>();
        for (AgentHeartbeatResponse.IdentityLost listed : identityLost) {
            alreadyListed.add(listed.curatedObjectId());
        }
        for (CuratedFact fact : curatedRunsOn) {
            CuratedObject curated = curatedObjectMapper.selectById(fact.getSubjectId());
            if (curated == null || curated.getKind() != CuratedObjectKind.DOCKER_CONTAINER) {
                continue;
            }
            if (matchedCuratedIds.contains(curated.getId()) || absentCuratedIds.contains(curated.getId())) {
                continue;
            }
            if (!reportingHostInIdentityLostScope(reportingHost.getId(), fact, observedRunsOnAtStart.get(curated.getId()))) {
                continue;
            }
            upsertIdentityLost(curated, reportingHost, agentId, now);
            if (alreadyListed.add(curated.getId())) {
                identityLost.add(new AgentHeartbeatResponse.IdentityLost(
                        curated.getId(),
                        curated.getImmutableObjectId(),
                        false
                ));
            }
        }
    }

    private boolean reportingHostInIdentityLostScope(
            String reportingHostId,
            CuratedFact curatedRunsOn,
            ObservedFact observedAtStart
    ) {
        if (reportingHostId.equals(curatedRunsOn.getTargetId())) {
            return true;
        }
        return observedAtStart != null
                && observedAtStart.getAvailability() == ObservedAvailability.PRESENT
                && !observationFreshnessService.isObservedFactStale(observedAtStart)
                && reportingHostId.equals(observedAtStart.getTargetId());
    }

    private Map<String, ObservedFact> observedRunsOnBySubject() {
        List<ObservedFact> facts = observedFactMapper.selectList(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getRelationType, CuratedRelationType.RUNS_ON));
        Map<String, ObservedFact> bySubject = new HashMap<>();
        for (ObservedFact fact : facts) {
            bySubject.put(fact.getSubjectId(), fact);
        }
        return bySubject;
    }

    private void upsertHostAgent(String agentId, String hostId, Instant now, boolean hasSnapshot) {
        HostAgent existing = hostAgentMapper.selectById(agentId);
        if (existing == null) {
            HostAgent created = new HostAgent();
            created.setAgentId(agentId);
            created.setHostId(hostId);
            created.setLastHeartbeatAt(now);
            created.setLastSnapshotAt(hasSnapshot ? now : null);
            created.setUpdatedAt(now);
            hostAgentMapper.insert(created);
            return;
        }
        existing.setHostId(hostId);
        existing.setLastHeartbeatAt(now);
        if (hasSnapshot) {
            existing.setLastSnapshotAt(now);
        }
        existing.setUpdatedAt(now);
        hostAgentMapper.updateById(existing);
    }

    private void upsertObservedPresent(CuratedObject container, CuratedObject host, String agentId, Instant now) {
        ObservedFact existing = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, container.getId())
                .eq(ObservedFact::getRelationType, CuratedRelationType.RUNS_ON));
        if (existing == null) {
            ObservedFact fact = new ObservedFact();
            fact.setId(newId("obs"));
            fact.setSubjectId(container.getId());
            fact.setRelationType(CuratedRelationType.RUNS_ON);
            fact.setAvailability(ObservedAvailability.PRESENT);
            fact.setTargetId(host.getId());
            fact.setObservedAt(now);
            fact.setSourceAgentId(agentId);
            fact.setSourceHostId(host.getId());
            observedFactMapper.insert(fact);
            conflictDetectionService.reconcileAfterObservedWrite(container.getId(), CuratedRelationType.RUNS_ON);
            return;
        }
        observedFactMapper.update(null, new LambdaUpdateWrapper<ObservedFact>()
                .eq(ObservedFact::getId, existing.getId())
                .set(ObservedFact::getAvailability, ObservedAvailability.PRESENT)
                .set(ObservedFact::getTargetId, host.getId())
                .set(ObservedFact::getObservedAt, now)
                .set(ObservedFact::getSourceAgentId, agentId)
                .set(ObservedFact::getSourceHostId, host.getId()));
        conflictDetectionService.reconcileAfterObservedWrite(container.getId(), CuratedRelationType.RUNS_ON);
    }

    private void upsertObservedAbsent(CuratedObject container, CuratedObject host, String agentId, Instant now) {
        ObservedFact existing = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, container.getId())
                .eq(ObservedFact::getRelationType, CuratedRelationType.RUNS_ON));
        if (existing == null) {
            ObservedFact fact = new ObservedFact();
            fact.setId(newId("obs"));
            fact.setSubjectId(container.getId());
            fact.setRelationType(CuratedRelationType.RUNS_ON);
            fact.setAvailability(ObservedAvailability.ABSENT);
            fact.setTargetId(null);
            fact.setObservedAt(now);
            fact.setSourceAgentId(agentId);
            fact.setSourceHostId(host.getId());
            observedFactMapper.insert(fact);
            conflictDetectionService.reconcileAfterObservedWrite(container.getId(), CuratedRelationType.RUNS_ON);
            return;
        }
        // Explicit set null — updateById would skip null targetId under default FieldStrategy.
        observedFactMapper.update(null, new LambdaUpdateWrapper<ObservedFact>()
                .eq(ObservedFact::getId, existing.getId())
                .set(ObservedFact::getAvailability, ObservedAvailability.ABSENT)
                .set(ObservedFact::getTargetId, null)
                .set(ObservedFact::getObservedAt, now)
                .set(ObservedFact::getSourceAgentId, agentId)
                .set(ObservedFact::getSourceHostId, host.getId()));
        conflictDetectionService.reconcileAfterObservedWrite(container.getId(), CuratedRelationType.RUNS_ON);
    }

    private AgentHeartbeatResponse.UnboundCandidate upsertUnbound(
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
            return toUnboundSummary(existing);
        }
        UnboundObservationCandidate row = new UnboundObservationCandidate();
        row.setId(newId("unb"));
        row.setSourceHostId(hostId);
        row.setRuntimeId(runtimeId);
        applyUnboundSnapshot(row, agentId, container, labels, reason, now);
        unboundMapper.insert(row);
        return toUnboundSummary(row);
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
        row.setLabelsJson(toJson(labels));
        row.setReason(reason);
        row.setUpgradeChainPromised(false);
        row.setObservedAt(now);
    }

    private AgentHeartbeatResponse.UnboundCandidate toUnboundSummary(UnboundObservationCandidate row) {
        return new AgentHeartbeatResponse.UnboundCandidate(
                row.getId(),
                row.getReason().name(),
                row.getRuntimeId(),
                row.getName(),
                false
        );
    }

    private UnboundObservationCandidate findUnboundByHostAndRuntime(String hostId, String runtimeId) {
        if (runtimeId == null || runtimeId.isBlank()) {
            return null;
        }
        return unboundMapper.selectOne(new LambdaQueryWrapper<UnboundObservationCandidate>()
                .eq(UnboundObservationCandidate::getSourceHostId, hostId)
                .eq(UnboundObservationCandidate::getRuntimeId, runtimeId));
    }

    private void upsertIdentityLost(CuratedObject curated, CuratedObject host, String agentId, Instant now) {
        IdentityLostMark existing = identityLostMarkMapper.selectById(curated.getId());
        if (existing == null) {
            IdentityLostMark mark = new IdentityLostMark();
            mark.setCuratedObjectId(curated.getId());
            mark.setReason("LABEL_CLUE_LOST");
            mark.setMarkedAt(now);
            mark.setSourceAgentId(agentId);
            mark.setSourceHostId(host.getId());
            mark.setUpgradeChainPromised(false);
            identityLostMarkMapper.insert(mark);
            return;
        }
        existing.setReason("LABEL_CLUE_LOST");
        existing.setMarkedAt(now);
        existing.setSourceAgentId(agentId);
        existing.setSourceHostId(host.getId());
        existing.setUpgradeChainPromised(false);
        identityLostMarkMapper.updateById(existing);
    }

    private CuratedFact findCuratedRunsOn(String containerId) {
        return curatedFactMapper.selectOne(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getSubjectId, containerId)
                .eq(CuratedFact::getRelationType, CuratedRelationType.RUNS_ON));
    }

    private CuratedObject findContainerByImmutableObjectId(String objectId) {
        return curatedObjectMapper.selectOne(new LambdaQueryWrapper<CuratedObject>()
                .eq(CuratedObject::getImmutableObjectId, objectId)
                .eq(CuratedObject::getKind, CuratedObjectKind.DOCKER_CONTAINER));
    }

    private CuratedObject requireHost(String hostId) {
        CuratedObject host = curatedObjectMapper.selectById(hostId);
        if (host == null || host.getKind() != CuratedObjectKind.PHYSICAL_HOST) {
            throw new BusinessException("CURATED_HOST_NOT_FOUND", "Physical host not found: " + hostId);
        }
        return host;
    }

    private CuratedObject requireContainer(String containerId) {
        CuratedObject container = curatedObjectMapper.selectById(containerId);
        if (container == null || container.getKind() != CuratedObjectKind.DOCKER_CONTAINER) {
            throw new BusinessException("CURATED_CONTAINER_NOT_FOUND", "Docker container not found: " + containerId);
        }
        return container;
    }

    private Map<String, String> parseLabels(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> labels = objectMapper.readValue(labelsJson, LABEL_MAP);
            return labels == null ? Map.of() : labels;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String toJson(Map<String, String> labels) {
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
