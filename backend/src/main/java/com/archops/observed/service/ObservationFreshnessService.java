package com.archops.observed.service;

import com.archops.conflict.service.ConflictDetectionService;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.observed.config.ObservationProperties;
import com.archops.observed.domain.HostAgent;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.dto.HeartbeatTimeoutScanResponse;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.observed.mapper.ObservedFactMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Heartbeat timeout scanner: stale agents → delete sourced observed facts (观测空洞)
 * → suspend conflicts + void active plans (ticket 10).
 */
@Service
public class ObservationFreshnessService {

    private final HostAgentMapper hostAgentMapper;
    private final ObservedFactMapper observedFactMapper;
    private final ConflictDetectionService conflictDetectionService;
    private final ObservationProperties observationProperties;
    private final Clock clock;

    public ObservationFreshnessService(
            HostAgentMapper hostAgentMapper,
            ObservedFactMapper observedFactMapper,
            ConflictDetectionService conflictDetectionService,
            ObservationProperties observationProperties,
            Clock clock
    ) {
        this.hostAgentMapper = hostAgentMapper;
        this.observedFactMapper = observedFactMapper;
        this.conflictDetectionService = conflictDetectionService;
        this.observationProperties = observationProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${archops.observation.hollow-scan-interval-ms:5000}")
    public void scheduledScan() {
        scanHeartbeatTimeouts();
    }

    @Transactional
    public HeartbeatTimeoutScanResponse scanHeartbeatTimeouts() {
        List<HostAgent> staleAgents = hostAgentMapper.selectList(new LambdaQueryWrapper<HostAgent>()
                .lt(HostAgent::getLastHeartbeatAt, heartbeatCutoff()));

        Set<String> affectedSubjects = new LinkedHashSet<>();
        int hollowed = 0;
        for (HostAgent agent : staleAgents) {
            List<ObservedFact> facts = observedFactMapper.selectList(new LambdaQueryWrapper<ObservedFact>()
                    .eq(ObservedFact::getSourceAgentId, agent.getAgentId()));
            for (ObservedFact fact : facts) {
                affectedSubjects.add(fact.getSubjectId());
                observedFactMapper.deleteById(fact.getId());
                hollowed++;
            }
        }

        List<String> suspended = new ArrayList<>();
        List<String> voidedPlans = new ArrayList<>();
        for (String subjectId : affectedSubjects) {
            ConflictDetectionService.HollowSuspendResult result =
                    conflictDetectionService.onObservationBecameHollow(subjectId, CuratedRelationType.RUNS_ON);
            if (result.suspendedConflictId() != null) {
                suspended.add(result.suspendedConflictId());
            }
            voidedPlans.addAll(result.voidedPlanIds());
        }

        return new HeartbeatTimeoutScanResponse(
                staleAgents.size(),
                hollowed,
                List.copyOf(affectedSubjects),
                List.copyOf(suspended),
                List.copyOf(voidedPlans)
        );
    }

    /**
     * True when the agent that sourced this fact has timed out (or agent row missing).
     */
    @Transactional(readOnly = true)
    public boolean isObservedFactStale(ObservedFact fact) {
        if (fact == null) {
            return true;
        }
        if (fact.getSourceAgentId() == null || fact.getSourceAgentId().isBlank()) {
            return false;
        }
        HostAgent agent = hostAgentMapper.selectById(fact.getSourceAgentId());
        if (agent == null || agent.getLastHeartbeatAt() == null) {
            return true;
        }
        return agent.getLastHeartbeatAt().isBefore(heartbeatCutoff());
    }

    /**
     * True when this host has an Agent and the latest heartbeat is past the timeout.
     * No agent row is not a timeout: that host never reported, so 身份失联 stays 身份失联.
     */
    @Transactional(readOnly = true)
    public boolean isHostChannelTimedOut(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            return false;
        }
        List<HostAgent> agents = hostAgentMapper.selectList(new LambdaQueryWrapper<HostAgent>()
                .eq(HostAgent::getHostId, hostId));
        if (agents.isEmpty()) {
            return false;
        }
        Instant latest = null;
        for (HostAgent agent : agents) {
            Instant at = agent.getLastHeartbeatAt();
            if (at != null && (latest == null || at.isAfter(latest))) {
                latest = at;
            }
        }
        if (latest == null) {
            return true;
        }
        return latest.isBefore(heartbeatCutoff());
    }

    private Instant heartbeatCutoff() {
        return Instant.now(clock).minus(observationProperties.getHeartbeatTimeout());
    }
}
