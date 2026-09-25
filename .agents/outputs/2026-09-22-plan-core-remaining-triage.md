# Stream C — the sixteen remaining tickets, size-cap triage

| | |
|---|---|
| Scope | Every open stream-C ticket except `W-10` (#11) and `W-22` (#26), both already specced |
| Purpose | Step 0 of `plan-feature` applied to all sixteen at once, before any spec is written |
| Founder instruction | Plan the whole of Core, blocked or not. The blocked label is recorded, not obeyed |

## The answer in one table

| Ticket | Tables | Modules | Verdict | Becomes |
|---|---|---|---|---|
| #12 `W-11` Authorization | 4 | `core` + `shared` | **split** | 2 |
| #13 `W-12` Subscription & entitlement | 2 | `core` + `shared` + frontend | **split** | 3 |
| #15 `W-14` Org structure & hierarchy | 4 | `core` | **split** | 2 |
| #16 `W-15` Approval engine | 3 | `core` | **split** | 3 |
| #17 `W-16.1` Leave types & policy | 2 | `core` | fits | 1 |
| #18 `W-16.2` Allocation & balance | 1 | `core` | fits | 1 |
| #19 `W-16.3` Request & approval | 2 | `core` | fits | 1 |
| #20 `W-16.4` Consumption, LOP, import | 3 | `core` | **split** | 2 |
| #21 `W-17` Holiday calendar | 2 | `core` | fits | 1 |
| #22 `W-18` LOP & working-day policy | 1 | `core` + `payroll` | **split** | 2 |
| #23 `W-19` Pay input ledger | 1 | `core` | fits | 1 |
| #24 `W-20` Notifications | 3 | `core` + `worker` | **split** | 2 |
| #25 `W-21` Document store | 1 | `core` | fits | 1 |
| #27 `W-23` Reporting & export | 1 | `core` + `worker` | **split** | 2 |
| #28 `W-24` Setup checklist & invitations | 3 | `core` | **split** | 2 |
| #29 `W-25` Self-service portal | 0 | frontend | fits | 1 |
| | | | **16 → 25** | |

Nine tickets split, seven stand. The programme gains nine tickets and loses the review
rounds that `W-08` spent — five spec reviews and three code rounds, merging with thirteen
findings open (`plan-feature` SKILL.md).

Table counts come from `02-data-model.md:293-310`, not from my reading of the legacy code.

---

## Why each split, in one line

**#12 `W-11` Authorization** — the catalogue is data, the check is a filter, and the Redis
cache is the thing that makes it hard. `09-build-order.md:183`: *"a role change takes effect
across both running instances"* is a distributed-cache behaviour, not a CRUD one.

| New | Scope |
|---|---|
| `W-11.1` | `role`, `action`, `role_action`, `user_role` — the catalogue and its API |
| `W-11.2` | the permission check filter and the shared Redis cache, with invalidation across replicas |

**#13 `W-12` Subscription & entitlement** — `09-build-order.md:185` demands **both** halves:
a `403` on the endpoint **and** no menu. That is a backend behaviour and a frontend behaviour,
and the build order calls a hidden menu over a live endpoint a security bug.

| New | Scope |
|---|---|
| `W-12.1` | `subscription`, `subscription_module`, module selection, tenant creation |
| `W-12.2` | server-side entitlement enforcement — the `403` |
| `W-12.3` | the navigation feed and the frontend gate |

**#15 `W-14` Org structure & hierarchy** — two capabilities in one ticket, `CORE-05` and
`CORE-06` (`02-data-model.md:296-297`), and `09-build-order.md:187` says the free-text
conversion has *"no clean rule — treat it as its own problem, not a footnote."*

| New | Scope |
|---|---|
| `W-14.1` | `department`, `designation`, `work_location` |
| `W-14.2` | `reporting_line`, the org-chart read model, and the three approver levels inherited from the `W-13` split decision |

**#16 `W-15` Approval engine** — `09-build-order.md:189`: *"five flows must fit. Design
against all five before building, or it becomes leave approval with adapters."* One ticket
cannot both design against five flows and prove one.

| New | Scope |
|---|---|
| `W-15.1` | `approval_definition` — the model, designed against all five flows, proved against none |
| `W-15.2` | `approval_instance`, `approval_step`, the lifecycle, routed by reporting line |
| `W-15.3` | delegation, escalation and history |

**#20 `W-16.4`** — three tables and two unrelated behaviours: deriving loss of pay is a
calculation, bulk import is a file pipeline.

| New | Scope |
|---|---|
| `W-16.4a` | `leave_consumption`, `leave_monthly_lop`, and LOP derivation |
| `W-16.4b` | `leave_import_log` and bulk import |

**#22 `W-18` LOP & working-day policy** — `09-build-order.md:198`: *"policy stamped on every
pay figure ... the stamp is not optional."* The policy lives in `core`; the stamp is written
by `payroll`. Two modules, and `maven-enforcer` will not let one ticket touch both.

| New | Scope |
|---|---|
| `W-18.1` | `lop_policy` in `core`, the working-day basis, the derivation rules |
| `W-18.2` | stamping the policy onto every pay figure, in `payroll` |

**#24 `W-20` Notifications** — `09-build-order.md:202`: *"composed in `app`, sent by
`worker`. The queue joins them."* The build order names the module boundary itself.

| New | Scope |
|---|---|
| `W-20.1` | `notification`, `notification_template`, composition in `app`, enqueue |
| `W-20.2` | `reminder_rule`, the scheduler and delivery on `worker` |

**#27 `W-23` Reporting & export** — `09-build-order.md:207`: *"exports run on `worker`,
reading the replica when one exists."*

| New | Scope |
|---|---|
| `W-23.1` | `report_definition` and the one synchronous export path |
| `W-23.2` | scheduled reports on `worker` against the replica |

**#28 `W-24` Setup checklist & invitations** — two capabilities, `CORE-17` and `CORE-18`
(`02-data-model.md:301,309`), sharing nothing but a ticket number.

| New | Scope |
|---|---|
| `W-24.1` | `tenant_setup_step`, assembled from the tenant's modules |
| `W-24.2` | `user_invitation`, `employee_invitation` — and this is what unblocks self-registration, deferred from `W-10` |

---

## The seven that stand

| Ticket | Why it fits |
|---|---|
| #17 `W-16.1` | two tables, one aggregate, one behaviour — same aggregate exception granted for `W-13.2` |
| #18 `W-16.2` | one table, one calculation |
| #19 `W-16.3` | two tables, one aggregate, one behaviour |
| #21 `W-17` | two tables, one aggregate; per work location, not per tenant (`09-build-order.md:196`) |
| #23 `W-19` | one table, and the build order says *"keep it dumb. It is a ledger, not a calculation engine"* (`:200`) |
| #25 `W-21` | one table; Blob and signed links are configuration, not a second module |
| #29 `W-25` | creates no table (`02-data-model.md:309`); it is one frontend area over existing endpoints |

Four of these — `W-16.1`, `W-16.3`, `W-17`, and `W-13.2` already — rely on the **aggregate
exception** the founder granted on 2026-09-22: a ticket may ship one script per table when the
tables are satellites of a single aggregate root. Without it they become two tickets each,
and the count goes from 25 to 29.

---

## Build order after the split

Dependencies are unchanged; the split only makes them finer. The two roots stay `W-10` and
`W-13.1`.

| Wave | Runs in parallel |
|---|---|
| now | `W-10` · `W-13.1` · `W-22.1` |
| after `W-10` | `W-11.1` → `W-11.2` · `W-12.1` → `W-12.2` → `W-12.3` |
| after `W-13.1` | `W-13.2` · `W-13.3` · `W-14.1` → `W-14.2` · `W-19` · `W-20.1` · `W-21` · `W-23.1` |
| after `W-14.2` | `W-15.1` → `W-15.2` → `W-15.3` · `W-17` |
| after `W-15.2` | `W-16.1` → `W-16.2` → `W-16.3` → `W-16.4a` · `W-16.4b` |
| after `W-16` and `W-17` | `W-18.1` → `W-18.2` |
| after `W-12.1` | `W-24.1` · `W-24.2` |
| last | `W-25` — it needs every panel it renders |

---

## What this triage does not do

It does not write the twenty-five specs. Each still needs its own evidence pass against
`legacy/`, and a spec written without one is the padding the skill warns about. The order
above is the order I propose to write them in.

## Decision needed

**Confirm the split count before I write.** Twenty-five specs is the honest shape of Core;
sixteen would mean putting `W-08`-sized tickets back into the queue. If you want fewer
tickets rather than fewer review rounds, say so now — it is much cheaper to change here than
after nine of them are written.
