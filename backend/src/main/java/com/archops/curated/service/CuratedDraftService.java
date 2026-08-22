package com.archops.curated.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.conflict.service.ConflictDetectionService;
import com.archops.conflict.service.ConflictEventService;
import com.archops.curated.domain.CuratedDraft;
import com.archops.curated.domain.CuratedDraftItem;
import com.archops.curated.domain.CuratedDraftItemKind;
import com.archops.curated.domain.CuratedDraftItemStatus;
import com.archops.curated.domain.CuratedDraftStatus;
import com.archops.curated.domain.CuratedFact;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.ConfirmRunsOnRequest;
import com.archops.curated.dto.CreateContainerRequest;
import com.archops.curated.dto.CuratedDraftResponse;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.mapper.CuratedDraftItemMapper;
import com.archops.curated.mapper.CuratedDraftMapper;
import com.archops.curated.mapper.CuratedDraftEventMapper;
import com.archops.curated.dto.CuratedDraftEventResponse;
import com.archops.curated.domain.CuratedDraftEventType;
import com.archops.curated.domain.CuratedDraftEvent;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.curated.domain.CuratedDraftOrigin;
import com.archops.curated.CuratedObjectLabels;
import com.archops.observed.domain.IdentityLostMark;
import com.archops.observed.domain.UnboundBindMemory;
import com.archops.observed.domain.UnboundObservationCandidate;
import com.archops.observed.domain.UnboundReason;
import com.archops.observed.mapper.IdentityLostMarkMapper;
import com.archops.observed.mapper.UnboundBindMemoryMapper;
import com.archops.observed.mapper.UnboundObservationCandidateMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.archops.user.security.AuthUserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule-templated 改理想 草案 (ticket 03), per-item review (ticket 04),
 * and OPEN-draft voiding on 冲突升级/空洞 (ticket 05).
 * Confirmation-before-write is not 策展真相.
 */
@Service
public class CuratedDraftService {

    private final CuratedDraftMapper curatedDraftMapper;
    private final CuratedDraftItemMapper curatedDraftItemMapper;
    private final CuratedFactMapper curatedFactMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictEventService conflictEventService;
    private final ConflictDetectionService conflictDetectionService;
    private final CuratedTruthService curatedTruthService;
    private final UnboundObservationCandidateMapper unboundObservationCandidateMapper;
    private final IdentityLostMarkMapper identityLostMarkMapper;
    private final UnboundBindMemoryMapper unboundBindMemoryMapper;
    private final CuratedDraftEventMapper curatedDraftEventMapper;
    private final ObjectMapper objectMapper;

    public CuratedDraftService(
            CuratedDraftMapper curatedDraftMapper,
            CuratedDraftItemMapper curatedDraftItemMapper,
            CuratedFactMapper curatedFactMapper,
            CuratedObjectMapper curatedObjectMapper,
            ConflictCaseMapper conflictCaseMapper,
            ConflictEventService conflictEventService,
            ConflictDetectionService conflictDetectionService,
            CuratedTruthService curatedTruthService,
            UnboundObservationCandidateMapper unboundObservationCandidateMapper,
            IdentityLostMarkMapper identityLostMarkMapper,
            UnboundBindMemoryMapper unboundBindMemoryMapper,
            CuratedDraftEventMapper curatedDraftEventMapper,
            ObjectMapper objectMapper
    ) {
        this.curatedDraftMapper = curatedDraftMapper;
        this.curatedDraftItemMapper = curatedDraftItemMapper;
        this.curatedFactMapper = curatedFactMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictEventService = conflictEventService;
        this.conflictDetectionService = conflictDetectionService;
        this.curatedTruthService = curatedTruthService;
        this.unboundObservationCandidateMapper = unboundObservationCandidateMapper;
        this.identityLostMarkMapper = identityLostMarkMapper;
        this.unboundBindMemoryMapper = unboundBindMemoryMapper;
        this.curatedDraftEventMapper = curatedDraftEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public boolean hasOpen(String conflictId) {
        return findOpen(conflictId) != null;
    }

    @Transactional(readOnly = true)
    public CuratedDraftResponse getOpen(String conflictId) {
        return respond(requireOpen(conflictId));
    }

    @Transactional(readOnly = true)
    public CuratedDraftResponse getByDraftId(String draftId) {
        CuratedDraft draft = curatedDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("DRAFT_NOT_FOUND",
                    "No 草案: " + draftId);
        }
        return respond(draft);
    }

    @Transactional(readOnly = true)
    public CuratedDraftResponse getById(String conflictId, String draftId) {
        CuratedDraft draft = curatedDraftMapper.selectById(draftId);
        if (draft == null || !conflictId.equals(draft.getConflictId())) {
            throw new BusinessException("DRAFT_NOT_FOUND",
                    "No 草案 " + draftId + " for conflict: " + conflictId);
        }
        return respond(draft);
    }

    @Transactional
    public CuratedDraftResponse createForChangeCurated(
            ConflictCase conflict,
            ConflictDiagnosisResponse diagnosis,
            ConflictDiagnosisResponse.ForkSuggestion fork,
            AuthUserPrincipal actor
    ) {
        if (findOpen(conflict.getId()) != null) {
            throw new BusinessException("DRAFT_ALREADY_OPEN",
                    "Conflict already has an open 草案");
        }
        String fromHostId = conflict.getCuratedTargetId();
        String toHostId = conflict.getObservedTargetId();
        if (fromHostId == null || fromHostId.isBlank() || toHostId == null || toHostId.isBlank()) {
            throw new BusinessException("DRAFT_TARGET_UNAVAILABLE",
                    "改理想草案需要两侧可用的策展宿主与观测宿主");
        }

        Instant now = Instant.now();
        CuratedDraft draft = new CuratedDraft();
        draft.setId("draft-" + UUID.randomUUID());
        draft.setConflictId(conflict.getId());
        draft.setDiagnosisId(diagnosis.id());
        draft.setSelectedForkId(fork.id());
        draft.setOrigin(CuratedDraftOrigin.CHANGE_CURATED);
        draft.setStatus(CuratedDraftStatus.OPEN);
        draft.setCreatedBy(actor.getUserId());
        draft.setCreatedAt(now);
        try {
            curatedDraftMapper.insert(draft);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("DRAFT_ALREADY_OPEN",
                    "Conflict already has an open 草案");
        }

        List<CuratedDraftItem> items = buildRunsOnItems(
                draft.getId(), conflict.getSubjectId(), fromHostId, toHostId, now);
        for (CuratedDraftItem item : items) {
            curatedDraftItemMapper.insert(item);
        }

        conflictEventService.append(conflict.getId(), ConflictEventType.DRAFT_CREATED, actor.getUserId(), Map.of(
                "draftId", draft.getId(),
                "itemCount", items.size(),
                "hint", "草案已创建"
        ));
        return toResponse(draft, items);
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
        return toResponse(draft, items);
    }

    @Transactional(readOnly = true)
    public List<CuratedDraftEventResponse> listEvents(String draftId) {
        CuratedDraft draft = curatedDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("DRAFT_NOT_FOUND", "No 草案: " + draftId);
        }
        return curatedDraftEventMapper.selectList(new LambdaQueryWrapper<CuratedDraftEvent>()
                        .eq(CuratedDraftEvent::getDraftId, draftId)
                        .orderByAsc(CuratedDraftEvent::getCreatedAt))
                .stream()
                .map(this::toEventResponse)
                .toList();
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

    private CuratedDraftEventResponse toEventResponse(CuratedDraftEvent row) {
        return new CuratedDraftEventResponse(
                row.getId(),
                row.getDraftId(),
                row.getEventType(),
                row.getActorUserId(),
                readPayloadMap(row.getDetailJson()),
                row.getCreatedAt()
        );
    }

    /**
     * Ticket 05: 冲突升级/空洞作废该冲突上仍 OPEN 的改理想草案.
     * PENDING items stay PENDING and are never written to 策展.
     */
    @Transactional
    public void voidOpenForConflict(String conflictId, String reason) {
        CuratedDraft open = findOpen(conflictId);
        if (open == null) {
            return;
        }
        int updated = curatedDraftMapper.update(null, new LambdaUpdateWrapper<CuratedDraft>()
                .eq(CuratedDraft::getId, open.getId())
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN)
                .set(CuratedDraft::getStatus, CuratedDraftStatus.VOIDED));
        if (updated != 1) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("draftId", open.getId());
        detail.put("reason", reason);
        detail.put("hint", "草案已作废");
        conflictEventService.append(conflictId, ConflictEventType.DRAFT_VOIDED, null, detail);
    }

    /**
     * 未绑定草案逐条确认：接受即写该条（新建容器），拒绝不写。
     */
    @Transactional
    public CuratedDraftResponse acceptUnboundItem(String draftId, String itemId, AuthUserPrincipal actor) {
        UnboundItemReview review = beginUnboundItemReview(draftId, itemId);
        applyUnboundAccept(review.draft(), review.item(), actor.getUserId());
        markItem(review.item(), CuratedDraftItemStatus.ACCEPTED);
        return respond(review.draft());
    }

    @Transactional
    public CuratedDraftResponse rejectUnboundItem(String draftId, String itemId, AuthUserPrincipal actor) {
        UnboundItemReview review = beginUnboundItemReview(draftId, itemId);
        markItem(review.item(), CuratedDraftItemStatus.REJECTED);
        return respond(review.draft());
    }

    /**
     * Accept writes that item's 策展 运行于 immediately, then runs the same merge-key compare
     * as snapshot ingest (equal → 待确认关闭, never auto CLOSED).
     */
    @Transactional
    public CuratedDraftResponse acceptItem(String conflictId, String itemId, AuthUserPrincipal actor) {
        OpenItemReview review = beginItemReview(conflictId, itemId, actor);
        writeAcceptedRunsOn(review.item());
        markItem(review.item(), CuratedDraftItemStatus.ACCEPTED);
        conflictEventService.append(
                conflictId,
                ConflictEventType.DRAFT_ITEM_ACCEPTED,
                actor.getUserId(),
                itemAuditDetail(review, "草案条目已接受并写入策展", true));
        conflictDetectionService.reconcileMergeKey(review.item().getSubjectId(), CuratedRelationType.RUNS_ON);
        return respond(review.draft());
    }

    @Transactional
    public CuratedDraftResponse rejectItem(String conflictId, String itemId, AuthUserPrincipal actor) {
        OpenItemReview review = beginItemReview(conflictId, itemId, actor);
        markItem(review.item(), CuratedDraftItemStatus.REJECTED);
        conflictEventService.append(
                conflictId,
                ConflictEventType.DRAFT_ITEM_REJECTED,
                actor.getUserId(),
                itemAuditDetail(review, "草案条目已拒绝", false));
        return respond(review.draft());
    }

    private UnboundItemReview beginUnboundItemReview(String draftId, String itemId) {
        CuratedDraft draft = curatedDraftMapper.selectById(draftId);
        if (draft == null
                || draft.getOrigin() != CuratedDraftOrigin.UNBOUND_CANDIDATE
                || draft.getStatus() != CuratedDraftStatus.OPEN) {
            throw new BusinessException("DRAFT_NOT_FOUND", "No open 未绑定草案: " + draftId);
        }
        CuratedDraftItem item = requireItemOnDraft(draft.getId(), itemId);
        return new UnboundItemReview(draft, item);
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
        if (lost == null) {
            throw new BusinessException("UNBOUND_BIND_TARGET_HEALTHY",
                    "只能绑到仍身份失联的对象");
        }
        rememberBind(draft, targetId);
    }

    private void rememberBind(CuratedDraft draft, String curatedObjectId) {
        UnboundBindMemory memory = new UnboundBindMemory();
        memory.setId("ubm-" + UUID.randomUUID());
        memory.setSourceHostId(draft.getSourceHostId());
        memory.setRuntimeId(draft.getRuntimeId());
        memory.setCuratedObjectId(curatedObjectId);
        memory.setCreatedAt(Instant.now());
        unboundBindMemoryMapper.insert(memory);
    }

    private static String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private OpenItemReview beginItemReview(String conflictId, String itemId, AuthUserPrincipal actor) {
        CuratedDraft draft = requireReviewableDraft(conflictId);
        requireAcceptedHandler(requireConflict(conflictId), actor);
        CuratedDraftItem item = requireItemOnDraft(draft.getId(), itemId);
        return new OpenItemReview(draft, item);
    }

    private static void requirePending(CuratedDraftItem item) {
        if (item.getStatus() != CuratedDraftItemStatus.PENDING) {
            throw new BusinessException("DRAFT_ITEM_NOT_PENDING",
                    "草案条目已不是待确认: " + item.getId());
        }
    }

    private void writeAcceptedRunsOn(CuratedDraftItem item) {
        if (item.getKind() != CuratedDraftItemKind.RUNS_ON_TARGET_CHANGE) {
            throw new BusinessException("DRAFT_ITEM_KIND_UNSUPPORTED",
                    "本刀只接受「运行于」目标变更条目");
        }
        curatedTruthService.applyAcceptedDraftRunsOn(item.getSubjectId(), item.getToHostId());
    }

    private void markItem(CuratedDraftItem item, CuratedDraftItemStatus status) {
        requirePending(item);
        item.setStatus(status);
        curatedDraftItemMapper.updateById(item);
    }

    private CuratedDraft requireOpen(String conflictId) {
        CuratedDraft draft = findOpen(conflictId);
        if (draft == null) {
            throw new BusinessException("DRAFT_NOT_FOUND",
                    "No open 草案 for conflict: " + conflictId);
        }
        return draft;
    }

    /**
     * Item review loads the latest 草案 including VOIDED so accept/reject can say
     * 草案已作废 instead of pretending there was never a draft.
     */
    private CuratedDraft requireReviewableDraft(String conflictId) {
        CuratedDraft draft = findLatest(conflictId);
        if (draft == null) {
            throw new BusinessException("DRAFT_NOT_FOUND",
                    "No 草案 for conflict: " + conflictId);
        }
        if (draft.getStatus() == CuratedDraftStatus.VOIDED) {
            throw new BusinessException("DRAFT_VOIDED", "草案已作废");
        }
        if (draft.getStatus() != CuratedDraftStatus.OPEN) {
            throw new BusinessException("DRAFT_NOT_FOUND",
                    "No open 草案 for conflict: " + conflictId);
        }
        return draft;
    }

    private CuratedDraft findLatest(String conflictId) {
        return curatedDraftMapper.selectOne(new LambdaQueryWrapper<CuratedDraft>()
                .eq(CuratedDraft::getConflictId, conflictId)
                .orderByDesc(CuratedDraft::getCreatedAt)
                .last("LIMIT 1"));
    }

    private ConflictCase requireConflict(String conflictId) {
        ConflictCase conflict = conflictCaseMapper.selectById(conflictId);
        if (conflict == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + conflictId);
        }
        return conflict;
    }

    private static void requireAcceptedHandler(ConflictCase conflict, AuthUserPrincipal actor) {
        boolean ok = conflict.getHandlerAcceptance() == HandlerAcceptance.ACCEPTED
                && actor.getUserId().equals(conflict.getHandlerUserId());
        if (!ok) {
            throw new BusinessException("PLAN_REQUIRES_ACCEPTED_HANDLER",
                    "Only the 已接受冲突处理人 may accept or reject 草案 items");
        }
    }

    private CuratedDraftItem requireItemOnDraft(String draftId, String itemId) {
        CuratedDraftItem item = curatedDraftItemMapper.selectById(itemId);
        if (item == null || !draftId.equals(item.getDraftId())) {
            throw new BusinessException("DRAFT_ITEM_NOT_FOUND",
                    "No 草案 item " + itemId + " on the open draft");
        }
        return item;
    }

    private List<CuratedDraftItem> buildRunsOnItems(
            String draftId,
            String mergeKeySubjectId,
            String fromHostId,
            String toHostId,
            Instant now
    ) {
        List<CuratedFact> facts = new ArrayList<>(curatedFactMapper.selectList(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getRelationType, CuratedRelationType.RUNS_ON)
                .eq(CuratedFact::getTargetId, fromHostId)));
        facts.sort(Comparator
                .comparing((CuratedFact fact) -> !mergeKeySubjectId.equals(fact.getSubjectId()))
                .thenComparing(CuratedFact::getSubjectId));

        String payload = writePayload(fromHostId, toHostId);
        List<CuratedDraftItem> items = new ArrayList<>();
        int seq = 1;
        for (CuratedFact fact : facts) {
            CuratedDraftItem item = new CuratedDraftItem();
            item.setId("ditem-" + UUID.randomUUID());
            item.setDraftId(draftId);
            item.setSeq(seq++);
            item.setKind(CuratedDraftItemKind.RUNS_ON_TARGET_CHANGE);
            item.setStatus(CuratedDraftItemStatus.PENDING);
            item.setSubjectId(fact.getSubjectId());
            item.setFromHostId(fromHostId);
            item.setToHostId(toHostId);
            item.setPayloadJson(payload);
            item.setCreatedAt(now);
            items.add(item);
        }
        if (items.size() < 2) {
            throw new BusinessException("DRAFT_ITEMS_INCOMPLETE",
                    "改理想草案规则夹具需要至少两条可独立确认的「运行于」条目");
        }
        boolean hasMergeKey = items.stream().anyMatch(item -> mergeKeySubjectId.equals(item.getSubjectId()));
        if (!hasMergeKey) {
            throw new BusinessException("DRAFT_MERGE_KEY_ITEM_MISSING",
                    "改理想草案必须包含合并键容器的「运行于」条目");
        }
        return items;
    }

    private CuratedDraft findOpen(String conflictId) {
        return curatedDraftMapper.selectOne(new LambdaQueryWrapper<CuratedDraft>()
                .eq(CuratedDraft::getConflictId, conflictId)
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN)
                .orderByDesc(CuratedDraft::getCreatedAt)
                .last("LIMIT 1"));
    }

    private List<CuratedDraftItem> loadItems(String draftId) {
        return curatedDraftItemMapper.selectList(new LambdaQueryWrapper<CuratedDraftItem>()
                .eq(CuratedDraftItem::getDraftId, draftId)
                .orderByAsc(CuratedDraftItem::getSeq));
    }

    private CuratedDraftResponse respond(CuratedDraft draft) {
        return toResponse(draft, loadItems(draft.getId()));
    }

    private CuratedDraftResponse toResponse(CuratedDraft draft, List<CuratedDraftItem> items) {
        ConflictCase conflict = draft.getConflictId() == null
                ? null
                : conflictCaseMapper.selectById(draft.getConflictId());
        String mergeKeySubjectId = conflict == null ? null : conflict.getSubjectId();
        CuratedDraftOrigin origin = draft.getOrigin() == null
                ? CuratedDraftOrigin.CHANGE_CURATED
                : draft.getOrigin();
        return new CuratedDraftResponse(
                draft.getId(),
                draft.getConflictId(),
                draft.getDiagnosisId(),
                draft.getSelectedForkId(),
                origin,
                draft.getCandidateId(),
                draft.getSourceHostId(),
                draft.getRuntimeId(),
                draft.getStatus(),
                items.stream().map(item -> toItem(item, mergeKeySubjectId)).toList(),
                draft.getCreatedBy(),
                draft.getCreatedAt()
        );
    }

    private CuratedDraftResponse.Item toItem(CuratedDraftItem item, String mergeKeySubjectId) {
        CuratedObject subject = item.getSubjectId() == null
                ? null
                : curatedObjectMapper.selectById(item.getSubjectId());
        CuratedObject fromHost = item.getFromHostId() == null
                ? null
                : curatedObjectMapper.selectById(item.getFromHostId());
        CuratedObject toHost = item.getToHostId() == null
                ? null
                : curatedObjectMapper.selectById(item.getToHostId());
        return new CuratedDraftResponse.Item(
                item.getId(),
                item.getSeq() == null ? 0 : item.getSeq(),
                item.getKind(),
                item.getStatus(),
                item.getSubjectId(),
                subject == null ? null : subject.getName(),
                item.getFromHostId(),
                fromHost == null ? null : fromHost.getName(),
                item.getToHostId(),
                toHost == null ? null : toHost.getName(),
                mergeKeySubjectId != null && mergeKeySubjectId.equals(item.getSubjectId()),
                readPayloadMap(item.getPayloadJson())
        );
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

    /**
     * Rule fixture for MISSING_LABEL: pick one identity-lost object on the candidate host.
     * Deterministic when several marks exist (curatedObjectId asc); ticket 03 owns bind conflicts.
     */
    private IdentityLostMark findIdentityLostOnHost(String hostId) {
        return identityLostMarkMapper.selectOne(new LambdaQueryWrapper<IdentityLostMark>()
                .eq(IdentityLostMark::getSourceHostId, hostId)
                .orderByAsc(IdentityLostMark::getCuratedObjectId)
                .last("LIMIT 1"));
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

    private Map<String, Object> readPayloadMap(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String writePayload(String fromHostId, String toHostId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "relationType", CuratedRelationType.RUNS_ON.name(),
                    "fromHostId", fromHostId,
                    "toHostId", toHostId
            ));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static Map<String, Object> itemAuditDetail(OpenItemReview review, String hint, boolean written) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("draftId", review.draft().getId());
        detail.put("itemId", review.item().getId());
        detail.put("subjectId", review.item().getSubjectId());
        if (written) {
            detail.put("written", true);
        }
        detail.put("hint", hint);
        return detail;
    }

    private record OpenItemReview(CuratedDraft draft, CuratedDraftItem item) {
    }

    private record UnboundItemReview(CuratedDraft draft, CuratedDraftItem item) {
    }
}
