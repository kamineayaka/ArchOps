---
name: add-agent-tool
description: >-
  Adds an in-process ArchOps AgentTool (Spring @Component implementing
  AgentTool, auto-registered by ToolRegistry). Use when the user asks to add
  an agent tool, LLM tool, ReAct tool, ssh_exec-like capability, or extend the
  AI tool registry — not MCP.
---

# 加 AgentTool（ArchOps）

进程内工具，**不是 MCP**。遵守 `.cursor/rules/backend-java.mdc` 中 Agent 工具一节。

## 流程清单

```
- [ ] 1. 新建 com.archops.tools.tool.<Name>Tool implements AgentTool
- [ ] 2. @Component + 构造器注入已有 Service（禁止平行造客户端）
- [ ] 3. name()：snake_case，全局唯一
- [ ] 4. description()：写清何时用、参数约定、目标资产行为
- [ ] 5. parametersJson()：合法 JSON Schema 字符串
- [ ] 6. execute()：读 arguments + ExecutionContext；尊重 targetAssetIds
- [ ] 7. 资产范围：ToolScope.assertInScope / allowedSet
- [ ] 8. 风险：破坏性命令不得绕过审批（见 ToolExecutorService 链路）
- [ ] 9. 确认无需改 ToolRegistry（List<AgentTool> 自动注入）
```

## 样板

| 类型 | 文件 |
|---|---|
| 契约 | `tools/AgentTool.java` |
| 只读列表 | `tools/tool/ListAssetsTool.java` |
| SSH 执行 | `tools/tool/SshExecTool.java` |
| 架构提案 | `tools/tool/ProposeArchitectureUpdateTool.java` |
| 范围助手 | `tools/ToolScope.java` |
| 调度/审批 | `ai/service/ToolExecutorService.java` |

## 实现要点

```java
@Component
public class FooTool implements AgentTool {
    @Override public String name() { return "foo_bar"; }
    @Override public String description() { /* 给模型看的英文说明 */ }
    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{...},\"required\":[...]}";
    }
    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) { ... }
}
```

- `ExecutionContext`：`userId` / `username` / `conversationId` / `targetAssetIds` / `providerId`
- 有对话目标时：过滤或拒绝范围外 `assetId`（对齐 `SshExecTool` / `ListAssetsTool`）
- SSH：只用 `SshConnectionPool`，禁止新 MINA 直连
- 返回值是喂回模型的字符串，保持简洁可读

## 风险与审批

工具执行经 `RiskClassifier` → `ApprovalGate` → 可选 `ExecutionGrant`。  
新增高风险能力时：扩展/对齐 `RiskClassifier` 分类逻辑，**禁止**在 tool 内直接执行已判定需审批的动作。

## 验证

- 启动后端后确认工具出现在 Agent 可用工具列表（`ToolRegistry.definitions()`）
- 有目标资产 / 无目标资产两种路径各测一次
- 上机验证走 aliserver（见 `remote-aliserver` 规则）
