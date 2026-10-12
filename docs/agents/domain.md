# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## ArchOps freeze (overrides lazy edits)

This is a **single-context** repo. `CONTEXT.md` and the domain contract (**ADR-0039**) are **frozen**.

- Use glossary terms exactly; do not invent synonyms.
- `/domain-modeling` and `/grill-with-docs` **must not** silently rewrite `CONTEXT.md` or existing ADRs.
- New terms or contract changes require a **new ADR** first (`AGENTS.md`). Implementation must not change semantics in code.
- Technical stack is **ADR-0043** (Gradle / MyBatis-Plus / React+Ant / PG+Redis). Process split is **ADR-0044** (control-plane hub, executor, AI orchestrator). Control-plane → executor transport is **ADR-0045** (gRPC). Conflict identity is **ADR-0046** (no operator, no handler, no 已知悉 / 指派). Cause is not a catalog (**ADR-0047**). Probe tasks are **ADR-0048** (the host agent pulls them on heartbeat; the control plane judges). Delivery and orchestrator transport are **ADR-0049** (three Kubernetes Deployments: 控制面, 执行引擎, 编排层; orchestrator ↔ control plane is gRPC; model HTTPS stays on the orchestrator; no Eureka; no gateway in front of internal gRPC; host agent stays on the host). Skills must not introduce Maven, JPA-as-base, Vue, Neo4j-required-in-v1, or LangChain.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root
- **`docs/adr/`** — especially `0039-domain-contract-frozen.md`, `0043-tech-stack.md`, and **0046 through 0049**, plus ADRs that touch the area you're about to work in
- Current frontier spec listed in `AGENTS.md` / `docs/dev-handoff.md`
- `/implement` also reads [`docs/agents/tdd.md`](tdd.md)

## File structure

```
/
├── CONTEXT.md
├── docs/adr/
├── docs/specs/
└── .scratch/<feature-slug>/issues/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap that needs a new ADR, not an inline rewrite of `CONTEXT.md`.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0039 (domain contract frozen) — but worth reopening because…_
