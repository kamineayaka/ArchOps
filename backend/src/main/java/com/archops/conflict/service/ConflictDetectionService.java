package com.archops.conflict.service;

import com.archops.common.json.PersistentJson;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.domain.CuratedFact;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.service.CuratedDraftService;
import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.mapper.ObservedFactMapper;
import com.archops.plan.service.OperationPlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits conflict warnings when curated ≠ currently available observed on a merge key.
 * When tracks become equal, transitions to PENDING_CLOSE (never auto-closes).
 * Observed-target 升级 voids OPEN 改理想草案 and active 操作计划;
 * draft SQL stays in CuratedDraftService. HTTP DTO assembly lives on ConflictCaseAssembler.
 */
@Service
public class ConflictDetectionService {

    private static final String IDENTITY_LOST_REASON = "identity_lost";
    private static final String CONFLICT_UPGRADE_REASON = "conflict_upgrade";

    private static final List<ConflictStatus> ACTIVE = List.of(
            ConflictStatus.OPEN,
            ConflictStatus.PENDING_CLOSE,
            ConflictStatus.SUSPENDED
    );

    private final ConflictCaseMapper conflictCaseMapper;
    private final CuratedFactMapper curatedFactMapper;
    private final ObservedFactMapper observedFactMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final ConflictEventService conflictEventService;
    private final OperationPlanService operationPlanService;
    private final CuratedDraftService curatedDraftService;
    private final PersistentJson persistentJson;

    public ConflictDetectionService(
            ConflictCaseMapper conflictCaseMapper,
            CuratedFactMapper curatedFactMapper,
            ObservedFactMapper observedFactMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            ConflictEventService conflictEventService,
            @Lazy OperationPlanService operationPlanService,
            @Lazy CuratedDraftService curatedDraftService,
            PersistentJson persistentJson
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.curatedFactMapper = curatedFactMapper;
        this.observedFactMapper = observedFactMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.conflictEventService = conflictEventService;
        this.operationPlanService = operationPlanService;
        this.curatedDraftService = curatedDraftService;
        this.persistentJson = persistentJson;
    }

    /**
     * Reconcile active conflict for merge key (subject + relation) after an observed write.
     */
    @Transactional
    public void reconcileAfterObservedWrite(String subjectId, CuratedRelationType relationType) {
        reconcileMergeKey(subjectId, relationType);
    }

    /**
     * Same compare/upgrade/pending-close/suspend engine for observed ingest and accepted 草案 writes.
     */
    @Transactional
    public void reconcileMergeKey(String subjectId, CuratedRelationType relationType) {
        CuratedFact curated = curatedFactMapper.selectOne(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getSubjectId, subjectId)
                .eq(CuratedFact::getRelationType, relationType));
        ObservedFact observed = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, subjectId)
                .eq(ObservedFact::getRelationType, relationType));

        // 观测空洞: no usable observed value → do not open a both-sides conflict;
        // if an OPEN/PENDING_CLOSE exists, suspend + void plans and any OPEN 草案.
        if (curated == null) {
            return;
        }
        ConflictCase active = findActive(subjectId, relationType);
        if (observed == null) {
            if (active != null && (active.getStatus() == ConflictStatus.OPEN
                    || active.getStatus() == ConflictStatus.PENDING_CLOSE)) {
                onObservationBecameHollow(subjectId, relationType);
            }
            return;
        }

        boolean equal = isEqual(curated, observed);
        Instant now = Instant.now();

        if (equal) {
            if (active == null) {
                // No open conflict — equality with no prior warn does not create a case.
                return;
            }
            if (active.getStatus() == ConflictStatus.PENDING_CLOSE) {
                // Keep pending close; refresh snapshot if needed.
                if (!sameObservedSnapshot(active, observed)
                        || !Objects.equals(active.getCuratedTargetId(), curated.getTargetId())) {
                    conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                            .eq(ConflictCase::getId, active.getId())
                            .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                            .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                            .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                            .set(ConflictCase::getUpdatedAt, now));
                }
                return;
            }
            if (active.getStatus() == ConflictStatus.SUSPENDED) {
                resumeFromSuspendedToPendingClose(active, curated, observed, now);
                return;
            }
            markPendingClose(active, curated, observed, now);
            return;
        }

        // Unequal tracks.
        if (active == null) {
            createOpen(subjectId, relationType, curated, observed, now);
            return;
        }

        if (active.getStatus() == ConflictStatus.PENDING_CLOSE) {
            // Drift after alignment — back to OPEN conflict (not force-close).
            reopenFromPendingClose(active, curated, observed, now);
            return;
        }

        if (active.getStatus() == ConflictStatus.SUSPENDED) {
            resumeFromSuspendedToOpen(active, curated, observed, now);
            return;
        }

        if (sameObservedSnapshot(active, observed) && Objects.equals(active.getCuratedTargetId(), curated.getTargetId())) {
            return;
        }

        upgradeOpen(active, curated, observed, now);
    }

    /**
     * Heartbeat timeout / fact retirement: suspend active conflict (not close),
     * void plans, and void any OPEN 改理想草案.
     */
    @Transactional
    public HollowSuspendResult onObservationBecameHollow(String subjectId, CuratedRelationType relationType) {
        ConflictCase active = findActive(subjectId, relationType);
        if (active == null) {
            return new HollowSuspendResult(null, List.of());
        }
        if (active.getStatus() == ConflictStatus.SUSPENDED) {
            List<String> voided = operationPlanService.voidActivePlansForConflict(
                    active.getId(), "observation_hollow_heartbeat_timeout");
            return new HollowSuspendResult(active.getId(), voided);
        }
        if (active.getStatus() != ConflictStatus.OPEN && active.getStatus() != ConflictStatus.PENDING_CLOSE) {
            return new HollowSuspendResult(null, List.of());
        }

        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, active.getId())
                .in(ConflictCase::getStatus, List.of(ConflictStatus.OPEN, ConflictStatus.PENDING_CLOSE))
                .set(ConflictCase::getStatus, ConflictStatus.SUSPENDED)
                .set(ConflictCase::getSuspendedAt, now)
                .set(ConflictCase::getPendingCloseAt, null)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(active.getId(), ConflictEventType.SUSPENDED, null, Map.of(
                "reason", "observation_hollow_heartbeat_timeout",
                "subjectId", subjectId,
                "relationType", relationType.name()
        ));
        List<String> voided = voidActivePlans(active.getId(), "observation_hollow_heartbeat_timeout");
        curatedDraftService.voidOpenForConflict(active.getId(), "observation_hollow_heartbeat_timeout");
        conflictDiagnosisService.scheduleAsyncDiagnosis(active.getId());
        return new HollowSuspendResult(active.getId(), voided);
    }

    /**
     * 身份失联落地：已有冲突保留。PENDING_CLOSE 无法再看见相等，退回 OPEN。
     * 不是空洞挂起，不新增 ConflictStatus。
     */
    @Transactional
    public void onIdentityLost(String subjectId) {
        ConflictCase active = findActive(subjectId, CuratedRelationType.RUNS_ON);
        if (active == null) {
            return;
        }
        if (active.getStatus() == ConflictStatus.PENDING_CLOSE) {
            Instant now = Instant.now();
            conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                    .eq(ConflictCase::getId, active.getId())
                    .eq(ConflictCase::getStatus, ConflictStatus.PENDING_CLOSE)
                    .set(ConflictCase::getStatus, ConflictStatus.OPEN)
                    .set(ConflictCase::getPendingCloseAt, null)
                    .set(ConflictCase::getUpdatedAt, now));
            conflictEventService.append(active.getId(), ConflictEventType.UPGRADED, null, Map.of(
                    "reason", IDENTITY_LOST_REASON,
                    "subjectId", subjectId,
                    "relationType", CuratedRelationType.RUNS_ON.name()
            ));
        }
        voidActivePlans(active.getId(), IDENTITY_LOST_REASON);
        curatedDraftService.voidOpenForConflict(active.getId(), IDENTITY_LOST_REASON);
        conflictDiagnosisService.scheduleAsyncDiagnosis(active.getId());
    }

    /**
     * 标签命中清标后闸门解除：重诊，使修实际 / 改理想可再次出现。
     * 不改冲突状态，不作废计划或草案（那是失联落地，不是清标）。
     */
    @Transactional
    public void onIdentityLostCleared(String subjectId) {
        ConflictCase active = findActive(subjectId, CuratedRelationType.RUNS_ON);
        if (active == null) {
            return;
        }
        conflictDiagnosisService.scheduleAsyncDiagnosis(active.getId());
    }

    private List<String> voidActivePlans(String conflictId, String reason) {
        List<String> voided = operationPlanService.voidActivePlansForConflict(conflictId, reason);
        for (String planId : voided) {
            conflictEventService.append(conflictId, ConflictEventType.PLAN_VOIDED, null, Map.of(
                    "planId", planId,
                    "reason", reason
            ));
        }
        return voided;
    }

    /**
     * Re-read curated/observed for confirm-close equality check.
     */
    @Transactional(readOnly = true)
    public boolean tracksCurrentlyEqual(ConflictCase row) {
        CuratedFact curated = curatedFactMapper.selectOne(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getSubjectId, row.getSubjectId())
                .eq(CuratedFact::getRelationType, row.getRelationType()));
        ObservedFact observed = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, row.getSubjectId())
                .eq(ObservedFact::getRelationType, row.getRelationType()));
        if (curated == null || observed == null) {
            return false;
        }
        return isEqual(curated, observed);
    }

    @Transactional(readOnly = true)
    public TrackPair currentTracks(ConflictCase row) {
        CuratedFact curated = curatedFactMapper.selectOne(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getSubjectId, row.getSubjectId())
                .eq(CuratedFact::getRelationType, row.getRelationType()));
        ObservedFact observed = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, row.getSubjectId())
                .eq(ObservedFact::getRelationType, row.getRelationType()));
        return new TrackPair(curated, observed);
    }

    private void markPendingClose(ConflictCase open, CuratedFact curated, ObservedFact observed, Instant now) {
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, open.getId())
                .eq(ConflictCase::getStatus, ConflictStatus.OPEN)
                .set(ConflictCase::getStatus, ConflictStatus.PENDING_CLOSE)
                .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                .set(ConflictCase::getPendingCloseAt, now)
                .set(ConflictCase::getSuspendedAt, null)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(open.getId(), ConflictEventType.PENDING_CLOSE, null, Map.of(
                "curatedTargetId", curated.getTargetId(),
                "observedTargetId", observed.getTargetId() == null ? "" : observed.getTargetId()
        ));
    }

    private void resumeFromSuspendedToPendingClose(
            ConflictCase suspended,
            CuratedFact curated,
            ObservedFact observed,
            Instant now
    ) {
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, suspended.getId())
                .eq(ConflictCase::getStatus, ConflictStatus.SUSPENDED)
                .set(ConflictCase::getStatus, ConflictStatus.PENDING_CLOSE)
                .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                .set(ConflictCase::getPendingCloseAt, now)
                .set(ConflictCase::getSuspendedAt, null)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(suspended.getId(), ConflictEventType.PENDING_CLOSE, null, Map.of(
                "via", "resume_from_suspended",
                "curatedTargetId", curated.getTargetId(),
                "observedTargetId", observed.getTargetId() == null ? "" : observed.getTargetId()
        ));
    }

    private void resumeFromSuspendedToOpen(
            ConflictCase suspended,
            CuratedFact curated,
            ObservedFact observed,
            Instant now
    ) {
        List<LineageRecord> lineage = readLineage(suspended.getObservedLineageJson());
        LineageRecord next = lineageStep(observed, now);
        if (lineage.isEmpty() || !sameStep(lineage.get(lineage.size() - 1), next)) {
            lineage.add(next);
        }
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, suspended.getId())
                .eq(ConflictCase::getStatus, ConflictStatus.SUSPENDED)
                .set(ConflictCase::getStatus, ConflictStatus.OPEN)
                .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                .set(ConflictCase::getObservedLineageJson, writeLineage(lineage))
                .set(ConflictCase::getSuspendedAt, null)
                .set(ConflictCase::getPendingCloseAt, null)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(suspended.getId(), ConflictEventType.UPGRADED, null, Map.of(
                "via", "resume_from_suspended",
                "observedTargetId", observed.getTargetId() == null ? "" : observed.getTargetId()
        ));
        conflictDiagnosisService.scheduleAsyncDiagnosis(suspended.getId());
    }

    private void reopenFromPendingClose(ConflictCase pending, CuratedFact curated, ObservedFact observed, Instant now) {
        List<LineageRecord> lineage = readLineage(pending.getObservedLineageJson());
        LineageRecord next = lineageStep(observed, now);
        if (lineage.isEmpty() || !sameStep(lineage.get(lineage.size() - 1), next)) {
            lineage.add(next);
        }
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, pending.getId())
                .eq(ConflictCase::getStatus, ConflictStatus.PENDING_CLOSE)
                .set(ConflictCase::getStatus, ConflictStatus.OPEN)
                .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                .set(ConflictCase::getObservedLineageJson, writeLineage(lineage))
                .set(ConflictCase::getPendingCloseAt, null)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(pending.getId(), ConflictEventType.UPGRADED, null, Map.of(
                "reason", "drift_after_pending_close",
                "observedTargetId", observed.getTargetId() == null ? "" : observed.getTargetId()
        ));
        voidOpenDraftsAndPlansThenRediagnose(pending.getId());
    }

    private void createOpen(
            String subjectId,
            CuratedRelationType relationType,
            CuratedFact curated,
            ObservedFact observed,
            Instant now
    ) {
        ConflictCase created = new ConflictCase();
        created.setId(newId("cnf"));
        created.setSubjectId(subjectId);
        created.setRelationType(relationType);
        created.setStatus(ConflictStatus.OPEN);
        created.setCuratedTargetId(curated.getTargetId());
        created.setObservedAvailability(observed.getAvailability());
        created.setObservedTargetId(observed.getTargetId());
        created.setObservedLineageJson(writeLineage(List.of(lineageStep(observed, now))));
        created.setFirstWarnedAt(now);
        created.setUpdatedAt(now);
        created.setAcknowledged(false);
        created.setAcknowledgedAt(null);
        created.setOwnerUserId(null);
        created.setHandlerUserId(null);
        created.setHandlerAcceptance(HandlerAcceptance.NONE);
        conflictCaseMapper.insert(created);
        conflictEventService.append(created.getId(), ConflictEventType.WARNED, null, Map.of(
                "curatedTargetId", curated.getTargetId(),
                "observedTargetId", observed.getTargetId() == null ? "" : observed.getTargetId()
        ));
        // Async diagnosis — never blocks the warning emission.
        conflictDiagnosisService.scheduleAsyncDiagnosis(created.getId());
    }

    private void upgradeOpen(ConflictCase open, CuratedFact curated, ObservedFact observed, Instant now) {
        List<LineageRecord> lineage = readLineage(open.getObservedLineageJson());
        LineageRecord next = lineageStep(observed, now);
        if (lineage.isEmpty() || !sameStep(lineage.get(lineage.size() - 1), next)) {
            lineage.add(next);
        }

        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, open.getId())
                .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                .set(ConflictCase::getObservedLineageJson, writeLineage(lineage))
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(open.getId(), ConflictEventType.UPGRADED, null, Map.of(
                "observedTargetId", observed.getTargetId() == null ? "" : observed.getTargetId()
        ));
        voidOpenDraftsAndPlansThenRediagnose(open.getId());
    }

    private void voidOpenDraftsAndPlansThenRediagnose(String conflictId) {
        curatedDraftService.voidOpenForConflict(conflictId, CONFLICT_UPGRADE_REASON);
        voidActivePlans(conflictId, CONFLICT_UPGRADE_REASON);
        conflictDiagnosisService.scheduleAsyncDiagnosis(conflictId);
    }

    private ConflictCase findActive(String subjectId, CuratedRelationType relationType) {
        return conflictCaseMapper.selectOne(new LambdaQueryWrapper<ConflictCase>()
                .eq(ConflictCase::getSubjectId, subjectId)
                .eq(ConflictCase::getRelationType, relationType)
                .in(ConflictCase::getStatus, ACTIVE));
    }

    private boolean isEqual(CuratedFact curated, ObservedFact observed) {
        if (observed.getAvailability() == ObservedAvailability.ABSENT) {
            return false;
        }
        return Objects.equals(curated.getTargetId(), observed.getTargetId());
    }

    private boolean sameObservedSnapshot(ConflictCase open, ObservedFact observed) {
        return open.getObservedAvailability() == observed.getAvailability()
                && Objects.equals(open.getObservedTargetId(), observed.getTargetId());
    }

    private LineageRecord lineageStep(ObservedFact observed, Instant at) {
        return new LineageRecord(observed.getAvailability(), observed.getTargetId(), at);
    }

    private boolean sameStep(LineageRecord a, LineageRecord b) {
        return a.availability() == b.availability() && Objects.equals(a.hostId(), b.hostId());
    }

    private List<LineageRecord> readLineage(String json) {
        return new ArrayList<>(persistentJson.read(json, new TypeReference<>() {
        }, List.of()));
    }

    private String writeLineage(List<LineageRecord> lineage) {
        return persistentJson.write(lineage);
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record LineageRecord(
            ObservedAvailability availability,
            String hostId,
            Instant at
    ) {
    }

    public record TrackPair(CuratedFact curated, ObservedFact observed) {
    }

    public record HollowSuspendResult(String suspendedConflictId, List<String> voidedPlanIds) {
    }
}
