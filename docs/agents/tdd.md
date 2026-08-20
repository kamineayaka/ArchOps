# ArchOps TDD overlay

Loop rules live in `.cursor/skills/tdd/SKILL.md` (desktop: `.agents/skills/tdd/SKILL.md`). This file is the ArchOps overlay: seams, witnessed **red**, Flyway, and TDD redo. `/implement` drives this overlay on every frontier ticket. If a skill conflicts, `AGENTS.md` and this file win.

## Seam

The ticket's spec `Testing seams (confirmed)` line is the seam. For `change-curated-draft` and the closed vertical-slice MVP that is the control-plane public HTTP API (unified `ApiResponse`, including Agent ingest). Gradle/`MockMvc` and Compose `bootRun`+`curl` are the same seam. Thin UI is demo-only and is not an automated seam.

## Cycle

Each cycle is one behavior at that seam:

1. Write **one** test method for **one** acceptance behavior.
2. Run that test. It is **red** (compile-fail or assertion-fail).
3. Paste the red command and the failing output under the ticket `## Comments`.
4. Write the minimum production code for that test.
5. Run the same test. It is **green**.
6. **Refactor** names and structure without changing behavior. Re-run the same test; it stays green.
7. Commit that slice.
8. Start the next test.

Run `cd backend && ./gradlew test` once at ticket end, then `/code-review` (Standards + Spec). `/code-review` is a second gate, not the refactor step.

The spec's HTTP tracer list is the **order of cycles**, not a license to write the whole suite before the first green.

## Witnessed red

Red is a command that exits non-zero for the missing behavior. Typical:

```text
cd backend && ./gradlew test --tests <FullyQualifiedClass>.<method>
```

Compile-fail counts as red. An already-green test is not a completed cycle.

Witness red in the session and record it on the ticket. Commit after **green**. Failing tests are not a ticket-done commit. Update the draft PR after green slices.

## TDD redo

A reopened ticket keeps the same acceptance criteria. Product behavior does not expand.

If the intended HTTP test is already green against current production, remove that ticket's production behavior first so the first cycle is red. Split any multi-behavior test method into one behavior per method, then cycle each.

Flyway stays forward-only: do not edit existing `V*.sql`. Reuse tables already migrated; a schema change is a new version.

## UI and helpers

Wire thin React+Ant UI after that ticket's HTTP cycles are green. `add-rest-api` / `add-frontend-page` checklists are the **green-phase** production layers, not a substitute for the red test.
