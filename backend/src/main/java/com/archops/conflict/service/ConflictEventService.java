package com.archops.conflict.service;

import com.archops.conflict.domain.ConflictCaseEvent;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.dto.ConflictEventResponse;
import com.archops.conflict.mapper.ConflictCaseEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ConflictCaseEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    public ConflictEventService(ConflictCaseEventMapper eventMapper, ObjectMapper objectMapper) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void append(String conflictId, ConflictEventType type, String actorUserId, Map<String, Object> detail) {
        ConflictCaseEvent event = new ConflictCaseEvent();
        event.setId("cevt-" + UUID.randomUUID());
        event.setConflictId(conflictId);
        event.setEventType(type);
        event.setActorUserId(actorUserId);
        event.setDetailJson(writeDetail(detail == null ? Map.of() : detail));
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
                readDetail(row.getDetailJson()),
                row.getCreatedAt()
        );
    }

    private String writeDetail(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, Object> readDetail(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
