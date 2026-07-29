package com.archops.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.tools.AgentTool.ExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    @Test
    void indexesToolsByName() {
        AgentTool list = stub("list_assets");
        AgentTool ssh = stub("ssh_exec");
        ToolRegistry registry = new ToolRegistry(List.of(list, ssh));

        assertEquals(2, registry.all().size());
        assertTrue(registry.find("list_assets").isPresent());
        assertTrue(registry.find("ssh_exec").isPresent());
        assertTrue(registry.find("missing").isEmpty());
        assertEquals(2, registry.definitions().size());
        assertEquals("list_assets", registry.definitions().get(0).name());
    }

    private static AgentTool stub(String name) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name + " stub";
            }

            @Override
            public String parametersJson() {
                return "{\"type\":\"object\",\"properties\":{}}";
            }

            @Override
            public String execute(Map<String, Object> arguments, ExecutionContext context) {
                return "{}";
            }
        };
    }
}
