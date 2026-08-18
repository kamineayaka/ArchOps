---
name: add-agent-tool
description: >-
  Extends the ArchOps host Agent (Python heartbeat/snapshot) or control-plane
  ingest API. Use when the user asks to add agent capability, heartbeat fields,
  snapshot payload, or ingest endpoints — not MCP, not a LangChain tool registry.
---

# 加 Agent 能力（ArchOps）

ADR-0043：Python 3.12+ host agent（systemd 交付）+ 控制面 ingest。**不是 MCP，不是 LangChain，不要复活 `com.archops.tools` / `ai` 工具注册表。**

先读：

- `docs/contracts/agent-heartbeat-snapshot.md`
- `agent/README.md`
- `agent/controller/AgentIngestController.java`（包 `com.archops.agent`）

## 流程清单

```
- [ ] 1. 确认票面要改的是 payload 契约、Python stub，还是 Java ingest
- [ ] 2. 契约变更：先改 docs/contracts/，再改代码
- [ ] 3. Python：agent/heartbeat.py（stdlib only，无 pip 依赖）
- [ ] 4. Java：com.archops.agent ingest → observed 直写（不人审、不覆盖策展）
- [ ] 5. 不要把 Agent 塞进默认 Compose；交付仍是 systemd
```

## 样板

| 类型 | 文件 |
|---|---|
| 契约 | `docs/contracts/agent-heartbeat-snapshot.md` |
| Python stub | `agent/heartbeat.py` |
| Ingest HTTP | `agent/controller/AgentIngestController.java` |
| 观测写入 | `observed/` |

## 硬性检查

- 观测心跳/探测直写；不自动覆盖策展
- 禁止旁路 SSH；禁止 LangChain 诊断编排主干
- 凭证不进日志/响应
- AI 出站走 WebClient + ADR-0041 白名单（不是 Agent tool）

## 验证

- `python3 agent/heartbeat.py --control-plane http://127.0.0.1:8080 ...`
- HTTP：`POST /api/agent/heartbeat`（见契约）
