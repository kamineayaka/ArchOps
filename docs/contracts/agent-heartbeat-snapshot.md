# Agent heartbeat + snapshot contract (vertical-slice MVP)

**Status**: ticket 03  
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

| Field | Required | Notes |
|---|---|---|
| `agentId` | yes | Stable id for this agent process |
| `hostId` | yes | **Curated** physical host id the agent runs on |
| `sentAt` | no | Agent clock hint (control plane uses server time for freshness) |
| `snapshot` | no | Omit for heartbeat-only freshness ping |
| `snapshot.containers` | no | Present containers with labels |
| `snapshot.containers[].labels.archops.object_id` | for match | Missing → **未绑定观测候选** (`MISSING_LABEL`) |
| `snapshot.absentObjectIds` | no | Immutable object ids **explicitly asserted missing** → **观测消失** (`ABSENT`, usable value) |
| `snapshot.identityLostObjectIds` | no | Curated container ids / object ids whose label clue is lost → **身份失联**; `upgradeChainPromised=false` |

Heartbeat-only (`snapshot` omitted/null) updates freshness and does **not** invent observed facts.

## Matching rules

1. Label `archops.object_id=<value>` matches curated Docker container `immutable_object_id`.
2. Match → write/overwrite observed fact `RUNS_ON` / `运行于` with `availability=PRESENT`, `target_id=hostId`.
3. Unknown `archops.object_id` → unbound candidate `UNKNOWN_OBJECT_ID` (no auto-merge, no upgrade chain).
4. Missing label → unbound candidate `MISSING_LABEL` (no upgrade chain).
5. `absentObjectIds` → observed `availability=ABSENT` (观测消失). **Not** 观测空洞.
6. `identityLostObjectIds` → `identity_lost_mark` with `upgradeChainPromised=false`. Does not imply ABSENT.

## Response (envelope `ApiResponse`)

`data` includes `freshness.lastHeartbeatAt` / `lastSnapshotAt`, plus `matched`, `absent`, `unbound`, `identityLost` summaries for the request.

## Related operator reads (authenticated)

| API | Meaning |
|---|---|
| `GET /api/observed/asks/actual-where?containerId=` | 规范问法「实际在哪」; always co-returns `curatedValue` (P2) |
| `GET /api/agent/{agentId}/freshness` | Persisted heartbeat freshness |
| `GET /api/observed/unbound-candidates` | Unbound candidates (`upgradeChainPromised` always false) |
| `GET /api/observed/identity-lost/{curatedObjectId}` | Identity-lost mark |

`observedValue.availability`: `PRESENT` | `ABSENT` | `HOLLOW`  
(`HOLLOW` = no currently usable observed fact; timeout→空洞 productization is ticket 10.)

## Python stub

`agent/heartbeat.py` sends this contract (`ARCHOPS_HOST_ID` required for real ingest).
