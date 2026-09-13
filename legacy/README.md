# Legacy — frozen reference

**These four applications are frozen.** They are here to be read, not changed.

They are the production systems the target-state platform replaces. You read them
to understand how something works today, you copy logic out of them into the new
modules, and you cite their `file:line` in your feature spec so the reviewer can
check the logic was carried over rather than reinvented.

---

## The rule

| | |
|---|---|
| **Read them** | Yes. That is what they are for |
| **Port logic out of them** | Yes. Cite the `file:line` in your spec |
| **Edit anything under `legacy/`** | **No.** A change here is meaningless — it is a copy, it is not deployed, and it will be deleted |
| **Push to the original repos** | **No.** They are live production. Hard rule 5 |

If you believe something in `legacy/` must change, it is a production defect.
Raise it with the founder. It does not get fixed here.

---

## What was copied

Snapshot taken **2026-09-13** from the branch tip of each source repo. No git
history — the full history remains in the source repos, which are untouched.

| Folder | Source repo | Branch | Commit | Last change |
|---|---|---|---|---|
| `HRMS_Backend` | `infinevocloud-hrms/HRMS_Backend` | `main` | `d984c64` | 2026-06-16 |
| `HRMS_Frontend` | `infinevocloud-hrms/HRMS_Frontend` | `main` | `c72116c` | 2025-12-17 |
| `Payroll-Bend-SBoot` | `infinevocloud-hrms/Payroll-Bend-SBoot` | **`taxation`** | `39b37d6` | 2026-09-09 |
| `Payroll-Fend-react` | `infinevocloud-hrms/Payroll-Fend-react` | **`employee`** | `053ca62` | 2026-09-10 |

> Note the branches. The two Payroll repos are **not** on `main`. `taxation` and
> `employee` are the live branches. Reading `main` on those repos would show you
> code that is not in production.

### Excluded from the copy

| Excluded | Why |
|---|---|
| `HRMS_Backend/target/` | 134 files of committed build output, ~49 MB. Regenerable, and not reference material |
| `node_modules/` | Dependencies. Run `npm install` inside the folder if you need to run a frontend |
| `.git/` | This is a snapshot, not a clone |

Each folder keeps its own `CLAUDE.md` with that app's conventions and build
commands.

---

## Reading them

Start from [`legacy/docs/FEATURE_MAP.md`](../legacy/docs/FEATURE_MAP.md) — it maps a feature to
the files that implement it across all four apps. Going in cold and grepping is
slower.

Two hazards, both documented in [`docs/CONVENTIONS.md`](../docs/CONVENTIONS.md):

1. **The package-name typos are real and load-bearing** — `timeshhet/`,
   `leaveAndAttedance/`, `EmployyePortalContoller.java`. They are not mistakes to
   fix, they are the actual names.
2. **Entities that look duplicated are not all dead.** Nothing in the "Retired"
   list in `docs/target-state/02-data-model.md` §7 should be assumed unused
   without its own check.

---

## When this folder is deleted

Per work item, not all at once. Once every ticket that depends on an application
has merged, that folder is removed. The whole of `legacy/` goes at cutover.

The source repos in `infinevocloud-hrms` stay as the historical record.
