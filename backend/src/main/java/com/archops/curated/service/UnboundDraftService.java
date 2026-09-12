package com.archops.curated.service;

import com.archops.common.exception.BusinessException;
import com.archops.common.json.PersistentJson;
import com.archops.curated.CuratedObjectLabels;
import com.archops.curated.domain.CuratedDraft;
import com.archops.curated.domain.CuratedDraftEvent;
import com.archops.curated.domain.CuratedDraftEventType;
import com.archops.curated.domain.CuratedDraftItem;
import com.archops.curated.domain.CuratedDraftItemKind;
import com.archops.curated.domain.CuratedDraftItemStatus;
import com.archops.curated.domain.CuratedDraftOrigin;
import com.archops.curated.domain.CuratedDraftStatus;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.ConfirmRunsOnRequest;
import com.archops.curated.dto.CreateContainerRequest;
import com.archops.curated.dto.CuratedDraftResponse;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.mapper.CuratedDraftEventMapper;
import com.archops.curated.mapper.CuratedDraftItemMapper;
import com.archops.curated.mapper.CuratedDraftMapper;
import com.archops.observed.domain.IdentityLostMark;
import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.domain.UnboundBindMemory;
import com.archops.observed.domain.UnboundObservationCandidate;
import com.archops.observed.domain.UnboundReason;
import com.archops.observed.mapper.IdentityLostMarkMapper;
import com.archops.observed.mapper.ObservedFactMapper;
import com.archops.observed.mapper.UnboundBindMemoryMapper;
import com.archops.observed.mapper.UnboundObservationCandidateMapper;
import com.archops.user.security.AuthUserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 未绑定 / 身份失联 rebind 草案 origin. 改理想 item review stays on CuratedDraftService.
 */
@Service
public class UnboundDraftService {

    private final CuratedDraftMapper curatedDraftMapper;
    private final CuratedDraftItemMapper curatedDraftItemMapper;
    private final CuratedDraftEventMapper curatedDraftEventMapper;
    private final UnboundObservationCandidateMapper unboundObservationCandidateMapper;
    private final IdentityLostMarkMapper identityLostMarkMapper;
    private final ObservedFactMapper observedFactMapper;
    private final UnboundBindMemoryMapper unboundBindMemoryMapper;
    private final CuratedTruthService curatedTruthService;
    private final CuratedDraftService curatedDraftService;
    private final PersistentJson persistentJson;

    public UnboundDraftService(
            CuratedDraftMapper curatedDraftMapper,
            CuratedDraftItemMapper curatedDraftItemMapper,
            CuratedDraftEventMapper curatedDraftEventMapper,
            UnboundObservationCandidateMapper unboundObservationCandidateMapper,
            IdentityLostMarkMapper identityLostMarkMapper,
            ObservedFactMapper observedFactMapper,
            UnboundBindMemoryMapper unboundBindMemoryMapper,
            CuratedTruthService curatedTruthService,
            CuratedDraftService curatedDraftService,
            PersistentJson persistentJson
    ) {
        this.curatedDraftMapper = curatedDraftMapper;
        this.curatedDraftItemMapper = curatedDraftItemMapper;
        this.curatedDraftEventMapper = curatedDraftEventMapper;
        this.unboundObservationCandidateMapper = unboundObservationCandidateMapper;
        this.identityLostMarkMapper = identityLostMarkMapper;
        this.observedFactMapper = observedFactMapper;
        this.unboundBindMemoryMapper = unboundBindMemoryMapper;
        this.curatedTruthService = curatedTruthService;
        this.curatedDraftService = curatedDraftService;
        this.persistentJson = persistentJson;
    }

    @Transactional
    public CuratedDraftResponse createFromUnboundCandidate(String candidateId, AuthUserPrincipal actor) {
        UnboundObservationCandidate candidate = unboundObservationCandidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException("UNBOUND_CANDIDATE_NOT_FOUND",
                    "No unbound observation candidate: " + candidateId);
        }
        if (findOpenUnboundForCandidate(candidateId) != null
                || findOpenUnboundForHostRuntime(candidate.getSourceHostId(), candidate.getRuntimeId()) != null) {
            throw new BusinessException("UNBOUND_DRAFT_ALREADY_OPEN",
                    "Field entity already has an open 未绑定草案");
        }

        Instant now = Instant.now();
        CuratedDraft draft = new CuratedDraft();
        draft.setId("draft-" + UUID.randomUUID());
        draft.setConflictId(null);
        draft.setDiagnosisId(null);
        draft.setSelectedForkId(null);
        draft.setOrigin(CuratedDraftOrigin.UNBOUND_CANDIDATE);
        draft.setCandidateId(candidate.getId());
        draft.setSourceHostId(candidate.getSourceHostId());
        draft.setRuntimeId(candidate.getRuntimeId());
        draft.setStatus(CuratedDraftStatus.OPEN);
        draft.setCreatedBy(actor.getUserId());
        draft.setCreatedAt(now);
        try {
            curatedDraftMapper.insert(draft);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("UNBOUND_DRAFT_ALREADY_OPEN",
                    "Field entity already has an open 未绑定草案");
        }

        List<CuratedDraftItem> items = buildUnboundItems(draft.getId(), candidate, now);
        for (CuratedDraftItem item : items) {
            curatedDraftItemMapper.insert(item);
        }
        appendDraftEvent(draft.getId(), CuratedDraftEventType.DRAFT_CREATED, actor.getUserId(), Map.of(
                "draftId", draft.getId(),
                "hint", "草案已创建",
                "origin", CuratedDraftOrigin.UNBOUND_CANDIDATE.name()
        ));
        return curatedDraftService.getByDraftId(draft.getId());
    }

    /**
     * 标签命中收尾：作废仍指向被消费候选 / 被消费现场实体键 / 绑到 X / 已接受新建主语为 X
     * 的 OPEN 未绑定草案。不改条目状态，不写策展，不作废改理想草案。
     */
    @Transactional
    public void voidOpenUnboundAfterLabelMatch(
            String curatedObjectId,
            Collection<String> consumedCandidateIds,
            Collection<String> consumedHostRuntimeKeys
    ) {
        Set<String> candidateIds = consumedCandidateIds == null ? Set.of() : Set.copyOf(consumedCandidateIds);
        Set<String> hostRuntimeKeys = consumedHostRuntimeKeys == null ? Set.of() : Set.copyOf(consumedHostRuntimeKeys);
        List<CuratedDraft> openUnbound = curatedDraftMapper.selectList(new LambdaQueryWrapper<CuratedDraft>()
                .eq(CuratedDraft::getOrigin, CuratedDraftOrigin.UNBOUND_CANDIDATE)
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN));
        for (CuratedDraft draft : openUnbound) {
            if (shouldVoidUnboundDraft(draft, curatedObjectId, candidateIds, hostRuntimeKeys)) {
                voidUnboundDraft(draft.getId());
            }
        }
    }

    @Transactional
    public CuratedDraftResponse acceptUnboundItem(String draftId, String itemId, AuthUserPrincipal actor) {
        UnboundItemReview review = beginUnboundItemReview(draftId, itemId);
        if (review.item().getKind() == CuratedDraftItemKind.CREATE_CONTAINER_FROM_UNBOUND
                || review.item().getKind() == CuratedDraftItemKind.BIND_UNBOUND_TO_EXISTING) {
            requireUnboundCandidateNotConsumed(review.draft());
        }
        applyUnboundAccept(review.draft(), review.item(), actor.getUserId());
        markItem(review.item(), CuratedDraftItemStatus.ACCEPTED);
        appendDraftEvent(review.draft().getId(), CuratedDraftEventType.DRAFT_ITEM_ACCEPTED, actor.getUserId(),
                unboundItemAuditDetail(review, "草案条目已接受"));
        return curatedDraftService.getByDraftId(review.draft().getId());
    }

    @Transactional
    public CuratedDraftResponse rejectUnboundItem(String draftId, String itemId, AuthUserPrincipal actor) {
        UnboundItemReview review = beginUnboundItemReview(draftId, itemId);
        markItem(review.item(), CuratedDraftItemStatus.REJECTED);
        appendDraftEvent(review.draft().getId(), CuratedDraftEventType.DRAFT_ITEM_REJECTED, actor.getUserId(),
                unboundItemAuditDetail(review, "草案条目已拒绝"));
        return curatedDraftService.getByDraftId(review.draft().getId());
    }

    static String hostRuntimeKey(String sourceHostId, String runtimeId) {
        return sourceHostId + "\0" + runtimeId;
    }

    static BusinessException bindMemoryRace(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause == null ? "" : String.valueOf(cause.getMessage());
        return detail.contains("unbound_bind_memory_object_uq")
                ? targetAlreadyBound()
                : candidateConsumed();
    }

    private boolean shouldVoidUnboundDraft(
            CuratedDraft draft,
            String curatedObjectId,
            Set<String> candidateIds,
            Set<String> hostRuntimeKeys
    ) {
        if (draft.getCandidateId() != null && candidateIds.contains(draft.getCandidateId())) {
            return true;
        }
        if (draft.getRuntimeId() != null
                && hostRuntimeKeys.contains(hostRuntimeKey(draft.getSourceHostId(), draft.getRuntimeId()))) {
            return true;
        }
        List<CuratedDraftItem> items = curatedDraftItemMapper.selectList(new LambdaQueryWrapper<CuratedDraftItem>()
                .eq(CuratedDraftItem::getDraftId, draft.getId()));
        for (CuratedDraftItem item : items) {
            if (!curatedObjectId.equals(item.getSubjectId())) {
                continue;
            }
            if (item.getKind() == CuratedDraftItemKind.BIND_UNBOUND_TO_EXISTING) {
                return true;
            }
            if (item.getKind() == CuratedDraftItemKind.CREATE_CONTAINER_FROM_UNBOUND
                    && item.getStatus() == CuratedDraftItemStatus.ACCEPTED) {
                return true;
            }
        }
        return false;
    }

    private void voidUnboundDraft(String draftId) {
        int updated = curatedDraftMapper.update(null, new LambdaUpdateWrapper<CuratedDraft>()
                .eq(CuratedDraft::getId, draftId)
                .eq(CuratedDraft::getOrigin, CuratedDraftOrigin.UNBOUND_CANDIDATE)
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN)
                .set(CuratedDraft::getStatus, CuratedDraftStatus.VOIDED));
        if (updated != 1) {
            return;
        }
        appendDraftEvent(draftId, CuratedDraftEventType.DRAFT_VOIDED, null, Map.of(
                "draftId", draftId,
                "hint", "草案已作废"
        ));
    }

    private UnboundItemReview beginUnboundItemReview(String draftId, String itemId) {
        CuratedDraft draft = curatedDraftMapper.selectById(draftId);
        if (draft == null || draft.getOrigin() != CuratedDraftOrigin.UNBOUND_CANDIDATE) {
            throw new BusinessException("DRAFT_NOT_FOUND", "No open 未绑定草案: " + draftId);
        }
        if (draft.getStatus() == CuratedDraftStatus.VOIDED) {
            throw new BusinessException("DRAFT_VOIDED", "草案已作废");
        }
        if (draft.getStatus() != CuratedDraftStatus.OPEN) {
            throw new BusinessException("DRAFT_NOT_FOUND", "No open 未绑定草案: " + draftId);
        }
        CuratedDraftItem item = requireItemOnDraft(draft.getId(), itemId);
        return new UnboundItemReview(draft, item);
    }

    private void requireUnboundCandidateNotConsumed(CuratedDraft draft) {
        Long existing = unboundBindMemoryMapper.selectCount(new LambdaQueryWrapper<UnboundBindMemory>()
                .eq(UnboundBindMemory::getSourceHostId, draft.getSourceHostId())
                .eq(UnboundBindMemory::getRuntimeId, draft.getRuntimeId()));
        if (existing != null && existing > 0) {
            throw candidateConsumed();
        }
    }

    private void applyUnboundAccept(CuratedDraft draft, CuratedDraftItem item, String actorUserId) {
        if (item.getKind() == CuratedDraftItemKind.CREATE_CONTAINER_FROM_UNBOUND) {
            writeAcceptedCreateContainer(draft, item, actorUserId);
            return;
        }
        if (item.getKind() == CuratedDraftItemKind.CURATED_RUNS_ON_INSERT) {
            writeAcceptedFirstRunsOn(item, actorUserId);
            return;
        }
        if (item.getKind() == CuratedDraftItemKind.BIND_UNBOUND_TO_EXISTING) {
            writeAcceptedBind(draft, item);
            return;
        }
        throw new BusinessException("UNBOUND_ITEM_KIND_UNSUPPORTED",
                "未绑定草案本票不审该条目 kind: " + item.getKind());
    }

    private void writeAcceptedFirstRunsOn(CuratedDraftItem item, String actorUserId) {
        CuratedDraftItem create = requireCreateAcceptedBeforeRunsOn(item);
        item.setSubjectId(create.getSubjectId());
        curatedTruthService.confirmRunsOn(
                new ConfirmRunsOnRequest(create.getSubjectId(), item.getToHostId()), actorUserId);
    }

    private CuratedDraftItem requireCreateAcceptedBeforeRunsOn(CuratedDraftItem runsOnItem) {
        CuratedDraftItem create = findSibling(runsOnItem.getDraftId(), CuratedDraftItemKind.CREATE_CONTAINER_FROM_UNBOUND);
        if (create == null
                || create.getStatus() != CuratedDraftItemStatus.ACCEPTED
                || create.getSubjectId() == null
                || create.getSubjectId().isBlank()) {
            throw new BusinessException("UNBOUND_RUNS_ON_BEFORE_CREATE",
                    "不能在新建策展容器之前接受策展「运行于」");
        }
        return create;
    }

    private CuratedDraftItem findSibling(String draftId, CuratedDraftItemKind kind) {
        return curatedDraftItemMapper.selectList(new LambdaQueryWrapper<CuratedDraftItem>()
                        .eq(CuratedDraftItem::getDraftId, draftId)
                        .eq(CuratedDraftItem::getKind, kind))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void writeAcceptedCreateContainer(CuratedDraft draft, CuratedDraftItem item, String actorUserId) {
        Map<String, Object> payload = readPayloadMap(item.getPayloadJson());
        String name = stringPayload(payload, "proposedName");
        String objectId = stringPayload(payload, "immutableObjectId");
        if (name == null || name.isBlank() || objectId == null || objectId.isBlank()) {
            throw new BusinessException("UNBOUND_CREATE_IMMUTABLE_ID_MISSING",
                    "MISSING_LABEL 新建没有可写的现场不可变 object id");
        }
        CuratedObjectResponse created = curatedTruthService.createContainer(
                new CreateContainerRequest(name, objectId), actorUserId);
        item.setSubjectId(created.id());
        rememberBind(draft, created.id());
    }

    private void writeAcceptedBind(CuratedDraft draft, CuratedDraftItem item) {
        String targetId = item.getSubjectId();
        if (targetId == null || targetId.isBlank()) {
            throw new BusinessException("UNBOUND_ITEM_KIND_UNSUPPORTED",
                    "绑到已有缺少目标策展对象");
        }
        IdentityLostMark lost = identityLostMarkMapper.selectById(targetId);
        if (lost == null || labelMatchedAfterIdentityLoss(lost)) {
            throw new BusinessException("UNBOUND_BIND_TARGET_HEALTHY",
                    "只能绑到仍身份失联、且失联之后未再标签命中的对象");
        }
        requireTargetNotAlreadyBound(targetId);
        rememberBind(draft, targetId);
    }

    private void requireTargetNotAlreadyBound(String targetId) {
        if (alreadyBound(targetId)) {
            throw targetAlreadyBound();
        }
    }

    private boolean alreadyBound(String curatedObjectId) {
        Long bound = unboundBindMemoryMapper.selectCount(new LambdaQueryWrapper<UnboundBindMemory>()
                .eq(UnboundBindMemory::getCuratedObjectId, curatedObjectId));
        return bound != null && bound > 0;
    }

    private boolean labelMatchedAfterIdentityLoss(IdentityLostMark lost) {
        ObservedFact observed = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, lost.getCuratedObjectId())
                .eq(ObservedFact::getRelationType, CuratedRelationType.RUNS_ON));
        if (observed == null || observed.getAvailability() != ObservedAvailability.PRESENT) {
            return false;
        }
        return !observed.getObservedAt().isBefore(lost.getMarkedAt());
    }

    private void rememberBind(CuratedDraft draft, String curatedObjectId) {
        UnboundBindMemory memory = new UnboundBindMemory();
        memory.setId("ubm-" + UUID.randomUUID());
        memory.setSourceHostId(draft.getSourceHostId());
        memory.setRuntimeId(draft.getRuntimeId());
        memory.setCuratedObjectId(curatedObjectId);
        memory.setCreatedAt(Instant.now());
        try {
            unboundBindMemoryMapper.insert(memory);
        } catch (DataIntegrityViolationException ex) {
            throw bindMemoryRace(ex);
        }
    }

    private static BusinessException targetAlreadyBound() {
        return new BusinessException("UNBOUND_BIND_TARGET_ALREADY_BOUND",
                "该策展对象已由另一个现场实体绑定，不能再绑第二个");
    }

    private static BusinessException candidateConsumed() {
        return new BusinessException("UNBOUND_CANDIDATE_CONSUMED",
                "该现场实体已因绑定或新建被消费，不能再次并入");
    }

    private static String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private void markItem(CuratedDraftItem item, CuratedDraftItemStatus status) {
        if (item.getStatus() != CuratedDraftItemStatus.PENDING) {
            throw new BusinessException("DRAFT_ITEM_NOT_PENDING",
                    "草案条目已不是待确认: " + item.getId());
        }
        item.setStatus(status);
        curatedDraftItemMapper.updateById(item);
    }

    private CuratedDraftItem requireItemOnDraft(String draftId, String itemId) {
        CuratedDraftItem item = curatedDraftItemMapper.selectById(itemId);
        if (item == null || !draftId.equals(item.getDraftId())) {
            throw new BusinessException("DRAFT_ITEM_NOT_FOUND",
                    "No 草案 item " + itemId + " on the open draft");
        }
        return item;
    }

    private CuratedDraft findOpenUnboundForCandidate(String candidateId) {
        return curatedDraftMapper.selectOne(new LambdaQueryWrapper<CuratedDraft>()
                .eq(CuratedDraft::getCandidateId, candidateId)
                .eq(CuratedDraft::getOrigin, CuratedDraftOrigin.UNBOUND_CANDIDATE)
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN)
                .orderByDesc(CuratedDraft::getCreatedAt)
                .last("LIMIT 1"));
    }

    private CuratedDraft findOpenUnboundForHostRuntime(String sourceHostId, String runtimeId) {
        if (sourceHostId == null || runtimeId == null || runtimeId.isBlank()) {
            return null;
        }
        return curatedDraftMapper.selectOne(new LambdaQueryWrapper<CuratedDraft>()
                .eq(CuratedDraft::getSourceHostId, sourceHostId)
                .eq(CuratedDraft::getRuntimeId, runtimeId)
                .eq(CuratedDraft::getOrigin, CuratedDraftOrigin.UNBOUND_CANDIDATE)
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN)
                .orderByDesc(CuratedDraft::getCreatedAt)
                .last("LIMIT 1"));
    }

    private List<CuratedDraftItem> buildUnboundItems(
            String draftId,
            UnboundObservationCandidate candidate,
            Instant now
    ) {
        List<CuratedDraftItem> items = new ArrayList<>();
        int seq = 1;
        if (candidate.getReason() == UnboundReason.UNKNOWN_OBJECT_ID) {
            IdentityLostMark lost = findIdentityLostOnHost(candidate.getSourceHostId());
            if (lost != null) {
                items.add(newItem(draftId, seq++, CuratedDraftItemKind.BIND_UNBOUND_TO_EXISTING,
                        lost.getCuratedObjectId(), null, null, "{}", now));
            }
            Map<String, String> labels = readStringMap(candidate.getLabelsJson());
            String immutableObjectId = labels.get(CuratedObjectLabels.OBJECT_ID_KEY);
            Map<String, Object> createPayload = new LinkedHashMap<>();
            createPayload.put("immutableObjectId", immutableObjectId);
            createPayload.put("labels", Map.of(CuratedObjectLabels.OBJECT_ID_KEY, immutableObjectId));
            createPayload.put("proposedName", candidate.getName());
            items.add(newItem(draftId, seq++, CuratedDraftItemKind.CREATE_CONTAINER_FROM_UNBOUND,
                    null, null, null, writeJson(createPayload), now));
            items.add(newItem(draftId, seq++, CuratedDraftItemKind.CURATED_RUNS_ON_INSERT,
                    null, null, candidate.getSourceHostId(), "{}", now));
        } else if (candidate.getReason() == UnboundReason.MISSING_LABEL) {
            IdentityLostMark lost = findIdentityLostOnHost(candidate.getSourceHostId());
            if (lost == null) {
                throw new BusinessException("UNBOUND_DRAFT_FIXTURE_UNAVAILABLE",
                        "MISSING_LABEL candidate has no identity-lost target on host");
            }
            items.add(newItem(draftId, seq++, CuratedDraftItemKind.BIND_UNBOUND_TO_EXISTING,
                    lost.getCuratedObjectId(), null, null, "{}", now));
            Map<String, Object> createPayload = new LinkedHashMap<>();
            createPayload.put("immutableObjectId", null);
            createPayload.put("proposedName", candidate.getName());
            items.add(newItem(draftId, seq++, CuratedDraftItemKind.CREATE_CONTAINER_FROM_UNBOUND,
                    null, null, null, writeJson(createPayload), now));
        } else {
            throw new BusinessException("UNBOUND_DRAFT_FIXTURE_UNAVAILABLE",
                    "Unsupported unbound reason for draft fixture");
        }
        return items;
    }

    private IdentityLostMark findIdentityLostOnHost(String hostId) {
        List<IdentityLostMark> marks = identityLostMarkMapper.selectList(
                new LambdaQueryWrapper<IdentityLostMark>()
                        .eq(IdentityLostMark::getSourceHostId, hostId)
                        .orderByAsc(IdentityLostMark::getCuratedObjectId));
        for (IdentityLostMark mark : marks) {
            if (!alreadyBound(mark.getCuratedObjectId())) {
                return mark;
            }
        }
        return null;
    }

    private CuratedDraftItem newItem(
            String draftId,
            int seq,
            CuratedDraftItemKind kind,
            String subjectId,
            String fromHostId,
            String toHostId,
            String payloadJson,
            Instant now
    ) {
        CuratedDraftItem item = new CuratedDraftItem();
        item.setId("ditem-" + UUID.randomUUID());
        item.setDraftId(draftId);
        item.setSeq(seq);
        item.setKind(kind);
        item.setStatus(CuratedDraftItemStatus.PENDING);
        item.setSubjectId(subjectId);
        item.setFromHostId(fromHostId);
        item.setToHostId(toHostId);
        item.setPayloadJson(payloadJson == null ? "{}" : payloadJson);
        item.setCreatedAt(now);
        return item;
    }

    private void appendDraftEvent(
            String draftId,
            CuratedDraftEventType type,
            String actorUserId,
            Map<String, Object> detail
    ) {
        CuratedDraftEvent event = new CuratedDraftEvent();
        event.setId("devt-" + UUID.randomUUID());
        event.setDraftId(draftId);
        event.setEventType(type);
        event.setActorUserId(actorUserId);
        event.setDetailJson(writeJson(detail == null ? Map.of() : detail));
        event.setCreatedAt(Instant.now());
        curatedDraftEventMapper.insert(event);
    }

    private Map<String, Object> readPayloadMap(String payloadJson) {
        return persistentJson.read(payloadJson, new TypeReference<Map<String, Object>>() {
        }, Map.of());
    }

    private Map<String, String> readStringMap(String json) {
        return persistentJson.read(json, new TypeReference<Map<String, String>>() {
        }, Map.of());
    }

    private String writeJson(Object value) {
        return persistentJson.write(value);
    }

    private static Map<String, Object> unboundItemAuditDetail(UnboundItemReview review, String hint) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("draftId", review.draft().getId());
        detail.put("itemId", review.item().getId());
        detail.put("subjectId", review.item().getSubjectId());
        detail.put("hint", hint);
        return detail;
    }

    private record UnboundItemReview(CuratedDraft draft, CuratedDraftItem item) {
    }
}
