# 10 — Scoping & Assignment

> All 72 work items sized and skill-tagged, so they can be handed to people.
> **Relative sizes, not dates.** `MANAGEMENT_SUMMARY_AND_PLAN.md` holds the calendar.
>
> Full specs are written **just before an item starts**, into `docs/target-state/features/W-nn-<slug>.md`
> from `docs/target-state/features/TEMPLATE.md`. This document is what you assign from, not what you build from.

---

## 1. How to read the columns

| Size | Meaning |
|---|---|
| **S** | A few days of focused work for one person |
| **M** | About one to two weeks |
| **L** | Two to four weeks |
| **XL** | **Do not assign as one item. Split first** — see §4 |

| Skill | Means |
|---|---|
| `BE` | Backend, Java and Spring |
| `FE` | Frontend, React and Ant Design |
| `DATA` | Schema, migrations, SQL, row-level security |
| `INFRA` | Azure, containers, pipelines |
| `SEC` | Security engineering |
| `ANY` | No specialism needed |

**Ready** means every prerequisite is already done, so it can be picked up today.

---

## 2. Assign these first

With a lead and two part-time developers, this is the opening move. All three are independent.

| Who | Item | Size | Why first |
|---|---|---|---|
| **Lead** | `W-01` Repository skeleton | M | Sets the module graph and conventions everyone else inherits. Nobody should write code before it exists |
| **Dev-1** | `W-49` → `W-50` Containers, then Azure | M → L | Longest lead time, zero dependency on product decisions. **Opens the moment `W-01` merges**, not on day one |
| **Dev-2** | `W-66` Marketing website | L | Fully independent, needs no platform knowledge, and is visible progress for you |

**After `W-01` lands**, three more open at once: `W-02` local stack, `W-03` pipeline,
`W-04` test foundation. Give `W-03` to whoever finishes first — it is small and everything
downstream depends on the gates being on early.

---

## 3. The full sheet

### Stream A — Foundation

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-01` | Repository & module skeleton | M | BE | — | ✅ |
| `W-02` | Local development stack | M | INFRA | `W-01` | |
| `W-03` | Build & test pipeline | S | INFRA | `W-01` | |
| `W-04` | Test foundation | M | BE | `W-01` | |

### Stream B — Data foundation

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-05` | Postgres & four schemas | S | DATA | `W-01` | |
| `W-06` | Flyway | S | DATA | `W-05` | |
| `W-07` | Tenant model, RLS, build check | **L** | DATA | `W-06` | |
| `W-08` | Tenant binding filter | M | BE | `W-07` | |
| `W-09` | Reference schema & seed | M | DATA | `W-06` | |

> `W-07` is the largest data item: policies across ~115 tables plus the build check that
> enforces the rule. It is also the single highest-value piece of work in the project.

### Stream C — Core platform

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-10` | Identity | M | BE | `W-08` | |
| `W-11` | Authorization | M | BE | `W-10` | |
| `W-12` | Subscription & entitlement | **L** | BE | `W-10` | |
| `W-13` | Employee master ⚠️ merge | **L** | BE | `W-08` | |
| `W-14` | Org structure & reporting hierarchy | M | BE | `W-13` | |
| `W-15` | Approval engine | **L** | BE | `W-14` | |
| `W-16` | Leave engine ⚠️ merge | **XL** | BE | `W-15` | |
| `W-17` | Holiday calendar | S | BE | `W-14` | |
| `W-18` | Loss-of-pay & working-day policy | M | BE | `W-16`, `W-17` | |
| `W-19` | Pay input ledger | S | BE | `W-13` | |
| `W-20` | Notifications | M | BE | `W-13` | |
| `W-21` | Document store | M | BE | `W-13` | |
| `W-22` | Audit trail | M | BE | `W-07` | |
| `W-23` | Reporting & export | M | BE | `W-13` | |
| `W-24` | Setup checklist & invitations | M | BE | `W-12` | |
| `W-25` | Employee self-service portal | M | BE | `W-16` | |
| `W-39` | Attendance & overtime capture (basic) | M | BE | `W-13`, `W-19` | |

### Stream D — Payroll module

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-26` | Salary catalogue & structure | **L** | BE | `W-13` | |
| `W-27` | Flexible benefit plan | M | BE | `W-26` | |
| `W-28` | Pay schedule | S | BE | `W-18` | |
| `W-29` | Pay run ⚠️ merge | **XL** | BE | `W-28`, `W-19`, `W-52` | |
| `W-30` | Off-cycle & one-time payouts | M | BE | `W-29` | |
| `W-31` | Statutory components | **L** | BE | `W-26` | |
| `W-32` | Income tax declaration | **XL** | BE | `W-26` | |
| `W-33` | Tax calculator | **XL** | BE | `W-32`, `W-09` | |
| `W-34` | Proof of investment | **L** | BE | `W-32`, `W-15`, `W-21` | |
| `W-35` | Reimbursements & deductions | M | BE | `W-15`, `W-29` | |
| `W-36` | TDS, payslips & statements | M | BE | `W-29`, `W-33` | |
| `W-37` | Payroll dashboard | S | BE | `W-29` | |
| `W-38` | Prior payroll import | M | BE | `W-29` | |

### Stream E — HRMS module

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-40` | Clock attendance & request workflows | **L** | BE | `W-39`, `W-15`, `W-16` | |
| `W-41` | Projects, tasks, assignments | M | BE | `W-13` | |
| `W-42` | Timesheets | **L** | BE | `W-41`, `W-15` | |
| `W-43` | Timesheet reminders | S | BE | `W-42`, `W-20` | |
| `W-44` | HRMS dashboards | S | BE | `W-42` | |

### Stream F — Frontend

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-45` | Frontend shell | **L** | FE | `W-12` | |
| `W-46` | Core screens | **XL** | FE | `W-45` | |
| `W-47` | Payroll screens | **XL** | FE | `W-45` | |
| `W-48` | HRMS screens (MUI → Ant Design rewrite) | **L** | FE | `W-45` | |

### Stream G — Infrastructure *(parallel from day one)*

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-49` | Containerisation | M | INFRA | `W-01` | |
| `W-50` | Azure infrastructure as code | **L** | INFRA | `W-49` | |
| `W-51` | Networking & identity | M | INFRA | `W-50` | |
| `W-52` | Queue & worker | M | BE | `W-50` | |
| `W-53` | Caching | M | BE | `W-50` | |
| `W-54` | Deployment pipeline | M | INFRA | `W-50`, `W-03` | |
| `W-55` | Index & query standard | M | DATA | `W-07` | |

### Stream H — Security, operations & go-to-market

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-56` | Secrets to Key Vault | S | SEC | `W-51` | |
| `W-57` | Deny-by-default authentication | M | SEC | `W-10` | |
| `W-58` | Tenant isolation tests | M | SEC | `W-08` | |
| `W-59` | Dependency & code scanning | S | INFRA | `W-03` | |
| `W-60` | Observability | M | INFRA | `W-50` | |
| `W-61` | Alerting | S | INFRA | `W-60` | |
| `W-62` | Backup & disaster recovery | M | INFRA | `W-50` | |
| `W-63` | Load test | S | INFRA | `W-54` | |
| `W-64` | Penetration test | S + remediation | SEC | `W-57`, `W-58` | |
| `W-65` | Admin console | **L** | BE + FE | `W-12` | |
| `W-66` | Marketing website | **L** | FE | — | ✅ |

### Stream I — Migration *(last)*

| ID | Work item | Size | Skill | Blocked by | Ready |
|---|---|---|---|---|---|
| `W-67` | Migration rules | M | DATA + you | Streams C–E | |
| `W-68` | Migration engine | **XL** | DATA | `W-67` | |
| `W-69` | Document migration | M | BE | `W-21`, `W-68` | |
| `W-70` | Reconciliation | **L** | DATA | `W-68` | |
| `W-71` | Rehearsals | M | DATA | `W-70` | |
| `W-72` | Cutover | M | INFRA + you | `W-71` | |

---

## 4. The seven XL items — split before assigning

Handing any of these to one person as a single ticket is how a project loses a month quietly.

**`W-16` Leave engine** → split into 4
1. Leave types and policy (S)
2. Allocation and balance calculation (M)
3. Request, approval and documents (M)
4. Consumption, loss-of-pay derivation and bulk import (M)

> Parts 1–2 come from Payroll's half, part 3 from HRMS's. Part 4 is the join, and the risk.

**`W-29` Pay run** → split into 4
1. Run creation, employee inclusion, locking (M)
2. Earnings and deductions computation (M)
3. Loss-of-pay application and pay input collection (M)
4. Async execution on the worker, status and progress (M)

> Build 1–3 synchronously first, then move to the worker in part 4. Debugging a distributed
> calculation before the calculation is correct is a bad trade.

**`W-32` Income tax declaration** → split by section, 3–4 tickets
1. Declaration window, submission and revision (M)
2. House rent, home loan, let-out property (M)
3. Section 6A, pre-tax deductions, previous employment (M)
4. Other income and tax summary (S)

**`W-33` Tax calculator** → split into 3
1. New regime (M)
2. Old regime with section deductions (L)
3. Revisions and recalculation (M)

> Old regime is materially harder than new. Do not assume symmetry.

**`W-46` Core screens** → split by area, 5 tickets
Employee · Leave · Holiday and org setup · Approvals · Employee portal

**`W-47` Payroll screens** → split by area, 5 tickets
Salary structure · Pay run · Tax and declarations · Claims · Dashboard

**`W-68` Migration engine** → split into 3
1. Extract and staging (M)
2. Transform and merge (L)
3. Load and restartability (M)

**After splitting: 72 work items become 93 assignable tickets.**

| | Count |
|---|---|
| Items needing no split | 65 |
| Tickets from splitting the 7 extra-large items (4+4+4+3+5+5+3) | 28 |
| **Total** | **93** |

---

## 5. Size distribution

| Size | Items | Share |
|---|---|---|
| S | 15 | 21% |
| M | 34 | 47% |
| L | 16 | 22% |
| XL | 7 | 10% |

| Skill | Items | Note |
|---|---|---|
| `BE` | 38 | The bulk of the work |
| `INFRA` | 12 | Front-loaded, then trickles |
| `DATA` | 9 | Concentrated in waves 2 and 9 |
| `FE` | 5 | But two are XL — frontend is heavier than the count suggests |
| `SEC` | 4 | Small items, high consequence |
| Mixed | 4 | |

**What this says about the team.** Backend is the constraint throughout. Frontend is
idle until wave 4 and then becomes a bottleneck, because two of its five items are XL and
one is a full rewrite. Infrastructure is busy early and then largely done.

If you add a third developer, make them frontend, and bring them in around wave 3 so the
shell is ready when Core screens open.

---

## 6. Definition of done — applies to every item

No item is complete without all six.

| # | Requirement |
|---|---|
| 1 | Code merged, with the pipeline green — compile, lint, tests |
| 2 | Tests written for what changed. No item ships untested |
| 3 | Every new table carries `tenant_id` and a row-level security policy, unless it is in `reference` |
| 4 | Every new endpoint is authenticated, or on the reviewed exception list |
| 5 | Indexes added for the queries the item introduces |
| 6 | Its `docs/target-state/features/W-nn-*.md` spec updated to match what was actually built |

Items 3 and 4 are checked by the build once `W-07` and `W-57` exist. Until then they are
checked by review, and they are the two most likely to be skipped under time pressure.

---

## Related

- Build order and per-item detail: `09` · Work items and features: `08`
- Capabilities: `01` · Tables: `02` · Decisions: `07`
- Spec template: `docs/target-state/features/TEMPLATE.md`
