package com.archops.ai.ws;

import com.archops.ai.service.AiAgentService.AgentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Tracks open AI WebSocket sessions per user so approval resume events can be
 * pushed to the requester's browser without a manual refresh.
 */
@Component
public class AiStreamSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiStreamSessionRegistry.class);

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public AiStreamSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
            }
        }
    }

    public void sendToUser(Long userId, AgentEvent event) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            for (WebSocketSession session : Set.copyOf(sessions)) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(payload));
                    }
                } else {
                    unregister(userId, session);
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to broadcast AI event to user {}", userId, ex);
        }
    }
}
