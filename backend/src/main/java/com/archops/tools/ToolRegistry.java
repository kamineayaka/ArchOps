package com.archops.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Registry of in-process agent tools. Tools self-register as Spring beans;
 * the registry indexes them by name for dispatch.
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> registered) {
        for (AgentTool tool : registered) {
            AgentTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate agent tool name '" + tool.name() + "': "
                                + previous.getClass().getName()
                                + " and "
                                + tool.getClass().getName());
            }
        }
    }

    public List<AgentTool> all() {
        return List.copyOf(tools.values());
    }

    public List<com.archops.ai.llm.LlmProvider.ToolDefinition> definitions() {
        return tools.values().stream()
                .map(t -> new com.archops.ai.llm.LlmProvider.ToolDefinition(t.name(), t.description(), t.parametersJson()))
                .toList();
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
