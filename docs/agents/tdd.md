# ArchOps TDD overlay

Loop rules live in `.cursor/skills/tdd/SKILL.md` (desktop: `.agents/skills/tdd/SKILL.md`). This file is the ArchOps overlay: seams, witnessed **red**, Flyway, TDD redo, and suite/tracer tickets. `/implement` drives this overlay on every frontier ticket. If a skill conflicts, `AGENTS.md` and this file win.

## Seam

The ticket's spec `Testing seams (confirmed)` line is the seam. For `unbound-identity-rebind`, `change-curated-draft`, the closed vertical-slice MVP, and `conflict-upgrade-void-plans` that is the control-plane public HTTP API. For `control-plane-executor` the **primary** seam is still that HTTP API; the spec also requires 执行引擎 `grpc.health.v1` and a narrow mTLS-negative gRPC call. Gradle/`MockMvc` and Compose `bootRun`+`curl` are the same HTTP seam. Thin UI is demo-only and is not an automated seam.

## Cycle

Each cycle is one behavior at that seam:

1. Write **one** test method for **one** acceptance behavior.
2. Run that test. For a **capability** ticket it is **red** (compile-fail or assertion-fail). For a **suite/tracer** ticket, see Suite / tracer tickets below (first-run green is allowed as reuse; do not delete production to fake red).
3. Paste the command and output under the ticket `## Comments` (capability tickets: the failing red; suite tickets: green reuse or red gap — see below).
4. Capability tickets: write the minimum production code for that test. Suite tickets: production only if the run was a real composition gap.
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

Compile-fail counts as red. An already-green **capability** test is not a completed cycle (use TDD redo). A new **suite** method that is green on first run is a completed cycle only when the output is recorded as `reuse/regression` on the ticket.

Witness red in the session and record it on the ticket. Commit after **green**. Failing tests are not a ticket-done commit. Update the draft PR after green slices.

## TDD redo

A reopened **capability** ticket keeps the same acceptance criteria. Product behavior does not expand.

If that ticket's intended HTTP test is already green against current production, remove **that ticket's** production behavior first so the first cycle is red. Split any multi-behavior test method into one behavior per method, then cycle each. Do not remove a *different* ticket's production to manufacture red.

Flyway stays forward-only: do not edit existing `V*.sql`. Reuse tables already migrated; a schema change is a new version.

## Suite / tracer tickets

A ticket whose job is an ordered HTTP suite (change-curated **06**, vertical-slice **13**) — Out of ticket includes “no new product capability” — does **not** use TDD redo’s “remove production first”.

Each cycle is still one suite method at the HTTP seam:

1. Write the method. Run only that method. Paste the command and output on the ticket.
2. First-run **green**: record `reuse/regression` and name the focused 01–05 (or vertical-slice) test that already covers it. Keep the new suite method; it is this knife’s definition of done. Do not delete production or focused tests to fake red.
3. First-run **red** because the test is wrong (fixture, id collision, path typo): fix the test, re-run. If it is then green, treat as reuse.
4. First-run **red** because composition is missing: that is a gap in earlier tickets. Fix the minimum production to **existing** earlier-ticket semantics (same error codes, same state machine). Do not invent product, routes, or item types.
5. Refactor helpers in the suite class. Re-run. Commit. Next method.

Happy path 1–N in the spec is **one** ordered method (step comments inside), not N cycles. Each negative is its own method with its own fixture. `@HttpAcceptanceTest` refreshes the database `AFTER_CLASS`, so ids must be unique per method.

Do not merge focused tests into the suite by deletion. Do not add Playwright or SSH as a second seam unless the ticket says so.

## UI and helpers

Wire thin React+Ant UI after that ticket's HTTP cycles are green. `add-rest-api` / `add-frontend-page` checklists are the **green-phase** production layers, not a substitute for the red test.
