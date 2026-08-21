# Issue tracker: Local Markdown

Issues and specs for this repo live as markdown files. **Do not use `gh issue create` from Cloud Agents** — the Cloud `gh` CLI is read-only. GitHub Issues are not the tracker for `/to-spec` / `/to-tickets` / `/triage` / `/wayfinder`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`
- **Canonical spec** (human + AGENTS.md index): `docs/specs/<feature-slug>.md`
- Tracker copy of the spec: `.scratch/<feature-slug>/spec.md` (full copy, or a one-line pointer to `docs/specs/<feature-slug>.md`)
- Implementation issues: one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01` — never a single combined tickets file
- Triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings). ArchOps also uses `done` for completed tickets.
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

Already published:

| Feature | Canonical spec | Tickets |
|---|---|---|
| vertical-slice-mvp | `docs/specs/vertical-slice-mvp.md` | `.scratch/vertical-slice-mvp/issues/` (01–13 **done** — do not re-open) |
| change-curated-draft | `docs/specs/change-curated-draft.md` | `.scratch/change-curated-draft/issues/`（**01–05 TDD-done**；06 `ready-for-agent`；frontier = **06**；开工贴 `docs/implement-change-curated-draft-06-prompt.md`） |

## When a skill says "publish to the issue tracker"

- **Spec**: write `docs/specs/<feature-slug>.md` and `.scratch/<feature-slug>/spec.md`. Update `docs/dev-handoff.md` and the AGENTS.md reading list if this is the current frontier.
- **Tickets**: create `.scratch/<feature-slug>/issues/<NN>-<slug>.md` (creating the directory if needed). Apply `Status: ready-for-agent`.

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly. Frontier tickets are **ready-for-agent** with all `Blocked by` tickets `done`. When two tickets are unblocked, implement the **lowest number**.

## Cloud / ArchOps overrides

- AGENTS.md wins over this file on process: **one ticket at a time** (lowest number when two are unblocked); HTTP API is the acceptance seam; `/implement` drives `/tdd` (**red → green → refactor**, overlay [`tdd.md`](tdd.md)); do not revive Vue/JPA/Neo4j/Maven/LangChain.
- `/implement` still implements a single unblocked frontier ticket, not a whole spec.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md` — the Notes / Decisions-so-far / Fog body.
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`, numbered from `01`, with the question in the body. A `Type:` line records the ticket type (`research`/`prototype`/`grilling`/`task`); a `Status:` line records `claimed`/`resolved`.
- **Blocking**: a `Blocked by: NN, NN` line near the top. A ticket is unblocked when every file it lists is `resolved`.
- **Frontier**: scan `.scratch/<effort>/issues/` for files that are open, unblocked, and unclaimed; first by number wins.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append a context pointer (gist + link) to the map's Decisions-so-far in `map.md`.
