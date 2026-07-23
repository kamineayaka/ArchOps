package com.archops.knowledge.architecture.service;

import com.archops.knowledge.architecture.domain.ToolExecutionEvent;
import com.archops.knowledge.architecture.repository.ToolExecutionEventRepository;
import com.archops.knowledge.classifier.ChangeLevel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolExecutionEventService {

    private static final int SUMMARY_MAX = 4000;

    private final ToolExecutionEventRepository repository;

    public ToolExecutionEventService(ToolExecutionEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ToolExecutionEvent record(
            Long conversationId,
            Long userId,
            String toolName,
            String argumentsJson,
            String stdoutSummary,
            String stderrSummary,
            Integer exitCode,
            List<Long> assetIds,
            ChangeLevel level) {
        ToolExecutionEvent event = new ToolExecutionEvent();
        event.setConversationId(conversationId);
        event.setUserId(userId);
        event.setToolName(toolName != null ? toolName : "unknown");
        event.setArgumentsJson(argumentsJson != null ? argumentsJson : "{}");
        event.setStdoutSummary(truncate(stdoutSummary));
        event.setStderrSummary(truncate(stderrSummary));
        event.setExitCode(exitCode);
        event.setAssetIds(assetIds != null ? new ArrayList<>(assetIds) : new ArrayList<>());
        event.setLevel(level != null ? level.name() : null);
        return repository.save(event);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= SUMMARY_MAX) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX) + "…";
    }
}
