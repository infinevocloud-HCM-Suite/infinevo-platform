# Harness Tracker

> The build process itself — skills, hooks, agents, the merge gate and ticket automation.
> Not product work and not infrastructure: this is how work gets done.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-09-22**.

## Summary

| | Count |
|---|---|
| Harness tickets closed | 5 |
| Open gate defects | 3 |
| Skills on `main` | 5 |
| Hooks on `main` | 2 |
| Agents on `main` | 3 |

---

## 1. What the harness is, today

| Piece | Files | What it does |
|---|---|---|
| Skills | `.claude/skills/` — `plan-feature`, `develop`, `merge`, `analyze`, `sync-docs` | The four-command ticket flow, plus a question-answering skill and the only sanctioned route into `docs/` |
| Hooks | `.claude/hooks/` — `guard-edit.mjs`, `verify-app.mjs` | `guard-edit` blocks writes to `legacy/`, `docs/`, `*.properties` and `.env*`; `verify-app` checks a build folder after an edit |
| Agents | `.claude/agents/` — `explorer`, `implementer`, `reviewer` | Read-only investigator · single-module code writer · independent reader with no edit tools |
| Scripts | `.claude/scripts/` — `check-done.mjs`, `prune-outputs.mjs` | The machine-checked definition of done, and output housekeeping |
| Workflow | `.github/workflows/tickets.yml` | Ticket claiming, unblocking and the stale sweep |
| State | `.claude/work/active-work.md` | Live project state. Read before starting any task |

### The flow

| Command | What it does | Stops for the founder? |
|---|---|---|
| `/plan-feature W-nn` | Writes the spec | **Yes** — the one gate before code |
| `/develop W-nn` | Builds it, runs its own checks, fixes what it finds, pushes the branch | No |
| `/merge W-nn` | Gates, one independent read, then the founder merges | **Yes** |
| `/analyze`, `/sync-docs` | Questions and doc drift | No |

**Two stops, no review rounds.** A defect found is fixed in the commit that caused it —
it does not become a numbered finding, a report file or a ticket.

---

## 2. Closed harness tickets

| # | Ticket | What it did | Status |
|---|---|---|---|
| #97 | Development process as skills, with an enforced merge gate | Turned the process into skills instead of prose | **done** |
| #100 | `docs/` files that are not ticket specs have no route through the merge gate | Gave them one — the `docs-<slug>` branch | **done** |
| #107 | Self-service ticket claiming | Work is pulled, not handed out. `tickets.yml` enforces one owner, a WIP limit of 2, unblocks tickets when their blockers close, and sweeps stale claims nightly | **done** |
| #139 | `W-06` code reached `main` inside a commit approved as documentation-only | A gate defect. Thirteen code files travelled inside a commit whose message said "Documentation only. No code changed." | **done** |
| #138 | Migrate job reports success while applying nothing when the image is stale | `compose up migrate` without `--build` found zero scripts and exited 0 | **done**, closed with `W-54` |

Also closed: #112 (`W-05` test classpath hardening, CI never ran the stack), #117
(Testcontainers did not detect Docker — `DatabasePrivilegesIT` skipped silently while the
build stayed green), #136 and #137 (`W-07` follow-ups: no test ran V001 through Flyway;
CI gates were per-file greps, so a two-table script could ship an unprotected table).

The #117 fix is why the backend suite went from 46 passing with 34 skipping to **100
passing with none skipped**.

---

## 3. Open gate defects

| # | What | Size |
|---|---|---|
| #101 | Merge-gate hardening — three defects found while building `W-03` | S |
| #104 | Three merge-gate paths have never been executed, and the harness runs only if someone remembers. Not in CI | S |
| #125 | `review-spec/SKILL.md` was softened on a feature branch and pushed to `main` without a PR | — |

**These three share a shape.** The gate is the thing that catches everything else, and
parts of it have never run. #101 and #104 unblock nothing, which is exactly why they keep
getting skipped.

---

## 4. Doc drift to fix

| Document | Says | Actually |
|---|---|---|
| `CLAUDE.md:92` | "No push to `main` carrying `code/` outside `/merge` — `guard-merge` hook" | The `guard-merge` hook was **removed** in `cdc7070`. Only `guard-edit` and `verify-app` remain |
| `CONTRIBUTING.md:246` | Lists the hooks | Same — re-check after the harness cut |
| #125 | `review-spec` skill | The skill no longer exists; the process cut left five skills |

Route: the `sync-docs` skill, on a `docs-<slug>` branch.

---

## 5. Rules a machine checks

Listed so you know what will reject you, not as prose to comply with by hand.

| Rule | Enforced by |
|---|---|
| `tenant_id` and a row-level security policy on every new table outside `reference` | CI, `check-done.mjs` |
| Flyway for every schema change; `ddl-auto` set nowhere | CI, `check-done.mjs` |
| `Money` or `BigDecimal` for money, never `double` or `float` | CI, `check-done.mjs` |
| No module references another module — only `core` | `maven-enforcer` |
| No write to `legacy/`, `docs/` (except the branch's own spec), `*.properties`, `.env*` | `guard-edit` hook |

### Branch naming, because the gate reads it

A `W-nn` branch may change only its own ticket spec. A `docs-<slug>` branch may change
only `docs/`. The test is `/w[-_. ]?(\d{1,4})/i`, unanchored, so `W-04-x`, `w-04-x`,
`feature/W-04-x` and `W04-x` all read as ticket W-04 — and so does `flow12`. It
over-matches on purpose: the gate names the ticket it thinks you are on, and the fix is
to rename the branch.

---

## 6. Rules nothing can check

1. **Upstream remotes are read-only.** The four origin repositories are production
   source. Changes flow one way: upstream → this repository.
2. **Repository state beats chat memory.** If a conversation and the repository disagree,
   the repository is right.
3. **Read `.claude/work/active-work.md` before starting** any task.

---

## Related

- Ways of working: [../target-state/11-ways-of-working.md](../../../docs/target-state/11-ways-of-working.md)
- Setup and the developer loop: `CONTRIBUTING.md`
- Live project state and the queue: `.claude/work/active-work.md`
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [DEV-TRACKER.md](DEV-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
