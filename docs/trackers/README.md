# Trackers

> Four files, one per kind of work. **These trackers are the source of truth** for who
> owns what and where it stands. GitHub tickets are no longer used (2026-09-24). Last refreshed: **2026-09-24**, against `origin/main` at `1d1123a`.

## The overall picture

| Tracker | Tickets | Done (on `main`) | Of which proven live | In flight | Ready | Blocked |
|---|---|---|---|---|---|---|
| [DEV](DEV-TRACKER.md) — streams A–F, product items | 74 | 15 | 15 | 1 | 13 | 45 |
| [INFRA](INFRA-TRACKER.md) — containers, CI, Azure, security | 18 | 13 | **4** | 1 | 3 | 1 |
| [HARNESS](HARNESS-TRACKER.md) — the build process | 12 | 9 | 9 | 0 | 3 | 0 |
| [MIGRATION](MIGRATION-TRACKER.md) — stream I | 8 | 0 | 0 | 0 | 0 | 8 |
| **Total** | **112** | **37** | **28** | **2** | **19** | **54** |

Rows are build pieces: where a ticket's parts ship separately (`W-13.1`, `.2`, `.3`) each
is a row. GitHub itself holds 113 tickets — 40 closed, 73 open — six of
them docs tickets, listed in the harness tracker. **By weighted effort the build is about 27%
done** (migration excluded).

## Blocking everyone today

| What | Effect |
|---|---|
| **GitHub Actions is not running** — "recent account payments have failed or your spending limit needs to be increased", every run since 2026-09-24 07:29 UTC | CI, `Tickets` (claiming, unblocking), `Deploy` and the security rescan all fail without starting. `check-done.mjs` gate 10 reads the CI conclusion, so **nothing can pass `/merge`** until billing is fixed |
| **Azure dev is deployed but nothing starts** | All four container apps exist in `rg-infinevo-dev` (image `git-26c6078`, 2026-09-23) and every revision fails activation: Keycloak gets no database password, `app` and `worker` fail on a missing JWT key-set setting, `web` fails its startup probe. The `Deploy` workflow itself has never succeeded — the estate was deployed by hand |

## How work moves

| Step | Who | Tracker change |
|---|---|---|
| Spec written, `/plan-feature W-nn <dev>` | Founder | Row → **Assigned**, Owner = developer |
| Build starts, `/develop W-nn` on `dev-<name>` | Developer | Row → **In flight — dev-<name>**. One row In flight per developer |
| Gates and review pass, `/merge W-nn` | Developer | Row → **Ready to merge** |
| Merged to `main` | Founder | Row → **Done**; rows it unblocks → **Ready** |
| Seen working in Azure | Whoever checks | Row → **Done — proven live** |

## The status values

| Status | Means |
|---|---|
| **Done** | The code is on GitHub `origin/main`. **Nothing else counts as done** — a closed ticket, a green branch or a local commit is not |
| **Done — not live** | On `main`, but never run in Azure. Every Azure and pipeline ticket is here |
| **In flight** | A branch is pushed to GitHub and not merged |
| **Ready** | Blockers cleared; not yet assigned |
| **Assigned** | The founder has given it to a developer; not started |
| **Ready to merge** | Gates and review passed on `dev-<name>`; waiting for the founder |
| **Blocked** | Waits on another ticket |

| Column | Where it comes from |
|---|---|
| **Spec** | `written` = the spec is in `docs/target-state/features/` |
| **Owner** | The developer the founder assigned. `—` means unassigned |
| **Built by** | The author of the commit on `main` |

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../target-state/09-build-order.md)
- Live project state and the queue: `.claude/work/active-work.md`
