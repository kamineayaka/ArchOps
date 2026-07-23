# Architecture

> Product north star: [product-vision.md](product-vision.md).  
> Mainline delivery plan: [mainline-implementation-plan.md](mainline-implementation-plan.md).  
> This document describes the current engineering shape.

## Overview

ArchOps AI Platform is a **B/S control plane** for managing Linux server fleets with AI-assisted operations.

```
Browser (Vue 3)
      │ HTTPS / WSS
      ▼
Nginx (frontend container)
      │ /api  /ws  /actuator
      ▼
Spring Boot Backend (stateless)
      ├── user/       Auth, RBAC, JWT, single-session
      ├── asset/      Inventory, encrypted SSH credentials, asset groups
      ├── terminal/   Web SSH proxy (MINA SSHD)
      ├── ai/         Agent loop, LLM providers, streaming WS
      ├── tools/      In-process agent tool registry (ssh_exec, list_assets, propose_architecture_update, ...)
      ├── approval/   RiskClassifier + ApprovalGate + workflow
      ├── knowledge/  Partitioned Architecture SSOT + proposals + work logs + scoped RAG
      ├── audit/      Append-only hash-chain log
      └── scheduler/  Periodic fleet inspection
      │
      ├── PostgreSQL 16 + pgvector (business data, vectors)
      ├── Redis 7       (sessions, cache)
      └── MinIO         (attachments)
```

Conversation targets may bind **assets and/or asset groups**; tools default to the resolved asset union
(`target assets ∪ group members`). Architecture is partitioned (`global` / `group:{id}` / `asset:{id}`)
with Proposal → review → merge (default no silent overwrite). See `docs/product-vision.md` and
`docs/mainline-domain-model.md`.## Module Boundaries

Each package under `com.archops.*` is a self-contained module:

- Own domain entities, repositories, services, controllers
- Cross-module calls go through public service interfaces only
- No circular dependencies between modules

## AI Agent Flow (ReAct)

```
User message
    → Build context (system prompt + architecture summary + RAG retrieval + recent logs)
    → LLM complete (with tool definitions)
    → Tool calls?
        YES → RiskClassifier → ApprovalGate
              → auto? execute tool → feed result back → loop (max 5)
              → manual? create Approval → notify user
        NO  → Return final answer
```

## RAG Pipeline

```
Source data (architecture / work_log / manual doc)
    → TextChunker (size + overlap)
    → EmbeddingProvider (OpenAI or Ollama)
    → kb_chunks (pgvector)

User query
    → Query embedding
    → Cosine similarity Top-K
    → Injected into AI system prompt
```

Configure via `archops.rag.*` in `application.yml` or `RAG_*` environment variables.
Run `POST /api/knowledge/reindex` after first deploy or provider change.

## Approval Matrix

| RBAC Tier | Policy A | Policy B | Policy C |
|---|---|---|---|
| LOW | All manual | — | — |
| MID | All manual | LOW auto | — |
| HIGH | All manual | LOW auto | LOW+MED auto, HIGH manual |

## Audit Hash Chain

Each `audit_log` row contains `prev_hash` and `curr_hash = SHA-256(prev_hash + payload)`.
Any modification breaks the chain, detectable via `GET /api/audit/verify`.

## Deployment

- **Docker Compose**: single-node, `deploy/compose/compose.yaml`
- **Kubernetes**: Helm chart scaffold, `deploy/helm/`
- **Observability**: optional overlay, `deploy/compose/compose.observability.yaml`

See [deployment.md](deployment.md) for operational procedures.
