# Cloud Agent 竖切实现 Prompt

复制到 Cursor Cloud Agent 任务描述：

```text
实现 ArchOps 竖切 frontier 工单。强制先读并遵守：

1. AGENTS.md
2. CLAUDE.md
3. CONTEXT.md
4. docs/adr/0043-tech-stack.md
5. docs/specs/vertical-slice-mvp.md
6. docs/dev-handoff.md（确认下一张票）
7. 对应 `.scratch/vertical-slice-mvp/issues/0N-*.md`

规则：
- 只做这一张票的验收项；不要顺手做下一张
- 栈：Gradle + MyBatis-Plus + React/Ant + PG + Redis；禁止 Vue/JPA/Maven/Neo4j必选/LangChain
- 不复活旧域包；不改 Flyway 历史脚本；改语义须 ADR
- 主验收接缝：HTTP API；完成后更新 docs/dev-handoff.md 下一票指针
```
