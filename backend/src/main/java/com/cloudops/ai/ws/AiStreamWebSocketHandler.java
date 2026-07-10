package com.cloudops.ai.ws;

import com.cloudops.ai.service.AiAgentService;
import com.cloudops.ai.service.ConversationService;
import com.cloudops.common.config.WebSocketEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Server emits events: token, tool_start, tool_result, approval_required, done
 */
@Component
@WebSocketEndpoint("/ws/ai")
public class AiStreamWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AiStreamWebSocketHandler.class);

    private final AiAgentService aiAgentService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public AiStreamWebSocketHandler(
            AiAgentService aiAgentService,
            ConversationService conversationService,
            ObjectMapper objectMapper) {
        this.aiAgentService = aiAgentService;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
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
        Long finalConversationId = conversationId;
        aiAgentService.chat(userId, finalConversationId, userMessage, providerId, event -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
                }
            } catch (Exception ex) {
                log.warn("Failed to send AI stream event", ex);
            }
        });
    }
}
