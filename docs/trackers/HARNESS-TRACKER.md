# Harness Tracker

> The build process itself — skills, hooks, agents, the merge gate and ticket automation.
> **GitHub is authoritative. Done means on `origin/main`, nothing else.** Legend: [README.md](README.md).
> Last refreshed: **2026-09-24**, against `origin/main` at `1d1123a`.

## Summary

| | Count |
|---|---|
| Harness tickets | 12 — **9 done**, 3 open, 0 blocked |
| Docs tickets (drift fixes) | 6 — all done |
| Skills on `main` | **4** — `plan-feature`, `develop`, `merge`, `analyze` |
| Hooks on `main` | **1** — `guard-edit` |
| Agents on `main` | 3 — `explorer`, `implementer`, `reviewer` |
| `tickets.yml` | **Removed** (`a1b72de`) — GitHub tickets are no longer used |

---

## 1. What the harness is, today

| Piece | Files | What it does |
|---|---|---|
| Skills | `.claude/skills/` — `plan-feature`, `develop`, `merge`, `analyze` | Write and assign a spec · build it · gate and merge it · answer questions |
| Hooks | `.claude/hooks/guard-edit.mjs` | Blocks writes to `legacy/`, `*.properties` and `.env*`. `docs/` is writable |
| Agents | `.claude/agents/` | Investigator · single-module implementer · independent reviewer |
| Scripts | `.claude/scripts/check-done.mjs` | The machine-checked definition of done |
| State | `.claude/work/active-work.md` | Live project state |

| Command | What it does | Stops for the founder? |
|---|---|---|
| `/plan-feature W-nn <dev>` | Founder writes the spec into `docs/target-state/features/` and assigns it | Founder runs it |
| `/develop W-nn` | Developer builds on `dev-<name>`, tests, pushes | No |
| `/merge W-nn` | Five gates, one independent review, then the founder merges | **Yes — the only stop** |

---

## 2. Harness tickets

| # | Ticket | Status |
|---|---|---|
| #97 | Development process as skills, with an enforced merge gate | **Done** |
| #100 | `docs/` files that are not ticket specs have no route through the gate | **Done** |
| #107 | Self-service ticket claiming — `tickets.yml` | **Removed** 2026-09-24 — the founder assigns in the tracker |
| #112 | `W-05` follow-ups — test classpath, CI never ran the stack | **Done** |
| #117 | Testcontainers did not detect Docker — tests skipped under a green build | **Done** (with `W-09`) |
| #136 | `W-07` follow-up — no test ran V001 through Flyway | **Done** (with `W-09`) |
| #137 | `W-07` follow-up — per-file CI greps missed a two-table script | **Done** (with `W-09`) |
| #138 | Migrate job reported success while applying nothing | **Done** (with `W-54`) |
| #139 | `W-06` code reached `main` inside a docs-only commit | **Done** |
| #101 | Merge-gate hardening — three defects from `W-03` | Ready (`next`) |
| #104 | Three gate paths never executed; harness not in CI | Ready (`next`) |
| #125 | `review-spec` skill changed on a branch and pushed to `main` without a PR | Ready |

**Docs tickets, all done:** #114 (`W-05` drift), #120 (`W-49` drift), #127 (Storage Queue,
`D-50`), #132 (`W-51` as built), #142 (`W-07` as built), #143 (active-work for `W-07`).

---

## 3. Process gaps seen in the data

| Gap | Evidence |
|---|---|
| Owners not recorded | 13 merged tickets have no GitHub assignee; on four the assignee is not who built it |
| Parents closed early | #12, #14, #15, #26 closed while a part is still open |
| Trackers drift | These files were two days stale and never merged. They are hand-maintained |

---

## 4. Rules a machine checks

| Rule | Enforced by |
|---|---|
| `tenant_id` and a row-level security policy on every new table outside `reference` | CI, `check-done.mjs` |
| Flyway for every schema change; `ddl-auto` set nowhere | CI, `check-done.mjs` |
| `Money` or `BigDecimal` for money, never `double` or `float` | CI, `check-done.mjs` |
| No module references another module — only `core` | `maven-enforcer` |
| No write to `legacy/`, `*.properties`, `.env*` | `guard-edit` hook |

---

## Related

- Ways of working: [../target-state/11-ways-of-working.md](../target-state/11-ways-of-working.md)
- Setup and the developer loop: `CONTRIBUTING.md`
- Live project state and the queue: `.claude/work/active-work.md`
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [DEV-TRACKER.md](DEV-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
