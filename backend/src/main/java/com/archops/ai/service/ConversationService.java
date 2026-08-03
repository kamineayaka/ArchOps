package com.archops.ai.service;

import com.archops.ai.domain.AiConversation;
import com.archops.ai.domain.AiMessage;
import com.archops.ai.dto.ChatMessageResponse;
import com.archops.ai.dto.ConversationResponse;
import com.archops.ai.repository.AiConversationRepository;
import com.archops.ai.repository.AiMessageRepository;
import com.archops.asset.service.AssetService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.terminal.pool.SshConnectionPool;
import java.util.ArrayList;
import java.util.Collection;
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
    private final AssetAclService assetAclService;
    private final SshConnectionPool sshConnectionPool;

    public ConversationService(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AssetService assetService,
            AssetAclService assetAclService,
            SshConnectionPool sshConnectionPool) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.assetService = assetService;
        this.assetAclService = assetAclService;
        this.sshConnectionPool = sshConnectionPool;
    }

    @Transactional
    public ConversationResponse create(Long userId, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "新对话");
        return toResponse(conversationRepository.save(conversation), userId, List.of());
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(Long userId, Collection<String> roles) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(conversation -> toResponse(conversation, userId, roles))
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
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "对话不存在"));
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setToolCalls(toolCallsJson != null ? toolCallsJson : "[]");
        AiMessage saved = messageRepository.save(message);
        // Touch conversation so list ordering by updatedAt stays fresh.
        conversation.touch();
        conversationRepository.save(conversation);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> new ChatMessageResponse(
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt(),
                        m.getToolCalls() != null ? m.getToolCalls() : "[]"))
                .toList();
    }

    @Transactional
    public ConversationResponse updateTargets(
            Long conversationId,
            Long userId,
            Collection<String> roles,
            List<Long> targetAssetIds) {
        AiConversation conversation = requireOwned(conversationId, userId);
        List<Long> assets = normalizeIds(targetAssetIds);
        for (Long assetId : assets) {
            // Reject the entire update rather than silently dropping explicitly requested targets.
            assetService.get(assetId, userId, roles);
        }

        conversation.setTargetAssetIds(assets);
        conversation.setTargetGroupIds(List.of());
        ConversationResponse response =
                toResponse(conversationRepository.save(conversation), userId, roles);
        for (Long assetId : response.resolvedAssetIds()) {
            try {
                sshConnectionPool.warm(userId, roles, assetId);
            } catch (Exception ignored) {
                // Warm is best-effort; chat will retry on first tool call.
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ConversationResponse getTargets(
            Long conversationId, Long userId, Collection<String> roles) {
        return toResponse(requireOwned(conversationId, userId), userId, roles);
    }

    @Transactional(readOnly = true)
    public List<Long> resolveEffectiveTargetAssetIds(
            AiConversation conversation, Long userId, Collection<String> roles) {
        Set<Long> resolved = new LinkedHashSet<>();
        if (conversation.getTargetAssetIds() != null) {
            resolved.addAll(conversation.getTargetAssetIds());
        }
        return assetAclService.filterAssetIds(userId, roles, resolved);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private ConversationResponse toResponse(
            AiConversation conversation, Long userId, Collection<String> roles) {
        List<Long> resolved = resolveEffectiveTargetAssetIds(conversation, userId, roles);
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getTargetAssetIds() != null ? conversation.getTargetAssetIds() : List.of(),
                resolved,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
