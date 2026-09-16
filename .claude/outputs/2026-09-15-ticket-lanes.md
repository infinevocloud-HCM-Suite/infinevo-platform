# Ticket lanes — one dependency family per developer

Read-only analysis of the 89 open tickets on 2026-09-15. Nothing on GitHub was changed.
Weeks are size weights (S=1, M=2, L=3) run along the dependency graph, not estimates.

| Fact | Value |
|---|---|
| Open work | 180 size-weeks across 89 tickets |
| Critical path | 24 weeks: W-05 → 06 → 07 → 08 → 13 → 14 → 15 → 16.4 → 18 → 28 → 29.4 → 38 |
| Claimable today, no blocker | W-05, W-49, W-59, W-66, W-67, #101, #104 |
| Pinch points | W-07 (L, alone on the path wk 2-5) · W-13 (8 tickets wait on it) · W-45 (11 FE tickets wait on it) |

**Rule of the lanes:** a developer claims the next `ready` ticket *in their own lane*
first. A lane is a chain whose tickets block each other and almost nothing outside it,
so one owner means no handoffs and no cross-developer waiting. Only when a lane has
nothing `ready` does a developer claim from another lane.

## Lane A — Tenancy spine, then HRMS core  (DATA → BE)

The critical path. Whoever holds this lane holds the schedule.

| Order | Ticket | Size | Opens after | Note |
|---|---|---|---|---|
| 1 | W-05 Postgres & schemas | S | — | **ready now** |
| 2 | W-06 Flyway | S | W-05 | |
| 3 | W-07 Tenant model | L | W-06 | 3 weeks alone on the path; nothing else in this lane can run beside it |
| 4 | W-08 Tenant binding filter | M | W-07 | Unblocks W-13, W-10, W-58 |
| 5 | W-13 Employee master | L | W-08 | Unblocks 8 tickets. W-67 must be done by then (Lane C) |
| 6 | W-14 Org structure | M | W-13 | |
| 7 | W-15 Approval engine | L | W-14 | Unblocks 8 tickets |
| 8 | W-17 Holiday calendar | S | W-14 | Fill-in while W-15 is in review |
| 9 | W-16.1 → 16.2 → 16.3 → 16.4 Leave engine | S M M M | W-15 | 16.4 is on the critical path |
| 10 | W-18 Loss-of-pay policy | M | W-16, W-17 | |
| 11 | W-41 Projects & tasks | M | W-13 | Fill-in, any time after W-13 |
| 12 | W-42 Timesheets → W-43, W-44 | L S S | W-41, W-15 | |
| 13 | W-39 Attendance → W-40 Overtime | M L | W-13+W-19 / W-39+W-15+W-16 | |
| 14 | W-25 Self-service portal | M | W-16 | |

## Lane B — Runtime & infrastructure, then identity  (INFRA → BE → SEC)

Twelve weeks of work that never touches the spine. Never blocked before week 7.

| Order | Ticket | Size | Opens after | Note |
|---|---|---|---|---|
| 1 | W-49 Containerisation | M | — | **ready now.** Inherits the `images` job from W-03 |
| 2 | W-50 Azure IaC | L | W-49 | Unblocks 6 tickets |
| 3 | W-52 Queue & worker | M | W-50 | Needed by every W-29.x pay-run ticket |
| 4 | W-51 Networking & identity | M | W-50 | |
| 5 | W-54 Deployment pipeline | M | W-50 | |
| 6 | W-53 Caching | M | W-50 | |
| 7 | W-60 Observability → W-61 Alerting | M S | W-50 | |
| 8 | W-62 Backup & DR | M | W-50 | |
| 9 | W-56 Secrets | S | W-51 | |
| 10 | W-10 Identity | M | W-08 (Lane A, ~wk 7) | Start of the identity chain; retires the HRMS JWT |
| 11 | W-11 Authorization | M | W-10 | |
| 12 | W-12 Tenant, subscription & entitlement | L | W-10 | **Unblocks W-45 Shell, the whole FE** |
| 13 | W-57 Deny-by-default auth · W-58 Tenant isolation tests → W-64 Pen test | M M S | W-10 / W-08 | |
| 14 | W-24 Setup checklist · W-65 Admin console | M L | W-12 | |
| 15 | W-63 Load test | S | W-54 | Late, fill-in |

## Lane C — Independent work now, payroll later  (FE/DATA → BE)

Nothing here waits on the spine until week 10. This is the lane that keeps the third
developer busy while Lane A grinds through W-05 to W-13.

| Order | Ticket | Size | Opens after | Note |
|---|---|---|---|---|
| 1 | #101 Merge-gate hardening · #104 Gate paths never run | S S | — | **ready now.** Small, closes debt on the gate everyone uses |
| 2 | W-59 Scanning | S | — | **ready now** |
| 3 | W-66 Marketing website | L | — | **ready now.** Separate repo, no platform knowledge needed |
| 4 | W-67 Migration rules | M | — | **ready now.** Not blocked by anything, and W-13's build note says the merge rules live here — finish it before Lane A reaches W-13 |
| 5 | W-68.1 → 68.2 → 68.3 Migration engine | M L M | W-67 | Pure DATA work against the legacy dumps |
| 6 | W-09 Reference schema & seed | M | W-06 (~wk 2) | Unblocks the tax calculators |
| 7 | W-22 Audit trail · W-55 Index & query standard | M M | W-07 | |
| 8 | W-19 Pay input ledger | S | W-13 (~wk 10) | Unblocks W-39 and all of W-29 |
| 9 | W-26 Salary catalogue & structure | L | W-13 | Unblocks 6 payroll tickets |
| 10 | W-20 Notifications · W-21 Document store · W-23 Reporting | M M M | W-13 | Fill-ins, any order |
| 11 | W-32.1 → 32.4 Income tax declaration | M M M S | W-26 | |
| 12 | W-31 Statutory components · W-27 Flexible benefits | L M | W-26 | |
| 13 | W-33.1 → 33.3 Tax calculator | M L M | W-32, W-09 | |
| 14 | W-34 Proof of investment | L | W-32, W-15, W-21 | |
| 15 | W-28 Pay schedule → W-29.1 → 29.4 Pay run | S M M M M | W-18 (Lane A), W-19, W-52 (Lane B) | The one place three lanes meet, ~wk 19 |
| 16 | W-30, W-35, W-36, W-37, W-38 | M M M S M | W-29 | |
| 17 | W-70 Reconciliation → W-71 Rehearsals → W-72 Cutover · W-69 Document migration | L M M M | W-68 / W-21 | Wave 9, end of project |

## Lane D — Frontend  (FE) — **no owner exists yet**

| Order | Ticket | Size | Opens after |
|---|---|---|---|
| 1 | W-45 Shell | L | W-12 (Lane B, ~wk 12) |
| 2 | W-46.1 → 46.5 Core screens | M×5 | W-45 |
| 3 | W-47.1 → 47.5 Payroll screens | M×5 | W-45 |
| 4 | W-48 HRMS screens | L | W-45 |

Thirty-three size-weeks of FE work, all blocked until W-12 lands around week 12. Nobody
on the repo carries `skill-FE` except through W-66. Either a fourth developer joins by
week 12, or the Lane C developer moves here after W-26 and the payroll tail slips.

## Where developers would otherwise block each other

| Handoff | From → To | When | Mitigation |
|---|---|---|---|
| W-08 → W-10 | A → B | ~wk 7 | B has W-51 to W-62 to fill; no idle |
| W-06 → W-09, W-07 → W-22/W-55 | A → C | wk 2, wk 5 | C has W-66, W-67, W-68 to fill |
| W-13 → W-19, W-26 | A → C | ~wk 10 | C must have W-67 done by then |
| W-12 → W-45 | B → D | ~wk 12 | **Nobody in D** — the real gap |
| W-18 + W-19 + W-52 → W-28/W-29 | A + C + B → C | ~wk 19 | Three lanes converge; W-52 should be done by wk 7, W-19 by wk 11, so only W-18 gates it |

## How to use this without assigning anyone

Lanes are guidance, not assignment. Two ways to encode them, both label-only:

1. Add `lane-A`, `lane-B`, `lane-C`, `lane-D` labels and let each developer say which lane
   they take. The claim rule becomes "your lane first, `next` second".
2. Keep only `next`, and put the head of each lane on it: W-05, W-49, W-66/W-67, plus
   #101/#104. That is the current state of `next`.

Option 1 is 89 label edits and gives developers a standing answer to "what do I take
next" without asking. Option 2 is zero work and relies on the founder refreshing `next`
weekly.

---

# Four developers, one approver — simulated schedule (2026-09-15)

Greedy simulation over the dependency graph: each developer takes the next ready ticket
in their own lane, else the highest-priority ready ticket anywhere. One ticket in build
at a time. Sizes S=1, M=2, L=3 weeks; 182 size-weeks total, 45.5 per developer ideal.

| Result | Value |
|---|---|
| Makespan | 47 weeks (ideal 45.5) |
| Idle weeks | D1: 2 · D2: 2 · D3: 3 · D4: 3 |
| Cross-lane pickups | 14 of 91 tickets, all in the last third |
| W-13 employee master done | week 10 |
| W-12 entitlement done (opens FE) | week 16 |
| W-45 shell done | week 23 |
| First pay run (W-29.4) done | week 43 |

## The four lanes, in claim order

| Lane | Skill | Tickets in order |
|---|---|---|
| **D1 Core → HRMS** | DATA, BE | W-05 · W-06 · W-07 · W-08 · **W-13** · W-14 · W-17 · **W-15** · W-16.1 · W-16.4 · W-18 · W-41 · W-39 · W-42 · W-40 · W-25 · W-43 · W-44 |
| **D2 Infra → identity → security** | INFRA, BE, SEC | W-49 · W-50 · W-52 · W-51 · W-54 · **W-10** · **W-12** · W-11 · W-57 · W-58 · W-53 · W-60 · W-62 · W-56 · W-61 · W-64 · W-24 · W-65 · W-63 · W-72 |
| **D3 Payroll** | BE | #101 · #104 · W-59 · W-09 · W-22 · W-55 · W-19 · **W-26** · W-32.1-.4 · W-31 · W-33.1-.3 · W-27 · W-34 · W-28 · W-29.1-.4 · W-36 · W-30 · W-35 · W-37 · W-38 |
| **D4 Website → migration → FE** | FE, DATA | W-66 · **W-67** · W-68.1-.3 · W-20 · W-21 · W-23 · W-16.2 · W-16.3 · **W-45** · W-46.1-.5 · W-47.1-.5 · W-48 · W-69 · W-70 · W-71 |

Two ordering choices matter and are deliberate:

- **D2 takes W-10 → W-12 the moment W-08 lands (week 7)**, ahead of the remaining infra.
  Left in wave order, identity started at week 19 and the FE shell slipped to week 27.
- **W-16.2 and W-16.3 sit in D4, not D1.** The four leave-engine parts need only W-15,
  so splitting them across two developers brings W-18, and with it the pay run, forward
  by two weeks.

## Week-by-week (start week : ticket; * = picked from another lane)

D1  0:W-05 1:W-06 2:W-07 5:W-08 7:W-13 10:W-14 12:W-17 13:W-15 16:W-16.1 17:W-16.4 19:W-41 21:W-18 23:W-39 25:W-42 28:W-40 31:W-25 33:W-43 34:W-44 35:W-47.2* 37:W-28* 38:W-29.1* 40:W-69* 43:W-36* 45:W-72*
D2  0:W-49 2:W-50 5:W-52 7:W-51 9:W-54 11:W-10 13:W-12 16:W-11 18:W-57 20:W-58 22:W-53 24:W-60 26:W-62 28:W-56 29:W-61 30:W-64 31:W-24 33:W-65 36:W-63 37:W-47.4* 39:W-29.2* 41:W-29.4* 43:W-71* 45:W-37*
D3  0:#101 1:#104 2:W-59 3:W-09 5:W-22 7:W-55 9:W-68.3* 11:W-19 12:W-26 15:W-32.1 17:W-32.2 19:W-32.3 21:W-32.4 22:W-31 25:W-33.1 27:W-33.2 30:W-33.3 32:W-27 34:W-34 37:W-47.5* 39:W-29.3 43:W-30 45:W-38
D4  0:W-66 3:W-67 5:W-68.1 7:W-68.2 10:W-20 12:W-21 14:W-23 16:W-16.2 18:W-16.3 20:W-45 23:W-46.1 25:W-46.2 27:W-46.3 29:W-46.4 31:W-46.5 33:W-47.1 35:W-47.3 37:W-48 40:W-70 43:W-35*

## The approver's load

One approver gates every spec (step 7) and every merge. Four developers finishing
tickets of 1-3 weeks means roughly 2 specs and 2 merges a week, every week. If either
waits more than a day the idle weeks above double, because every lane is a chain.
