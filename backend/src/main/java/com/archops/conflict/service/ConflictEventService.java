package com.archops.conflict.service;

import com.archops.common.json.PersistentJson;
import com.archops.conflict.domain.ConflictCaseEvent;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.dto.ConflictEventResponse;
import com.archops.conflict.mapper.ConflictCaseEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only conflict lifecycle audit (ticket 09).
 */
@Service
public class ConflictEventService {

    private static final TypeReference<LinkedHashMap<String, Object>> DETAIL = new TypeReference<>() {
    };

    private final ConflictCaseEventMapper eventMapper;
    private final PersistentJson persistentJson;

    public ConflictEventService(ConflictCaseEventMapper eventMapper, PersistentJson persistentJson) {
        this.eventMapper = eventMapper;
        this.persistentJson = persistentJson;
    }

    @Transactional
    public void append(String conflictId, ConflictEventType type, String actorUserId, Map<String, Object> detail) {
        ConflictCaseEvent event = new ConflictCaseEvent();
        event.setId("cevt-" + UUID.randomUUID());
        event.setConflictId(conflictId);
        event.setEventType(type);
        event.setActorUserId(actorUserId);
        event.setDetailJson(persistentJson.write(detail == null ? Map.of() : detail));
        event.setCreatedAt(Instant.now());
        eventMapper.insert(event);
    }

    @Transactional(readOnly = true)
    public List<ConflictEventResponse> listForConflict(String conflictId) {
        return eventMapper.selectList(new LambdaQueryWrapper<ConflictCaseEvent>()
                        .eq(ConflictCaseEvent::getConflictId, conflictId)
                        .orderByAsc(ConflictCaseEvent::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ConflictEventResponse toResponse(ConflictCaseEvent row) {
        return new ConflictEventResponse(
                row.getId(),
                row.getConflictId(),
                row.getEventType(),
                row.getActorUserId(),
                persistentJson.read(row.getDetailJson(), DETAIL, new LinkedHashMap<>()),
                row.getCreatedAt()
        );
    }
}
