# CLAUDE.md — ArchOps

Claude / Cloud 编码助手请把本仓库的 **`AGENTS.md` 当作最高执行纪律**，并遵守下列指针。不要根据「常见 Java/Vue 运维台」惯性选型。

## Mandatory reading

1. [AGENTS.md](./AGENTS.md) — 防漂移、栈、模块、工单方式  
2. [CONTEXT.md](./CONTEXT.md) — 领域术语（双轨、冲突、计划冻结等）  
3. [docs/adr/0043-tech-stack.md](./docs/adr/0043-tech-stack.md) — Gradle / MyBatis-Plus / React+Ant / PG+Redis；进程切分 [ADR-0044](./docs/adr/0044-control-plane-hub-executor-and-ai-orchestrator.md)  
4. [docs/specs/vertical-slice-mvp.md](./docs/specs/vertical-slice-mvp.md) — 竖切 Spec（01–13 已闭合）  
5. [docs/specs/change-curated-draft.md](./docs/specs/change-curated-draft.md) — 改策展/草案逐条确认 Spec（**已闭合**）  
6. [docs/specs/unbound-identity-rebind.md](./docs/specs/unbound-identity-rebind.md) — 下一刀 Spec（未绑定观测候选 / 身份失联重绑；01–07 已拆）  
7. [docs/dev-handoff.md](./docs/dev-handoff.md) — 进度与下一票  
8. Tickets: 竖切 [.scratch/vertical-slice-mvp/issues/](./.scratch/vertical-slice-mvp/issues/) 已 done；改策展 [.scratch/change-curated-draft/issues/](./.scratch/change-curated-draft/issues/)（**01–06 TDD-done**；本刀闭合）。下一刀 [.scratch/unbound-identity-rebind/issues/](./.scratch/unbound-identity-rebind/issues/)（**01–04 + 08 TDD-done；frontier = 05**；01–03 合同审计报告 [audit-01-03-opus.md](./.scratch/unbound-identity-rebind/audit-01-03-opus.md)，C-3 / S-4 / S-2 已由票 04 处置，C-1 = 票 09 待人排期；代码 vs ADR-0044 审计 [audit-code-vs-adr-0044.md](./.scratch/unbound-identity-rebind/audit-code-vs-adr-0044.md)；票 05 prompt [docs/implement-unbound-identity-rebind-05-prompt.md](./docs/implement-unbound-identity-rebind-05-prompt.md)）
9. Cloud VM setup: [.cursor/CLOUD.md](./.cursor/CLOUD.md) · [.cursor/environment.json](./.cursor/environment.json)
10. Matt tracker config: [`docs/agents/`](./docs/agents/)（含 [`docs/agents/tdd.md`](./docs/agents/tdd.md)）

## Agent skills

### Issue tracker

Local markdown under `.scratch/<feature-slug>/`; canonical specs in `docs/specs/`. Cloud `gh` is read-only. See `docs/agents/issue-tracker.md`.

### Triage labels

Ticket `Status:` line uses the canonical roles. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context frozen `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.

### TDD

`/implement` drives `/tdd`: **red → green → refactor**. Overlay: [`docs/agents/tdd.md`](./docs/agents/tdd.md).

## Non-negotiables (short)

- Domain contract frozen (ADR-0039). Do not change semantics in code; new ADR first.
- Stack frozen (ADR-0043 + **ADR-0044**): **no Maven, no JPA-as-base, no Vue frontend, no Neo4j-required-in-v1, no LangChain backbone, Redis is not truth SSOT**; 控制面不持模型密钥，不把执行引擎/编排层塞进未绑定票。
- Implement **one ticket at a time** from the scratch issues folder; `/implement` drives `/tdd` (**red → green → refactor**) at the HTTP API acceptance seam (`docs/agents/tdd.md`).
- Do not revive deleted legacy packages (`ai`, `asset`, `graph` Neo4j SSOT, architecture proposals, etc.).

## Package layout

`com.archops.{common,curated,observed,conflict,plan,user,agent}` — MyBatis-Plus mappers, Flyway forward-only.
