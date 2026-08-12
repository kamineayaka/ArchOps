# CLAUDE.md — ArchOps

Claude / Cloud 编码助手请把本仓库的 **`AGENTS.md` 当作最高执行纪律**，并遵守下列指针。不要根据「常见 Java/Vue 运维台」惯性选型。

## Mandatory reading

1. [AGENTS.md](./AGENTS.md) — 防漂移、栈、模块、工单方式  
2. [CONTEXT.md](./CONTEXT.md) — 领域术语（双轨、冲突、计划冻结等）  
3. [docs/adr/0043-tech-stack.md](./docs/adr/0043-tech-stack.md) — Gradle / MyBatis-Plus / React+Ant / PG+Redis  
4. [docs/specs/vertical-slice-mvp.md](./docs/specs/vertical-slice-mvp.md) — 竖切 Spec  
5. [docs/dev-handoff.md](./docs/dev-handoff.md) — 下一张 frontier 工单  
6. Tickets: [.scratch/vertical-slice-mvp/issues/](./.scratch/vertical-slice-mvp/issues/)
7. Cloud VM setup: [.cursor/CLOUD.md](./.cursor/CLOUD.md) · [.cursor/environment.json](./.cursor/environment.json)

## Non-negotiables (short)

- Domain contract frozen (ADR-0039). Do not change semantics in code; new ADR first.
- Stack frozen (ADR-0043): **no Maven, no JPA-as-base, no Vue frontend, no Neo4j-required-in-v1, no LangChain backbone, Redis is not truth SSOT**.
- Implement **one ticket at a time** from the scratch issues folder; prefer HTTP API acceptance seams.
- Do not revive deleted legacy packages (`ai`, `asset`, `graph` Neo4j SSOT, architecture proposals, etc.).

## Package layout

`com.archops.{common,curated,observed,conflict,plan,user,agent}` — MyBatis-Plus mappers, Flyway forward-only.
