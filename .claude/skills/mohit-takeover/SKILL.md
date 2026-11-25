---
name: mohit-takeover
description: Take over mohit's eight payroll tickets (W-88, W-89, W-90, W-91, W-103, W-94, W-107, W-108) and drive them phase by phase to main. Picks up where the last session stopped. Use when the founder types /mohit-takeover or says "continue mohit work".
---

# mohit-takeover

Invoke as `/mohit-takeover` (resume at the next open step) or `/mohit-takeover <phase>`
(for example `/mohit-takeover 1`).

Mohit (git author `mbirla-infinevocloud`) left eight tickets half done. Claude finishes
them, one ticket at a time, using the normal `/plan-feature`, `/develop` and `/merge`
loop. This file is the plan. The progress lives in the memory note
`mohit-takeover-progress.md`. **Repo state beats both.**

## Before anything

1. Read `.claude/work/active-work.md` and the memory note `mohit-takeover-progress.md`.
2. Re-read the tracker rows from file: `docs/trackers/DEV-TRACKER.md` (mohit's queue is
   near line 91, his rows near lines 921-951). Never trust an earlier answer.
3. `git fetch`, then check that these branches still point where the table says. If one
   has moved, someone else is working on it: stop and tell the founder.

| Branch | Commit | What is on it |
|---|---|---|
| `origin/dev-mohit` | `49c4c2d7` | W-88 bank file: code and spec. Merges cleanly, CI green |
| `origin/feature/mohit-branch-3-w90-fnf` | `d295aa11` | W-89, W-90 code, all other specs. Base `761cad1be` |
| `origin/feature/mohit-branch-2-w89-specs` | — | Fully inside branch-3. Ignore |

4. Find the first phase below that is not marked done in the progress note, and resume there.
   Inside a phase, check `git status` in its worktree first.

## Founder decisions (gate for everything)

Ask all of them in one go, the first time only. Record each answer in the progress note.

| # | Question | Blocks |
|---|---|---|
| 1 | Reassign mohit's 8 tickets to Claude in the tracker? | all |
| 2 | Keep mohit's code as the base, or rebuild from the spec? | Phases 1, 3, 4 |
| 3 | Who owns setting an employee to TERMINATED: core (exit) or payroll (F&F)? | Phase 4 |
| 4 | The 5 CA questions (24Q layout version, Annexure II rules, 10(10AA), gratuity cap, notice pay tax) | Phases 3, 4 |
| 5 | Bank file format: generic CSV, or one named bank's upload layout? | Phase 1 |
| 6 | W-94 prices | Phase 7 |

Decisions 1-3 are needed to start. 4 and 5 can follow. Without 1, do nothing.

## Rules that apply to every phase

- One branch per ticket, `dev-claude-<ticket>`, cut from `origin/main`, in its own
  worktree under `D:\tmp\wt\`. Never work on mohit's branches; cherry-pick or copy from them.
- Migrations take the next free global number above main's highest (main was at V222 on
  2026-11-24; check again). Every new table gets `tenant_id` and an RLS policy
  (pattern: `payroll/V190__tds_switches.sql`). Role grants follow the V189 pattern.
- New actions go in the catalogue (`reference/V020__action.sql` pattern), with
  `@RequiresModule` and `@RequiresAction`. Approve and pay are separate actions.
- Frontend: `PageHeader`, `MoneyText`, `shared/ui/format.js`, `StatusTag`, `LoadError`
  with Retry, `EmployeePicker`, `useCan`. Menu items come from `PayrollNavigation`.
  The e2e suite walks every sidebar screen, so a new screen must load clean.
- Tests hit the real database (Testcontainers), not only mocks.
- Never turn on TDS for the migrated tenant ([[company-tds-stays-off]]).
- Founder merges. Leave changes uncommitted unless the step is a branch push the
  founder asked for. Tracker rows ride with the branch and change to Done only on merge.
- Update the progress note at the end of every step: what is done, commit, next step.

## Phase 0 — what not to carry over from branch-3

Do not copy any of these when taking code from `feature/mohit-branch-3-w90-fnf`:

| Change on branch-3 | Do instead |
|---|---|
| `code/backend/pom.xml` Spring Boot 3.5.16 → 3.4.2 | Keep main's version |
| `.mvn/wrapper/maven-wrapper.properties` Maven 3.9.11 → 3.9.9 | Keep main's |
| `.trivyignore`, `code/.trivyignore`: 16 CVEs suppressed | Drop all |
| Migrations V192, V193, V194, `core/V195__form24q_actions.sql`, V196 | Renumber above main's highest |
| `TaxRecalculationServiceImpl.recalculate` gets `REQUIRES_NEW` | Drop it (clashes with D-147 on main) |
| Tracker rows rewritten to "built" / "specced" | Keep main's rows |

## Phase 1 — W-88 bank transfer file

Base: `origin/dev-mohit`. Fix, then `/merge W-88`.

| Fix | Where |
|---|---|
| The omitted list holds the included employees; list the left-out ones with name and reason, and show them on screen | `BankFileServiceImpl.java:240-245` |
| Check IFSC format (`^[A-Z]{4}0[A-Z0-9]{6}$`) | bank file service |
| Off-cycle runs get their own file name and narration | bank file service |
| File format per decision 5; no BOM unless the bank needs it | bank file service |
| Account numbers must not turn into numbers in Excel (quote or text-prefix) | file writer |
| Audit every download | controller |
| Integration test against the real database | `app` IT |
| Add the template's Database, Risks and Rollback sections to the spec | W-88 spec |

## Phase 2 — specs

Write or fix each spec with `/plan-feature`. Start from mohit's spec on branch-3.
Every spec needs the template's Tests, Risks and Rollback sections, and must name
only tables and actions that exist or that it creates.

| Spec | Action |
|---|---|
| W-90.3, W-94.1, W-103, W-108.1 | Rewrite |
| W-89.1-.4, W-90.1/.2/.4, W-91, W-94.2, W-107, W-108.2 | Fix |

The founder approves each spec before its code starts.

## Phase 3 — W-89 TDS challans and Form 24Q

Rebuild the backend from the spec; mohit's code is a reference only.

| Must be right | Note |
|---|---|
| 24Q text file follows the NSDL layout and passes FVU | Use the published file format; test with the FVU tool |
| Annexure II gross salary is gross, not GTI | Mohit takes deductions off twice |
| Missing TDS record fails clearly; never write made-up figures | No rollback-only trap |
| No-PAN deductee code is `C` (206AA) | Mohit used `Y` |
| Allocation checks: amounts add up, one quarter, no double allocation | |
| A challan is locked once allocated; something sets it filed | |
| Split challans are kept (no `put` overwrite) | |
| CSV cells escape `= + - @` | Formula injection |
| A missing pay date is an error, never `now()` | |

Frontend: read the fields the server really sends (not `missingPanCount`,
`totalDeductees`, `totalGrossPaid`, `filed`); allocate path is `/allocate`;
menu item from `PayrollNavigation`; `useCan` gating; formatted dates and labels; `LoadError`.

## Phase 4 — W-91, then W-90 full and final settlement

W-91 first, since W-90 needs it. Then rebuild W-90 from the spec.

| Must be right | Note |
|---|---|
| Approved F&F lines reach the pay run and the payslip | Today only the status changes |
| The employee becomes TERMINATED | Owner per decision 3 |
| The exit month cannot be paid twice | |
| TDS is worked out, not 0 | |
| Notice pay direction right for both employer- and employee-ended exits, with a waive step | |
| Edit, cancel and recompute exist | |
| Server sets status and approver, never the client | |
| Approve and pay are separate permissions | |
| PF, ESI, PT come from the statutory setup, not hardcoded | |
| Gratuity: 15/26, 5-year rule, 6-month round-up, ₹20 lakh cap, in its own service | |
| 10(10AA): ₹25 lakh lifetime cap, 10-month average; encashment days capped; leave balance reduced | |
| Bad input returns 4xx, not 500 | |

Frontend: `EmployeePicker`, not a raw UUID; names, not UUIDs, in lists; a negative
net payable is shown as owed, not green.

## Phase 5 — W-103

Build from the approved spec.

## Phase 6 — W-107, then W-108

Build from the approved specs.

## Phase 7 — W-94

Only after decision 6 (prices).

## When a phase ends

Update the progress note: phase done, merge commit, next phase. Tell the founder in
one table what merged and what is next.
