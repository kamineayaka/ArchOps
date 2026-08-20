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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule-templated 改理想 草案 (ticket 03) and itemized accept/reject (ticket 04).
 * Confirmation-before-write is not 策展真相; accept writes immediately then compares.
 */
@Service
public class CuratedDraftService {

    private final CuratedDraftMapper curatedDraftMapper;
    private final CuratedDraftItemMapper curatedDraftItemMapper;
    private final CuratedFactMapper curatedFactMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final CuratedTruthService curatedTruthService;
    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictEventService conflictEventService;
    private final ConflictDetectionService conflictDetectionService;
    private final ObjectMapper objectMapper;

    public CuratedDraftService(
            CuratedDraftMapper curatedDraftMapper,
            CuratedDraftItemMapper curatedDraftItemMapper,
            CuratedFactMapper curatedFactMapper,
            CuratedObjectMapper curatedObjectMapper,
            CuratedTruthService curatedTruthService,
            ConflictCaseMapper conflictCaseMapper,
            ConflictEventService conflictEventService,
            ConflictDetectionService conflictDetectionService,
            ObjectMapper objectMapper
    ) {
        this.curatedDraftMapper = curatedDraftMapper;
        this.curatedDraftItemMapper = curatedDraftItemMapper;
        this.curatedFactMapper = curatedFactMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.curatedTruthService = curatedTruthService;
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictEventService = conflictEventService;
        this.conflictDetectionService = conflictDetectionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public boolean hasOpen(String conflictId) {
        return findOpen(conflictId) != null;
    }

    @Transactional(readOnly = true)
    public CuratedDraftResponse getOpen(String conflictId) {
        CuratedDraft draft = findOpen(conflictId);
        if (draft == null) {
            throw new BusinessException("DRAFT_NOT_FOUND",
                    "No open 草案 for conflict: " + conflictId);
        }
        return toResponse(draft, loadItems(draft.getId()));
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

    @Transactional
    public CuratedDraftResponse acceptItem(String draftId, String itemId, AuthUserPrincipal actor) {
        ReviewContext ctx = beginReview(draftId, itemId, actor);
        markItem(ctx.item().getId(), CuratedDraftItemStatus.ACCEPTED);
        curatedTruthService.applyAcceptedRunsOnTarget(ctx.item().getSubjectId(), ctx.item().getToHostId());
        conflictEventService.append(ctx.draft().getConflictId(), ConflictEventType.ITEM_ACCEPTED, actor.getUserId(),
                Map.of(
                        "draftId", ctx.draft().getId(),
                        "itemId", ctx.item().getId(),
                        "subjectId", ctx.item().getSubjectId(),
                        "toHostId", ctx.item().getToHostId(),
                        "wroteCurated", true,
                        "hint", "条目已接受（含写入）"
                ));
        conflictDetectionService.reconcileAfterCuratedWrite(
                ctx.item().getSubjectId(), CuratedRelationType.RUNS_ON);
        return toResponse(ctx.draft(), loadItems(ctx.draft().getId()));
    }

    @Transactional
    public CuratedDraftResponse rejectItem(String draftId, String itemId, AuthUserPrincipal actor) {
        ReviewContext ctx = beginReview(draftId, itemId, actor);
        markItem(ctx.item().getId(), CuratedDraftItemStatus.REJECTED);
        conflictEventService.append(ctx.draft().getConflictId(), ConflictEventType.ITEM_REJECTED, actor.getUserId(),
                Map.of(
                        "draftId", ctx.draft().getId(),
                        "itemId", ctx.item().getId(),
                        "subjectId", ctx.item().getSubjectId(),
                        "wroteCurated", false,
                        "hint", "条目已拒绝"
                ));
        return toResponse(ctx.draft(), loadItems(ctx.draft().getId()));
    }

    private ReviewContext beginReview(String draftId, String itemId, AuthUserPrincipal actor) {
        CuratedDraft draft = curatedDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException("DRAFT_NOT_FOUND", "草案不存在: " + draftId);
        }
        if (draft.getStatus() != CuratedDraftStatus.OPEN) {
            throw new BusinessException("DRAFT_NOT_OPEN", "只能审开放草案的条目");
        }
        ConflictCase conflict = conflictCaseMapper.selectById(draft.getConflictId());
        if (conflict == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + draft.getConflictId());
        }
        requireAcceptedHandler(conflict, actor);
        CuratedDraftItem item = curatedDraftItemMapper.selectById(itemId);
        if (item == null || !draft.getId().equals(item.getDraftId())) {
            throw new BusinessException("DRAFT_ITEM_NOT_FOUND", "草案条目不存在: " + itemId);
        }
        if (item.getStatus() != CuratedDraftItemStatus.PENDING) {
            throw new BusinessException("DRAFT_ITEM_NOT_PENDING", "只能审待确认条目");
        }
        return new ReviewContext(draft, item);
    }

    private void markItem(String itemId, CuratedDraftItemStatus next) {
        Integer rows = curatedDraftItemMapper.update(null, new LambdaUpdateWrapper<CuratedDraftItem>()
                .eq(CuratedDraftItem::getId, itemId)
                .eq(CuratedDraftItem::getStatus, CuratedDraftItemStatus.PENDING)
                .set(CuratedDraftItem::getStatus, next));
        if (rows == null || rows != 1) {
            throw new BusinessException("DRAFT_ITEM_NOT_PENDING", "只能审待确认条目");
        }
    }

    private static void requireAcceptedHandler(ConflictCase conflict, AuthUserPrincipal actor) {
        boolean ok = conflict.getHandlerAcceptance() == HandlerAcceptance.ACCEPTED
                && actor.getUserId().equals(conflict.getHandlerUserId());
        if (!ok) {
            throw new BusinessException("PLAN_REQUIRES_ACCEPTED_HANDLER",
                    "Only the 已接受冲突处理人 may accept or reject 草案条目");
        }
    }

    private record ReviewContext(CuratedDraft draft, CuratedDraftItem item) {
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
}
