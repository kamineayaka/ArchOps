package com.cloudops.ai.runtime;

import com.cloudops.ai.llm.LlmProvider.ChatMessage;
import com.cloudops.ai.llm.LlmProvider.CompletionResult;
import com.cloudops.ai.llm.LlmProvider.ToolDefinition;
import java.util.List;
import java.util.function.Consumer;

public interface LlmRuntime {

    CompletionResult complete(List<ChatMessage> messages, List<ToolDefinition> tools);

    void streamComplete(List<ChatMessage> messages, List<ToolDefinition> tools, Consumer<String> onToken);
}
