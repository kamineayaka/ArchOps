# Seed Hadoop demo (ML-8-04)

Manual / semi-auto seed steps for the [mainline acceptance playbook](../mainline-acceptance.md) (Hadoop three-node demo).

Prerequisites: platform running; ADMIN signed in (default `admin` / `admin123`).

## 1. Create three assets (P2)

In **Assets**, create three SERVER assets with SSH credentials:

| Name  | Host (example) | Port |
|-------|----------------|------|
| node1 | 192.168.1.11   | 22   |
| node2 | 192.168.1.12   | 22   |
| node3 | 192.168.1.13   | 22   |

Use reachable hosts for live SSH; placeholders are fine for UI-only walkthrough.

## 2. Create Hadoop group (P3)

In **Asset groups**:

1. Create group `Hadoop` (optional description: `prod hadoop cluster`).
2. Edit members → select node1, node2, node3 → save.

Note the group id `G` from the table (used as partition key `group:G`).

## 3. Chat targets (P4)

Open **AI Ops** → new chat → set **Target groups** to `Hadoop` only (assets optional).

## 4. Discovery → proposal → merge (A1–A5)

1. Ask: “这是 Hadoop 集群，帮我确认各节点角色”.
2. Confirm Agent only `ssh_exec`s machines in the group.
3. When an architecture proposal is created, open **Architecture proposals** (chat card link or nav).
4. As a different OPERATOR/ADMIN (not the requester), **Approve**.
5. Open **Architecture** → partition `group:G` (and/or `asset:{id}`) → verify NN/DN facts and version +1.

## 5. Optional checks

| Step | Action |
|------|--------|
| B2 | Run a read-only `df -h` via chat → Work Log only, no new proposal |
| B3 | Approve a wrong fact, then ADMIN **Rollback** on the partition to previous version |
| Metrics | `GET /actuator/prometheus` → `archops_architecture_proposals_pending`, `*_merged_total`, `*_rollback_total` |

## API sketch (optional curl)

```bash
# After login, reuse Bearer token
TOKEN=...

# List partitions (keys may include global, group:1, asset:2)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/architecture/partitions

# Pending proposals
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/architecture/proposals?status=PENDING_REVIEW'
```

Partition path segments must be URL-encoded (`group:1` → `group%3A1`).
