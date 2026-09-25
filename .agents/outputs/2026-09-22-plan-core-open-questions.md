# Core — every open question across 33 specs

| | |
|---|---|
| Specs written | 33, covering all of stream C |
| Questions open | **none — all 69 settled 2026-09-22** |
| Answered against my recommendation | **9**, listed in §6 |
| Specs needing a change as a result | 9 |

Every question is answered. Nine went against my recommendation; those are the ones worth
re-reading, and §6 lists them with what each changes.

---

## 1. Blocking — settled by the founder, 2026-09-22

| # | Ticket | Question | **Decision** |
|---|---|---|---|
| **B1** | `W-11.1` | Where does the action catalogue live? | **`reference.action`.** `D-08`'s exemption already covers data identical for every tenant; no `tenant_id`, no 88 duplicated rows per tenant |
| **B2** | `W-12.1` | What happens when a tenant drops a module? | **Read-only for the statutory retention window, no new records.** `W-12.2`'s `@RequiresModule` therefore needs a read-only mode, not a plain refusal |
| **B3** | `W-13.1` | Where do the statutory eligibility flags go? | **A `payroll` table under `PAY-01`.** `core.employee` stays product-neutral; an HRMS-only tenant carries no payroll columns |
| **B4** | `W-20.2` | Where does the scheduler lease live? | **Postgres.** Losing Redis slows permissions; it must not stop reminders, escalations and retention |
| **B5** | `W-22.2` | Who may delete expired audit rows? | **A dedicated `retention_user` role.** `app_user` keeps `SELECT, INSERT` only. `infra/postgres/` gains a fourth role and `DatabasePrivilegesIT` a fourth row |

**Consequences to carry into the code:**

- `W-11.1`'s migration writes `reference/V0NN__action.sql`, not `core/`. `role_action.action_code` references `reference.action(code)` across schemas, which is legal and needs the grant to `app_user` spelled out
- `W-12.2` gains a read-only mode and the tests that go with it
- `W-13.1` drops six columns from its migration; `PAY-01` gains them
- `W-20.2` ships a lease table, so it has **two** migration scripts — still one table each
- `W-22.2` changes `infra/postgres/provision.sh` and the role matrix, which `W-05` made canonical

### B6 — raised by settling B4, then overtaken by `main`

Putting the lease in Postgres implied a `scheduler_lease` table, and it fitted no schema. I
recommended stretching `D-08` to put it in `reference`; the founder's objection killed that,
because `reference` is read-only to `app_user`. We settled on a PostgreSQL advisory lock with
no table.

**All of that was unnecessary.** On 2026-09-23, rebasing onto `origin/main` showed `W-52` had
already merged **ShedLock**, backed by `core.shedlock`, wired in
`code/backend/worker/.../config/SchedulerLockConfig.java` and covered by `SchedulerLockIT`.
It closes DEBT-021 with the exact words *"prevents scheduled jobs from duplicate execution
across multi-replica workers"*.

| | |
|---|---|
| Decision that stands | The lock lives in **Postgres**, not Redis — satisfied by ShedLock |
| Withdrawn | The `scheduler_lease` table, and the advisory lock that replaced it |
| `W-20.2` now | Annotates jobs `@Scheduled` + `@SchedulerLock`. It builds no locking of its own |

And `core.shedlock` answers the question I put to the founder: it sits in `core`, carries no
`tenant_id` and has no row-level security, because a lock row belongs to the platform. The
precedent was already merged while I was asking.

**Two lessons worth keeping**, because they cost a real decision each:

1. **I planned against a stale `main`.** The specs were written while local `main` was 18 commits behind. `W-52` (queue, worker, ShedLock), `W-53` (Redis cache) and `W-60` (observability) had all landed. `CLAUDE.md` rule 2 — *repo state beats chat memory* — exists for this, and I did not fetch before planning.
2. **`V006` was taken.** `W-10` and `W-22.1` had claimed `V006` and `V007`; upstream's `V006__job_status_and_shedlock.sql` made the first a duplicate, which `migration/README.md:30` calls a loud Flyway failure. Both were renumbered to `V007` and `V008`.

**A defect found while checking, not caused by this work.** `V006` creates **two** tables in
one script, and the second, `core.shedlock`, has no row-level security. That is issue **#137**
exactly as `active-work.md` describes it — *"the checks grep per file, not per `CREATE TABLE`"*
— now live on `main` rather than hypothetical. Whether `shedlock` should have RLS is a
separate question; that it passed the gate unexamined is the point.

---

## 2. Money and compliance — all settled 2026-09-22

| # | Ticket | Question | **Decision** | Note |
|---|---|---|---|---|
| M1 | `W-18.1` | Default working-day basis | **Calendar days in the month** | Against my recommendation of fixed 30, and chosen knowingly: it keeps payslip amounts identical to the frozen system at cutover. Consequence: the same absence costs more in February than in July. `W-18.2`'s stamp is what makes that explainable |
| M2 | `W-18.1` | A tenant with no policy | **Fall back to calendar days silently** | Against my recommendation of refusing. Consistent with M1. The fallback is still stamped on the pay figure by `W-18.2`, so it is recorded even though it is not announced |
| M3 | `W-18.2` | One employee fails mid-run | **Skip that employee, finish the rest**, report who was skipped | |
| M4 | `W-16.2` | Policy changed mid-leave-year | **Applies immediately, current year included** | Against my recommendation. Consequence: `W-16.2`'s allocation must recalculate entitlement on policy change rather than freezing it at year start, and already-approved leave can become over-drawn. The audit trail matters more under this choice |
| M5 | `W-16.4a` | Loss of pay per type or per month | **Per type, summed to one monthly figure** | |
| M6 | `W-16.4a` | Cancellation after the period locks | **Credit the next open month** | Locked payslips are never rewritten |
| M7 | `W-19` | Who locks a pay period | **The pay run, plus an admin override** for abandoned runs | |
| M8 | `W-19` | Does a pay input need its own approval | **No** — it was approved where it originated | |
| M9 | `W-15.1` | Amount-based approval rules | **Not yet** — its own ticket after the engine is proven | |

---

## 3. Security and access — all settled 2026-09-22

| # | Ticket | Question | **Decision** | Note |
|---|---|---|---|---|
| S1 | `W-13.2` | Encrypt bank account numbers at column level | **No — disk encryption only**, revisit at `W-57` | |
| S2 | `W-21` | Document link lifetime | **15 minutes** | |
| S3 | `W-23.2` | Scheduled report link lifetime | **7 days** | |
| S4 | `W-23.2` | External report recipients | **Allowed, every external address audited** | |
| S5 | `W-11.2` | Refusal code for an unheld action | **`403`** | |
| S6 | `W-15.3` | What a delegate sees | **Everything the delegator would**, stated on the delegation screen | |
| S7 | `W-24.2` | Invitation expiry | **7 days for both** admin and employee | Against my recommendation of 3 days for admins. One rule, easier to explain, and matches today's employee behaviour |
| S8 | `W-24.2` | Email already has an account elsewhere | **Reuse it**, add a second tenant membership | One credential reaches two customers' data, accepted knowingly |
| S9 | `W-24.2` | Revoking an accepted invitation | **Does nothing** — revoke applies to pending invitations only | |
| S10 | `W-21` | Document deletion | **Soft delete by an admin; the file is kept** | |
| S11 | `W-12.2` | Non-payment | **`past_due` blocks nothing; `suspended` blocks the modules**, leaving login and billing reachable | |

---

## 4. Model and scope — all settled 2026-09-22

| # | Ticket | Question | **Decision** |
|---|---|---|---|
| D1 | `W-16.1` | Eligibility as a third table in the same ticket? | **Yes**, aggregate exception |
| D2 | `W-16.1` | Carry the unused encashment settings forward? | **No — leave them out entirely.** *Against my recommendation; adding them later is a migration* |
| D3 | `W-16.1` | Hour-based leave? | **Days only**, hours rejected at the API |
| D4 | `W-16.2` | Who runs accrual? | **The shared scheduler**, not a timer in `app` |
| D5 | `W-16.3` | Cancelling approved leave | **Allowed until the leave starts**; after that it is a correction |
| D6 | `W-16.3` | Mandatory attachments | **Yes — a `requires_document` flag per leave type** |
| D7 | `W-16.4b` | Import formats | **CSV only** |
| D8 | `W-16.4b` | Dry run before commit | **The screen always dry-runs; the API may commit directly** |
| D9 | `W-17` | Calendar-to-location link in the same ticket? | **Yes**, aggregate exception |
| D10 | `W-17` | Calendars per location | **Exactly one per location**; a calendar may cover many |
| D11 | `W-19` | Pay period as text or a link | **Text now**, link added by `W-28` |
| D12 | `W-13.1` | Keep the per-employee portal switch? | **Yes, keep it.** *Against my recommendation; `is_portal_enabled` returns to `core.employee`* |
| D13 | `W-13.2` | Permanent address | **Repeated columns on one row** |
| D14 | `W-14.1` | Level or grade on a designation | **No** — grade stays on the employment record |
| D15 | `W-14.1` | Nested departments | **Flat** |
| D16 | `W-14.2` | Multiple managers | **One primary, unlimited indirect** |
| D17 | `W-14.2` | Manager changes mid-approval | **The original approver keeps it** |
| D18 | `W-15.1` | Who edits approval chains | **Platform defaults, tenant-editable** |
| D19 | `W-15.1` | Shape of the invented regularisation flow | **One step, to the reporting manager** |
| D20 | `W-15.2` | Self-approval | **Allowed.** *Against my recommendation; the audit trail records approver and requester as the same person* |
| D21 | `W-15.2` | After rejection | **The instance ends**; raise a new request |
| D22 | `W-15.3` | Delegation chains | **Not allowed** — one hop only |
| D23 | `W-15.3` | Admin reassignment of a stuck approval | **Allowed**, recorded on the step |
| D24 | `W-11.1` | Seeded system roles | **A richer set** — platform admin, tenant admin, HR, manager, payroll officer, finance, employee. *Against my recommendation of three* |
| D25 | `W-11.2` | Permission cache location | **Redis, shared** |
| D26 | `W-12.1` | Trials | **No trial concept at all.** *Against my recommendation of keeping the status value* |
| D27 | `W-12.1` | Who creates a tenant | **Platform staff only** |
| D28 | `W-12.3` | Read-only screens in the menu | **Yes** — show the screen, hide the buttons |
| D29 | `W-12.3` | Per-tenant menu order | **No**, one fixed order |
| D30 | `W-20.1` | Tenant-editable templates | **Yes**, over platform defaults |
| D31 | `W-20.2` | Email provider | **Stay on Brevo**, behind a swappable interface |
| D32 | `W-23.1` | Tenant-defined reports | **Yes**, from a fixed set of sources |
| D33 | `W-23.1` | Export delivery | **Saved to the document store, then a link** |
| D34 | `W-24.1` | New setup steps for existing tenants | **Shown as pending, progress stays at 100%** |
| D35 | `W-24.1` | Does setup block anything | **No** — it guides only |
| D36 | `W-25` | Per-employee portal toggle | **Kept** — see D12 |
| D37 | `W-25` | Ship the portal before every panel is real | **Yes**, with placeholders |
| D38 | `W-22.2` | Archive before deleting | **No — deletion is final** |
| D39 | `W-22.2` | Retention floor | **12 months minimum** |
| D40 | `W-20.1` | Notification retention | **Add it to `W-22.2`'s sweep** as a second target |
| D41 | `W-18.2` | Who may see a pay figure's working | **The employee for their own**, plus payroll staff |
| D42 | `W-20.2` | Does the retention sweep use the shared scheduler | **Yes** |
| D43 | `W-12.1` | A tenant re-buys a module it dropped | **Its old data returns as it was**, with the gap visible in the record |

---

## 6. The nine that went against my recommendation

These are the ones to re-read. Each changes a spec, and none is wrong — they trade differently
than I did.

| # | Decision | What it changes | What it costs |
|---|---|---|---|
| M1 | Pay divisor is **calendar days**, not fixed 30 | `W-18.1`'s default | An absence costs more in February than July. Accepted to keep payslip amounts identical at cutover |
| M2 | Missing policy **falls back silently** | `W-18.1` refuses nothing | A tenant can run payroll on a default nobody told them about. Mitigated: `W-18.2` stamps the fallback on the figure |
| M4 | Policy change applies **to the year in progress** | `W-16.2` must recalculate entitlement on change, not freeze it at year start | Already-approved leave can become over-drawn retrospectively |
| S7 | Invitations expire in **7 days for both** types | `W-24.2` | An admin invitation — the more powerful one — stays live as long as an employee's |
| D2 | Encashment settings **left out** | `W-16.1` drops two columns | Adding them later is a migration |
| D12 | Portal switch **kept** | `is_portal_enabled` returns to `W-13.1`; `W-25` reads it | Two mechanisms control portal access. I will make the stricter one win unless you say otherwise |
| D20 | Self-approval **allowed** | `W-15.2` drops the refusal | Anyone senior can approve their own leave and expenses. Visible in the audit trail, not prevented |
| D24 | **Richer role set** seeded | `W-11.1`'s seed grows | Database roles no longer map one-to-one to the three login roles. The spec must state how they relate |
| D26 | **No trial concept** | `W-12.1` drops the status value | Adding trials later is a migration rather than a code change |

---

## 5. Documentation corrections found while specifying

Not questions — these are places where a document and the code disagree. Each needs
`sync-docs`, and none should be silently worked around.

| Document | Says | Actually |
|---|---|---|
| `02-data-model.md:73` | HRMS `work` **and `report`** merge into `employee_employment` | The approved `W-13` split sends `report`'s approver levels to `W-14.2`'s `reporting_line` |
| `02-data-model.md:123` | `report_definition` comes from HRMS `report` | HRMS `report` is the reporting-hierarchy table. `report_definition` has no source — it is a new build |
| `09-build-order.md:196` | "Payroll models locations properly; use that" | It models *work locations* properly; holiday-to-location is a `Set<String>`, so a rename orphans them |
| `09-build-order.md:202` | A payroll event sending email is "something Payroll has never done" | Payroll sends the salary slip at `PayRunServiceImpl.java:975`. The gap is the absence of a framework, not of any email |
| `legacy/docs/FEATURE_MAP.md:346` | The pay run reads consumption for loss-of-pay days | It calls HRMS at `EmployeePayRunServiceImpl.java:1091`. This is `legacy/docs/`, outside `sync-docs` — raise separately |
