package com.archops.ai.service;

import com.archops.ai.domain.AiConversation;
import com.archops.ai.domain.AiMessage;
import com.archops.ai.dto.ChatMessageResponse;
import com.archops.ai.dto.ConversationResponse;
import com.archops.ai.repository.AiConversationRepository;
import com.archops.ai.repository.AiMessageRepository;
import com.archops.asset.service.AssetGroupService;
import com.archops.asset.service.AssetService;
import com.archops.common.exception.BusinessException;
import com.archops.terminal.pool.SshConnectionPool;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AssetService assetService;
    private final AssetGroupService assetGroupService;
    private final SshConnectionPool sshConnectionPool;

    public ConversationService(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AssetService assetService,
            AssetGroupService assetGroupService,
            SshConnectionPool sshConnectionPool) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.assetService = assetService;
        this.assetGroupService = assetGroupService;
        this.sshConnectionPool = sshConnectionPool;
    }

    @Transactional
    public ConversationResponse create(Long userId, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "新对话");
        return toResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiConversation requireOwned(Long conversationId, Long userId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "对话不存在"));
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "CONVERSATION_FORBIDDEN", "无权访问该对话");
        }
        return conversation;
    }

    @Transactional
    public AiMessage appendMessage(Long conversationId, String role, String content, String toolCallsJson) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setToolCalls(toolCallsJson != null ? toolCallsJson : "[]");
        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> new ChatMessageResponse(m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ConversationResponse updateTargets(
            Long conversationId,
            Long userId,
            List<Long> targetAssetIds,
            List<Long> targetGroupIds) {
        AiConversation conversation = requireOwned(conversationId, userId);
        List<Long> assets = normalizeIds(targetAssetIds);
        List<Long> groups = normalizeIds(targetGroupIds);
        for (Long assetId : assets) {
            assetService.get(assetId);
        }
        // Validates groups exist and loads members.
        assetGroupService.resolveMemberAssetIds(groups);

        conversation.setTargetAssetIds(assets);
        conversation.setTargetGroupIds(groups);
        ConversationResponse response = toResponse(conversationRepository.save(conversation));
        for (Long assetId : response.resolvedAssetIds()) {
            try {
                sshConnectionPool.warm(userId, assetId);
            } catch (Exception ignored) {
                // Warm is best-effort; chat will retry on first tool call.
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ConversationResponse getTargets(Long conversationId, Long userId) {
        return toResponse(requireOwned(conversationId, userId));
    }

    @Transactional(readOnly = true)
    public List<Long> resolveEffectiveTargetAssetIds(AiConversation conversation) {
        Set<Long> resolved = new LinkedHashSet<>();
        if (conversation.getTargetAssetIds() != null) {
            resolved.addAll(conversation.getTargetAssetIds());
        }
        if (conversation.getTargetGroupIds() != null && !conversation.getTargetGroupIds().isEmpty()) {
            resolved.addAll(assetGroupService.resolveMemberAssetIds(conversation.getTargetGroupIds()));
        }
        return new ArrayList<>(resolved);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private ConversationResponse toResponse(AiConversation conversation) {
        List<Long> resolved = resolveEffectiveTargetAssetIds(conversation);
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getTargetAssetIds() != null ? conversation.getTargetAssetIds() : List.of(),
                conversation.getTargetGroupIds() != null ? conversation.getTargetGroupIds() : List.of(),
                resolved,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
