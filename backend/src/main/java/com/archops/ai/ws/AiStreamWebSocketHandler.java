package com.archops.ai.ws;

import com.archops.ai.dto.UiContext;
import com.archops.ai.service.AiAgentService;
import com.archops.ai.service.ConversationService;
import com.archops.common.config.WebSocketEndpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Streaming AI chat over WebSocket.
 * Client sends: {"type":"chat","conversationId":1,"message":"check disk usage"}
 * Server emits events: token, tool_start, tool_result, approval_required, resume_start, done
 */
@Component
@WebSocketEndpoint("/ws/ai")
public class AiStreamWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AiStreamWebSocketHandler.class);

    private final AiAgentService aiAgentService;
    private final ConversationService conversationService;
    private final AiStreamSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public AiStreamWebSocketHandler(
            AiAgentService aiAgentService,
            ConversationService conversationService,
            AiStreamSessionRegistry sessionRegistry,
            ObjectMapper objectMapper) {
        this.aiAgentService = aiAgentService;
        this.conversationService = conversationService;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.register(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!hasOperatorAccess(session)) {
            session.sendMessage(new TextMessage(
                    "{\"type\":\"error\",\"content\":\"AI chat requires ADMIN or OPERATOR role\"}"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        if (!"chat".equals(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"content\":\"unknown type\"}"));
            return;
        }

        String userMessage = root.path("message").asText();
        Long conversationId = root.hasNonNull("conversationId") ? root.get("conversationId").asLong() : null;
        if (conversationId == null) {
            conversationId = conversationService.create(userId, userMessage).id();
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(Map.of("type", "conversation", "conversationId", conversationId))));
        }

        Long providerId = root.hasNonNull("providerId") ? root.get("providerId").asLong() : null;
        UiContext uiContext = parseUiContext(root.get("uiContext"));
        Long finalConversationId = conversationId;
        aiAgentService.chat(userId, finalConversationId, userMessage, providerId, uiContext, event -> {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to send AI stream event", ex);
            }
        });
    }

    private static boolean hasOperatorAccess(WebSocketSession session) {
        Object rolesAttr = session.getAttributes().get("roles");
        if (rolesAttr instanceof java.util.Collection<?> roles) {
            for (Object role : roles) {
                if (role == null) {
                    continue;
                }
                String name = String.valueOf(role).trim().toUpperCase();
                if (name.startsWith("ROLE_")) {
                    name = name.substring(5);
                }
                if ("ADMIN".equals(name) || "OPERATOR".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private UiContext parseUiContext(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            String route = textOrNull(node, "route");
            String surface = textOrNull(node, "surface");
            Long selectedAssetId = node.hasNonNull("selectedAssetId") ? node.get("selectedAssetId").asLong() : null;
            List<Long> selectedAssetIds = List.of();
            if (node.has("selectedAssetIds") && node.get("selectedAssetIds").isArray()) {
                selectedAssetIds = objectMapper.convertValue(node.get("selectedAssetIds"), new TypeReference<>() {});
            }
            return new UiContext(route, surface, selectedAssetId, selectedAssetIds);
        } catch (Exception ex) {
            log.debug("Ignoring invalid uiContext: {}", ex.getMessage());
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
