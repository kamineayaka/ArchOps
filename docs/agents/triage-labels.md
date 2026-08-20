# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

Local markdown tickets record the role on a `Status:` line (not GitHub labels).

| Label in mattpocock/skills | Label in our tracker | Meaning |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage` | `needs-triage` | Maintainer needs to evaluate this issue |
| `needs-info` | `needs-info` | Waiting on reporter for more information |
| `ready-for-agent` | `ready-for-agent` | Fully specified, ready for an AFK agent |
| `ready-for-human` | `ready-for-human` | Requires human implementation |
| `wontfix` | `wontfix` | Will not be actioned |

ArchOps addition: completed tickets use `Status: done` (equivalent to closed). Do not treat `done` tickets as frontier. A **TDD redo** reopens a ticket as `ready-for-agent` with the same acceptance list; see [`tdd.md`](tdd.md).

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.
