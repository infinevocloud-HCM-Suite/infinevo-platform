# Trackers

> Four files, one per kind of work. **These trackers are the source of truth** for who
> owns a ticket and where it stands. `/plan-feature`, `/develop` and `/merge` update the
> row; GitHub carries no status of its own.
> Last refreshed: **2026-09-25**, against `main` `c27ea24`.

| Tracker | Covers |
|---|---|
| [INFRA-TRACKER.md](INFRA-TRACKER.md) | Containers, GitHub Actions, Azure, security, operations — streams G and H |
| [DEV-TRACKER.md](DEV-TRACKER.md) | The product itself — foundation, data, core, payroll, HRMS, frontend — streams A to F |
| [HARNESS-TRACKER.md](HARNESS-TRACKER.md) | The build process itself: skills, hooks, merge gate, ticket automation |
| [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md) | Moving live data out of the four frozen applications — stream I |

## The three status columns

Every row carries three, because a ticket can be closed and the thing still not work.

| Column | Values | Means |
|---|---|---|
| **Spec** | `—` · `draft` · `approved` | Founder approval is the gate before any code (hard rule 1) |
| **Code** | `—` · `in flight` · `on main` | Where the code is. `in flight` means a branch exists |
| **Feature** | `—` · `code done` · `done` | `code done` = merged and the build is green. `done` = proven to work for real |

**`code done` is not `done`.** Everything Azure is `code done`: the Bicep builds, the
scripts parse, nothing has ever been deployed (#124). A backend ticket reaches `done`
when its tests pass against a real database; an Azure ticket reaches `done` when it has
run in a live environment.

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../target-state/09-build-order.md)
- Live project state and the queue: `.claude/work/active-work.md`
