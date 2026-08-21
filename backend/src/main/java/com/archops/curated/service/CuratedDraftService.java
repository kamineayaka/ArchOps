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
import com.archops.curated.dto.CuratedDraftResponse;
import com.archops.curated.mapper.CuratedDraftItemMapper;
import com.archops.curated.mapper.CuratedDraftMapper;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.mapper.CuratedObjectMapper;
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

    /**
     * Accept writes that item's 策展 运行于 immediately, then runs the same merge-key compare
     * as snapshot ingest (equal → 待确认关闭, never auto CLOSED).
     */
    /**
     * Ticket 05: 冲突升级/空洞作废该冲突上仍 OPEN 的改理想草案.
     * PENDING items stay PENDING and are never written to 策展.
     */
    @Transactional
    public void voidOpenForConflict(String conflictId) {
        curatedDraftMapper.update(null, new LambdaUpdateWrapper<CuratedDraft>()
                .eq(CuratedDraft::getConflictId, conflictId)
                .eq(CuratedDraft::getStatus, CuratedDraftStatus.OPEN)
                .set(CuratedDraft::getStatus, CuratedDraftStatus.VOIDED));
    }

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

    private OpenItemReview beginItemReview(String conflictId, String itemId, AuthUserPrincipal actor) {
        CuratedDraft draft = requireOpen(conflictId);
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
        ConflictCase conflict = conflictCaseMapper.selectById(draft.getConflictId());
        String mergeKeySubjectId = conflict == null ? null : conflict.getSubjectId();
        return new CuratedDraftResponse(
                draft.getId(),
                draft.getConflictId(),
                draft.getDiagnosisId(),
                draft.getSelectedForkId(),
                draft.getStatus(),
                items.stream().map(item -> toItem(item, mergeKeySubjectId)).toList(),
                draft.getCreatedBy(),
                draft.getCreatedAt()
        );
    }

    private CuratedDraftResponse.Item toItem(CuratedDraftItem item, String mergeKeySubjectId) {
        CuratedObject subject = curatedObjectMapper.selectById(item.getSubjectId());
        CuratedObject fromHost = curatedObjectMapper.selectById(item.getFromHostId());
        CuratedObject toHost = curatedObjectMapper.selectById(item.getToHostId());
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
                mergeKeySubjectId != null && mergeKeySubjectId.equals(item.getSubjectId())
        );
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
}
