---
name: implement
description: "Implement a piece of work based on a spec or set of tickets."
disable-model-invocation: true
---

Implement one frontier ticket from the tracker. Read `AGENTS.md` for which ticket is next.

Drive `/tdd` at the ticket's confirmed seam: **red → green → refactor**, one test per cycle. Witness the red run before production code for that slice. ArchOps overlay: `docs/agents/tdd.md`.

Run the single test each cycle. Run the full suite once at ticket end.

When the ticket's acceptance is green, `/code-review` (Standards + Spec). Commit each green slice; the review is the ticket-end gate, not a substitute for per-cycle refactor.
