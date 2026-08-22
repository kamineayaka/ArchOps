# Agent heartbeat + snapshot contract

**Status**: vertical-slice ticket 03 + unbound-identity-rebind tickets 01 and 04  
**Seam**: `POST /api/agent/heartbeat` (control-plane public HTTP; no operator `X-ArchOps-User-Id`)  
**SSOT**: PostgreSQL observed tables. Redis is not relationship truth.

## Purpose

Host Agent proves the observation channel is fresh via heartbeat, and may attach a
**状态快照** so the control plane can write **观测真相** (e.g. container `运行于` host)
without human review. Matching uses the immutable label `archops.object_id`.

## Request

```http
POST /api/agent/heartbeat
Content-Type: application/json
```

```json
{
  "agentId": "agent-on-host-b",
  "hostId": "host-<curated-physical-host-uuid>",
  "sentAt": "2026-08-12T01:00:00Z",
  "snapshot": {
    "containers": [
      {
        "runtimeId": "docker-deadbeef",
        "name": "app-x",
        "labels": {
          "archops.object_id": "ctr-x-001"
        }
      }
    ],
    "absentObjectIds": ["ctr-gone-001"],
    "identityLostObjectIds": ["ctr-x-001"]
  }
}
```

Heartbeat-only (omit `snapshot`):

```json
{
  "agentId": "agent-on-host-b",
  "hostId": "host-<curated-physical-host-uuid>"
}
```

| Field | Required | Notes |
|---|---|---|
| `agentId` | yes | Stable id for this agent process |
| `hostId` | yes | **Curated** physical host id the agent runs on |
| `sentAt` | no | Agent clock hint (control plane uses server time for freshness) |
| `snapshot` | no | Omit for heartbeat-only freshness ping |
| `snapshot.containers` | no | Present containers with labels. Fixtures **Must** send `runtimeId` |
| `snapshot.containers[].runtimeId` | for upsert | Together with `sourceHostId`, identifies the field entity for 未绑定 upsert |
| `snapshot.containers[].labels.archops.object_id` | for match | Missing → **未绑定观测候选** (`MISSING_LABEL`) |
| `snapshot.absentObjectIds` | no | Immutable object ids **explicitly asserted missing** → **观测消失** (`ABSENT`, usable value) |
| `snapshot.identityLostObjectIds` | no | Optional explicit 身份失联 (immutable object id). Same **host scope** as control-plane inference |

Heartbeat-only (`snapshot` omitted/null) updates freshness and does **not** invent observed facts, 未绑定 rows, or 身份失联 marks. It also does **not** infer 身份失联, consume 绑定记忆, release stale 绑定记忆, or void 未绑定草案.

## Matching rules

1. Label `archops.object_id=<value>` matches curated Docker container `immutable_object_id`. Name and `runtimeId` never decide identity.
2. Match → write/overwrite observed fact `RUNS_ON` / `运行于` with `availability=PRESENT`, `target_id=hostId`. Then **clear 身份失联** (delete `identity_lost_mark` for that object), **consume** that object's 绑定记忆 and the matching 未绑定候选 rows, **void** related OPEN 未绑定草案, and run the existing merge-key compare. Hit is recognition, not 观测空洞恢复.
3. Unknown `archops.object_id` → unbound candidate `UNKNOWN_OBJECT_ID` (no auto-merge, no upgrade chain).
4. Missing label → unbound candidate `MISSING_LABEL` (no upgrade chain).
5. Unbound candidates **upsert** by (`sourceHostId`, `runtimeId`): refresh `observedAt`, name, labels, reason. One field entity stays one row. Refreshing the same key does **not** void an OPEN 未绑定草案 and does **not** release 绑定记忆.
6. `absentObjectIds` → observed `availability=ABSENT` (观测消失). **Not** 观测空洞 and **not** 身份失联. If that object had 身份失联 or 绑定记忆: clear the mark, release memory for that curated object, and leave a still-unlabeled field entity in 待并入 (do **not** delete its 未绑定候选 row).
7. After label match and `absentObjectIds` on this snapshot: if the reporting host is in **host scope** for a curated Docker container that has curated `运行于`, and this snapshot did not label-match it and did not list it in `absentObjectIds`, the control plane **infers** 身份失联 (`reason=LABEL_CLUE_LOST`, `upgradeChainPromised=false`). Agent `identityLostObjectIds` is optional.
8. **Host scope** (read at the start of snapshot processing, not from PRESENT written later in this same snapshot): reporting `hostId` equals the container’s curated `运行于` target, **or** its currently usable observed `运行于` target (`observed_fact.availability=PRESENT` and not heartbeat-timeout 空洞). If never observed, curated host only. A snapshot from any other host must not mark that container 身份失联 — including when it sends `identityLostObjectIds`.
9. `identityLostObjectIds` still upserts the mark when the reporting host is in that same host scope, except it must not re-mark an object this snapshot just label-matched or listed in `absentObjectIds`. It does not imply ABSENT. Out-of-scope declarations are ignored.
10. **绑定记忆** (matching state after 逐条确认, not a glossary term and not a fourth 冲突): key (`sourceHostId`, `runtimeId`) → curated object, unique per curated object. It does **not** write observed `运行于` and does **not** promise an upgrade chain. Default 待并入 hides keys that have 绑定记忆.
11. A snapshot from host H is that host's complete field inventory. If 绑定记忆 `(H, runtime r)` exists and this snapshot does not report `r`, that memory and the `(H, r)` 未绑定候选 row are expired: delete them. Do **not** clear 身份失联 on the remembered object (nobody recognized it, and nobody asserted it is gone). Heartbeat-only does not expire anything.

## Label-match consume (ticket 04)

When a container label hits curated object X in the same ingest request:

1. Write observed `运行于` PRESENT on the reporting host.
2. Clear 身份失联 for X (`GET /api/observed/identity-lost/{X}` → 400 `IDENTITY_LOST_NOT_FOUND`; 「实际在哪」 `identityLost=false`, `availability=PRESENT`).
3. Delete 绑定记忆 rows with `curated_object_id = X`, and delete 未绑定候选 rows for those (`sourceHostId`, `runtimeId`) keys plus this container's own (`hostId`, `runtimeId`).
4. VOID OPEN 未绑定草案 (`origin=UNBOUND_CANDIDATE`) whose `candidate_id` / (`source_host_id`, `runtime_id`) was consumed, or whose BIND target / accepted CREATE subject is X. Further item accept → 400 `DRAFT_VOIDED`. Items stay PENDING. Do not void `CHANGE_CURATED` 草案. Audit event is `curated_draft_event` `DRAFT_VOIDED` (`detail.hint` includes `草案已作废`), not `conflict_case_event`.
5. Existing merge-key compare: equal and no active 冲突 → invent nothing; unequal → open or upgrade. Same-name unlabeled never promises the upgrade chain.

## Response (envelope `ApiResponse`)

`data` includes `freshness.lastHeartbeatAt` / `lastSnapshotAt`, plus `matched`, `absent`, `unbound`, `identityLost` summaries for the request. Completion is subsequent operator GETs, not the shape of those summary arrays.

## Related operator reads (authenticated)

| API | Meaning |
|---|---|
| `GET /api/observed/asks/actual-where?containerId=` | 规范问法「实际在哪」; always co-returns `curatedValue` (P2) |
| `GET /api/curated/asks/should-where?containerId=` | 规范问法「应该在哪」; curated `运行于` only |
| `GET /api/agent/{agentId}/freshness` | Persisted heartbeat freshness |
| `GET /api/observed/unbound-candidates` | Unbound candidates: `labels` (JSON object), `runtimeId`, `name`, `reason`, `sourceHostId`; `upgradeChainPromised` always false |
| `GET /api/observed/identity-lost/{curatedObjectId}` | Identity-lost mark; 400 `IDENTITY_LOST_NOT_FOUND` if none |

`observedValue.availability` on 「实际在哪」: `PRESENT` | `ABSENT` | `HOLLOW` | `IDENTITY_LOST`  
(`HOLLOW` = no currently usable observed fact; timeout→空洞 is vertical-slice ticket 10.)  
`IDENTITY_LOST` is **ask-DTO only** when `identityLost=true`: `observedValue.hostId` must be null (must not report the pre-loss host as 实际). Do **not** store `IDENTITY_LOST` as `observed_fact.availability` (that CHECK remains `PRESENT`/`ABSENT` only) and do **not** add a new `ConflictStatus`.

## Python stub

`agent/heartbeat.py` may still send `identityLostObjectIds`. The control plane Must infer 身份失联 when that field is omitted, if host scope and match/absent rules above hold.
