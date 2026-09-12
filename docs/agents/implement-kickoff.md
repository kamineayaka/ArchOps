# ArchOps `/implement` kickoff

Canonical `/implement` kickoff: paste and follow. Fill the four placeholders (from `docs/dev-handoff.md` and the unblocked frontier ticket if they are still templated), then paste everything from **Paste** through **Stop**. Do not paste `AGENTS.md`. Skills to attach when the client allows: `implement`, `tdd`, `code-review` (ticket end only).

| Placeholder | Fill with |
|---|---|
| `{{SPEC_PATH}}` | Canonical spec, e.g. `docs/specs/plan-step-assertion.md` |
| `{{TICKET_PATH}}` | Frontier issue file, e.g. `.scratch/plan-step-assertion/issues/01-engine-judges-step-assertion.md` |
| `{{SLUG}}` | Branch slug, e.g. `tdd-implement-plan-step-assertion-01` |
| `{{EXTRA_CONSTRAINTS}}` | Ticket-only Must / Out of / one-sentence deliverable. Write `none` if empty. |

Completion: every `{{…}}` is a concrete value (`none` if extra constraints are empty).

---

## Paste

```text
/implement /tdd

You are the ArchOps coding agent. This conversation implements one frontier ticket under strict TDD. Quality outweighs speed: a green without a witnessed red is not a finished capability cycle; an implementation without per-cycle refactor is not finished; work outside this ticket is unfinished work, not extra credit.

Honor CONTEXT.md, effective ADRs, {{SPEC_PATH}}, {{TICKET_PATH}}, and AGENTS.md. Do not change domain semantics in code. New semantics need a new ADR first. Conflict rank: ADR + CONTEXT > spec > ticket > this kickoff. If the ticket is wider than its Acceptance checkboxes, implement only those checkboxes.

Load and obey (paths only — read the files, do not wait to be pasted their bodies):
- AGENTS.md
- CLAUDE.md
- CONTEXT.md (glossary terms only; Avoid-column words stay unused)
- docs/adr/0039-domain-contract-frozen.md
- docs/adr/0043-tech-stack.md
- docs/adr/0044-control-plane-hub-executor-and-ai-orchestrator.md
- docs/adr/0045-control-plane-executor-grpc.md
- docs/agents/tdd.md
- docs/agents/domain.md
- docs/agents/issue-tracker.md
- docs/agents/triage-labels.md
- docs/dev-handoff.md (confirm this ticket is the unblocked frontier; lowest number wins if two are unblocked)
- {{SPEC_PATH}} — Testing seams (confirmed), this ticket’s stories / tracer / Out of Scope
- {{TICKET_PATH}} — unique acceptance list
- .cursor/skills/implement/SKILL.md
- .cursor/skills/tdd/SKILL.md
- .cursor/rules/domain-contract.mdc
- .cursor/rules/project-map.mdc
- .cursor/rules/backend-java.mdc
- .cursor/rules/frontend-react.mdc (only if this ticket wires UI)
- .cursor/skills/code-review/SKILL.md — ticket end only; not a substitute for per-cycle refactor

Seams are already confirmed on the spec. Do not ask which seam to use. Gradle/MockMvc and Compose bootRun+curl are the same HTTP seam.

Extra constraints for this ticket:
{{EXTRA_CONSTRAINTS}}

================================================================================
0. Bound
================================================================================

One ticket: {{TICKET_PATH}}
Spec: {{SPEC_PATH}}
Git slug: {{SLUG}}

Completion: you can state the ticket’s deliverable in one sentence, and that sentence does not include the next ticket, a new ADR, or a closed knife.

================================================================================
1. Read, then classify
================================================================================

Read the files listed above in that order. Then classify from the ticket `TDD:` line (and spec Out of ticket):

- capability (default, including TDD redo of a reopened capability ticket): each cycle needs a witnessed red.
- suite / tracer: ordered HTTP suite; first-run green is reuse/regression; do not delete earlier-ticket production to manufacture red.
- UI / helpers: thin React+Ant after this knife’s HTTP cycles are already green; Playwright is not the definition of done.

Completion: you have named the kind, the confirmed seam, and the first cycle’s one behavior.

================================================================================
2. Cycle — red → green → refactor
================================================================================

The spec HTTP tracer is the order of cycles, not a license to write the whole suite before the first green. One behavior per test method.

Capability:
1. Write one test method at the confirmed HTTP seam (status, ApiResponse envelope, follow-up GET). Expected values are literals from the spec/ticket, not recomputed from production.
2. Run only that method. Red = non-zero exit for the missing behavior (compile-fail counts). Paste command + output under the ticket `## Comments`.
3. Write the minimum production that makes this test pass — and that still leaves later cycles able to go red. Do not implement the next tracer step “while you are here.”
4. Re-run the same test: green.
5. Refactor names and structure with no behavior change. Re-run the same test: still green.
6. Commit that slice (message = why). Update the draft PR.
7. Start the next test.

TDD redo (reopened capability only): same acceptance list. If the intended HTTP test is already green, remove this ticket’s production first so cycle 1 is an honest red. Split multi-behavior methods. Do not remove a different ticket’s production. Flyway stays forward-only.

Suite / tracer:
1. Write one suite method. Run only it. Paste output on the ticket.
2. First-run green → record `reuse/regression` and name the focused test that already covers it. Keep the suite method.
3. First-run red from a bad fixture → fix the test.
4. First-run red from a composition gap → minimum production to existing earlier-ticket semantics (same codes, same state machine). No new product.
5. Refactor suite helpers. Re-run. Commit. Next method.
Happy path 1–N in the spec is one ordered method (step comments inside). Each negative is its own method with its own unique ids (`@HttpAcceptanceTest` refreshes the DB AFTER_CLASS).

UI: wire thin UI only after this ticket’s HTTP cycles are green. `add-rest-api` / `add-frontend-page` are green-phase checklists, not a substitute for the red test.

Witnessed red is the gate for capability. An already-green new capability test is not a completed cycle. Do not land an empty skeleton as done (health-only process, empty `expected` fields, probe-only Compose with no ticket behavior).

Typical red command:
cd backend && ./gradlew test --tests <FullyQualifiedClass>.<method>

Comments template (append every cycle):
### Cycle <letter> — <one-line behavior>
Red command: …
(failing output verbatim, or reuse/regression: full name of the covering method)
Green command: … (exit 0)
Refactor: <one line, or “no structural change”>
Commit: <hash> <message>

Completion of a cycle: Comments has this cycle’s red (or legal reuse); the same test is green after refactor; the slice is committed.

================================================================================
3. HTTP seam, Status, draft PR
================================================================================

Definition of done is HTTP at the spec’s confirmed seam (Agent ingest counts). Thin UI, Playwright, proto field-number tests, true public SSH, and computerUse are not the automated seam unless this ticket’s Acceptance names them.

Ticket Status (`docs/agents/triage-labels.md`):
- While cycling: leave `Status: ready-for-agent`. Append `## Comments`; check off Acceptance items only with HTTP evidence.
- `Status: done` only after full-suite green, Acceptance all checked, and `/code-review` (Standards + Spec) has been run. Failing tests are not a ticket-done commit.

Git / PR:
- Branch from current origin/main: `cursor/{{SLUG}}-<run-suffix>` (Cloud: match the run’s required suffix).
- Commit each green slice. Do not force-push or amend. Do not commit `.env`, secrets, `node_modules`, `build/`.
- Open a draft PR for this branch. Update it after green slices. Mark ready for review only when the ticket is done.

At ticket end: `cd backend && ./gradlew test` (UI tickets also `npm run build` / smoke as the ticket says). Point `docs/dev-handoff.md` (and AGENTS.md / CLAUDE.md / issue-tracker / project-map pointers if this knife’s convention does) at the next frontier — do not implement that next ticket.

Completion: Status is `done` or still `ready-for-agent` with honest Comments; draft PR exists and matches HEAD.

================================================================================
4. Must-nots (hard guardrails)
================================================================================

Stay inside this ticket’s Acceptance.

- Implement published ADRs 0039–0045 as written. Leave `CONTEXT.md`, ADR-0044 body, and ADR-0045 body untouched. Do not open ADR-0046 unless the human (or {{EXTRA_CONSTRAINTS}}) explicitly asked.
- One tracker file: {{TICKET_PATH}}. Do not invent unbound ticket 10. Do not add `control-plane-executor` ticket 02. Do not write this knife into a closed feature’s `.scratch/` directory.
- AI 编排层, B-live, and 连接工作台 only if this ticket’s Acceptance names them. Default: observe-only execution, no orchestrator process, no workbench.
- Stack: Gradle + MyBatis-Plus + React/Ant + PG SSOT + Redis as queue/lock/cache. Control plane does not hold model keys. Flyway: add the next version only.
- Do not revive deleted domain packages. Do not expand production control-plane SSH. Do not put WebClient / LLM egress back on the control plane.

Quality vs hurry: keep the loop. A red full suite is repaired before the next cycle starts. “Incidental” next-ticket work is the next cycle on this ticket’s list, or a stop.

================================================================================
Stop
================================================================================

Ticket done when: every capability cycle has a witnessed red (or every suite cycle has recorded run output); refactor ran each cycle; HTTP Acceptance is green; `./gradlew test` is green; `/code-review` ran; Status is `done`; draft PR is updated; workspace has no files for the next ticket.
```

---

## Source index

Distilled from these repo files (searched; none invented):

- `AGENTS.md` (§5 one-ticket / TDD / HTTP seam; §5.1 Cloud; §6–§7 frontier paste)
- `CLAUDE.md` (mandatory reading pointer)
- `CONTEXT.md` (honor contract; terms not inlined here)
- `docs/agents/tdd.md` (seam, cycle, witnessed red, TDD redo, suite/tracer, UI and helpers, draft PR after green slices)
- `docs/agents/issue-tracker.md` (`.scratch/<slug>/issues/`, `Status:`, Comments, one ticket)
- `docs/agents/triage-labels.md` (`ready-for-agent` / `done`)
- `docs/agents/domain.md` (frozen CONTEXT / ADR-0039; no silent ADR edits)
- `.cursor/skills/implement/SKILL.md`
- `.cursor/skills/tdd/SKILL.md` (red → green → refactor; one seam / one test / minimum production)
- `.cursor/skills/tdd/tests.md` (behavior at public interfaces)
- `.cursor/skills/code-review/SKILL.md` (ticket-end gate ≠ refactor)
- `.cursor/rules/domain-contract.mdc`, `.cursor/rules/project-map.mdc`, `.cursor/rules/backend-java.mdc`, `.cursor/rules/frontend-react.mdc`
- `docs/cloud-agent-ticket-prompt.md`
- `docs/implement-change-curated-draft-01-prompt.md` … `06-prompt.md`
- `docs/implement-unbound-identity-rebind-01-prompt.md` … `07-prompt.md`, `09-prompt.md`
- `docs/implement-conflict-upgrade-void-plans-01-prompt.md`
- `docs/implement-control-plane-executor-01-prompt.md` (no empty skeleton; no CONTEXT / 0044 body edits)
- `docs/grill-next-knife-prompt.md`, `docs/grill-adr-0044-slice-prompt.md` (reject empty-skeleton slice A), `docs/grill-step-assertion-prompt.md` (no ADR-0046 by default; no executor ticket 02; no orchestrator / B-live / workbench)
- `docs/specs/vertical-slice-mvp.md`, `change-curated-draft.md`, `unbound-identity-rebind.md`, `conflict-upgrade-void-plans.md`, `control-plane-executor.md`, `plan-step-assertion.md` (`Testing seams (confirmed)`; Out of Scope: unbound 10, executor 02, orchestrator / B-live / workbench, CONTEXT / 0044 / 0045 bodies)
- `docs/dev-handoff.md` (frontier pointer)
- Ticket `## Comments` / `TDD:` lines under `.scratch/change-curated-draft/issues/`, `.scratch/unbound-identity-rebind/issues/`, `.scratch/conflict-upgrade-void-plans/issues/`, `.scratch/control-plane-executor/issues/`, `.scratch/plan-step-assertion/issues/01-engine-judges-step-assertion.md` (witnessed red, Comments template traces, “不要先交空字段骨架”)
