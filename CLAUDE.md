# CLAUDE.md — ArchOps

Claude / Cloud 编码助手请把本仓库的 **`AGENTS.md` 当作最高执行纪律**，并遵守下列指针。不要根据「常见 Java/Vue 运维台」惯性选型。

## Mandatory reading

1. [AGENTS.md](./AGENTS.md) — 防漂移、栈、模块、工单方式  
2. [CONTEXT.md](./CONTEXT.md) — 领域术语（双轨、冲突、计划冻结等）  
3. [docs/adr/0043-tech-stack.md](./docs/adr/0043-tech-stack.md) — Gradle / MyBatis-Plus / React+Ant / PG+Redis  
4. [docs/specs/vertical-slice-mvp.md](./docs/specs/vertical-slice-mvp.md) — 竖切 Spec（01–13 已闭合）  
5. [docs/specs/change-curated-draft.md](./docs/specs/change-curated-draft.md) — 下一刀 Spec（改策展/草案逐条确认）  
6. [docs/dev-handoff.md](./docs/dev-handoff.md) — 进度与下一票  
7. Tickets: 竖切 [.scratch/vertical-slice-mvp/issues/](./.scratch/vertical-slice-mvp/issues/) 已 done；改策展 [.scratch/change-curated-draft/issues/](./.scratch/change-curated-draft/issues/)（**01 TDD-done**；02–03 TDD redo；frontier = **02**）
8. Cloud VM setup: [.cursor/CLOUD.md](./.cursor/CLOUD.md) · [.cursor/environment.json](./.cursor/environment.json)
9. Matt tracker config: [`docs/agents/`](./docs/agents/)（含 [`docs/agents/tdd.md`](./docs/agents/tdd.md)）

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
- Stack frozen (ADR-0043): **no Maven, no JPA-as-base, no Vue frontend, no Neo4j-required-in-v1, no LangChain backbone, Redis is not truth SSOT**.
- Implement **one ticket at a time** from the scratch issues folder; `/implement` drives `/tdd` (**red → green → refactor**) at the HTTP API acceptance seam (`docs/agents/tdd.md`).
- Do not revive deleted legacy packages (`ai`, `asset`, `graph` Neo4j SSOT, architecture proposals, etc.).

## Package layout

`com.archops.{common,curated,observed,conflict,plan,user,agent}` — MyBatis-Plus mappers, Flyway forward-only.
