package com.cloudops.ai.controller;

import com.cloudops.ai.dto.ChatMessageResponse;
import com.cloudops.ai.dto.ChatRequest;
import com.cloudops.ai.dto.ConversationResponse;
import com.cloudops.ai.dto.ConversationTargetsRequest;
import com.cloudops.ai.service.AiAgentService;
import com.cloudops.ai.service.ConversationService;
import com.cloudops.common.dto.ApiResponse;
import com.cloudops.common.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
public class AiController {

    private final ConversationService conversationService;
    private final AiAgentService aiAgentService;

    public AiController(ConversationService conversationService, AiAgentService aiAgentService) {
        this.conversationService = conversationService;
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationResponse> create(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(conversationService.create(principal.getUserId(), null));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationResponse>> list(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(conversationService.list(principal.getUserId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<ChatMessageResponse>> messages(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        conversationService.requireOwned(id, principal.getUserId());
        return ApiResponse.ok(conversationService.history(id));
    }

    @GetMapping("/conversations/{id}/targets")
    public ApiResponse<List<Long>> targets(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(conversationService.targetAssetIds(id, principal.getUserId()));
    }

    @PutMapping("/conversations/{id}/targets")
    public ApiResponse<ConversationResponse> updateTargets(
            @PathVariable Long id,
            @RequestBody ConversationTargetsRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(conversationService.updateTargets(
                id, principal.getUserId(), request.targetAssetIds()));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        Long conversationId = request.conversationId();
        if (conversationId == null) {
            conversationId = conversationService.create(principal.getUserId(), truncate(request.message())).id();
        }
        AiAgentService.AgentResult result = aiAgentService.chat(
                principal.getUserId(), conversationId, request.message(), request.providerId(), null);
        return ApiResponse.ok(Map.of(
                "conversationId", conversationId,
                "answer", result.answer(),
                "tools", result.tools()));
    }

    private String truncate(String message) {
        return message.length() > 40 ? message.substring(0, 40) + "..." : message;
    }
}
