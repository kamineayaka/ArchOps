# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## ArchOps freeze (overrides lazy edits)

This is a **single-context** repo. `CONTEXT.md` and the domain contract (**ADR-0039**) are **frozen**.

- Use glossary terms exactly; do not invent synonyms.
- `/domain-modeling` and `/grill-with-docs` **must not** silently rewrite `CONTEXT.md` or existing ADRs.
- New terms or contract changes require a **new ADR** first (`AGENTS.md`). Implementation must not change semantics in code.
- Technical stack is **ADR-0043** (Gradle / MyBatis-Plus / React+Ant / PG+Redis). Skills must not introduce Maven, JPA-as-base, Vue, Neo4j-required-in-v1, or LangChain.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root
- **`docs/adr/`** — especially `0039-domain-contract-frozen.md` and `0043-tech-stack.md`, plus ADRs that touch the area you're about to work in
- Current frontier spec listed in `AGENTS.md` / `docs/dev-handoff.md`

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
