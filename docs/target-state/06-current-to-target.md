# 06 — Current State to Target State

> Why each thing changes. No timelines, no estimates, no team allocation — those live in
> `MANAGEMENT_SUMMARY_AND_PLAN.md` and would drift if repeated here.

---

## 1. Side by side

| Dimension | Today | Target |
|---|---|---|
| Repositories | 4, independent, no shared history | 1 platform + 1 website |
| Deployables | 4 | 5 containers from 4 Dockerfiles |
| Backends | 2 applications | 1 application, 3 modules |
| Frontends | 2, different build tools and component libraries | 1 |
| Databases | 2 MySQL | 1 Postgres, 4 schemas |
| Tables | 131, with duplicates | ~130, deduplicated |
| Login systems | 2 | 1 |
| Tenant isolation | None in HRMS, 63 of 97 entities in Payroll | Enforced by the database, everywhere |
| Cross-product calls | HTTP over the network | Method calls, one process |
| Scaling | Single instance only | Horizontal |
| Long jobs | Synchronous HTTP requests | Queued to a worker |
| Schema changes | Applied automatically at startup | Numbered scripts, reviewed |
| Secrets | In configuration files, committed | Key Vault + managed identity |
| Documents | Cloudinary, outside Azure | Blob Storage |
| Deployment | Manual, DigitalOcean | Automated pipeline, Azure |
| Monitoring | Minimal | Traces, metrics, alerts |
| Tests | Effectively none | Required to merge |
| Indexes | Effectively none across 136 entities | Standard, tenant-leading |
| Audit trail | None in either product | Platform-wide |

---

## 2. Why each change, one at a time

### Repositories: 4 → 1
Four repositories cannot see each other, so shared code was **copied rather than shared**.
That is why cost-to-company calculation exists in more than one place, and why a fix in one
does not reach the other. One repository makes sharing possible; Maven modules stop it
becoming a tangle (`03` §3).

### Backends: 2 applications → 1 with 3 modules
All 58 Payroll tables reference Core data. Kept as separate services, every payroll
operation becomes a network call for employee and leave data — slower, and much harder to
keep correct across a failure. The boundary is still real, but enforced by the build rather
than by the network. (`D-01`)

### A worker role appears
The pay run is heavy, but it is a **batch** problem, not a request-throughput problem.
Moving it to a queue and a worker fixes three things at once: requests stop timing out, jobs
survive a restart, and pay-run capacity exists only during pay runs. (`D-02`)

It also fixes a correctness bug. Two scheduled jobs run today with no locking, so a second
instance makes them fire twice.

### Databases: 2 MySQL → 1 Postgres
Two databases mean employee and leave data exist twice and can disagree. One database ends
that. Postgres specifically, because **row-level security** moves tenant isolation from
"every developer remembers" to "the database refuses". Given HRMS has no tenancy at all
today, that is not a refinement — it is the whole safety mechanism. (`D-09`)

### Four schemas, not one namespace
The module boundary needs to be visible and enforceable in the data, not just in the code.
It also isolates the one deliberate exception: `reference` holds shared national tax rules
with no tenant column, so any table outside `reference` lacking `tenant_id` is a bug you can
query for. (`D-08`)

### Login: 2 → 1
A customer with both products logs in twice today. One identity provider is the premise of
the whole platform. Keycloak wins because Payroll's implementation is already multi-tenant
and HRMS's custom token system would have to be retrofitted for tenancy anyway.

### Leave: two engines → one, from both halves
The management summary said to build Core's engine from HRMS's code and retire Payroll's
recent work. The evidence says otherwise: they implement **different halves** of the same
engine. HRMS owns request, approval, documents. Payroll owns allocation, consumption, loss
of pay, import. Discarding either loses real function. (`D-03`)

### Attendance and overtime: Core → HRMS module
Loss of pay today comes from approved leave, not attendance — so Payroll does not depend on
attendance and a Payroll-only customer is not broken without it. It is therefore something a
customer *buys*, which makes it a module capability and a commercial lever. (`D-04`, `D-05`)

Payroll's attendance preferences move with it. They currently configure hour thresholds,
overtime minimums and pay-treatment flags for an attendance system Payroll does not have.

### Schema changes: automatic → versioned scripts
Both backends run `ddl-auto=update` today. It silently alters production schema on startup,
never drops anything, and leaves no record of what changed or when. It is the single most
dangerous behaviour in the platform. Flyway replaces it. (`D-09`)

### Secrets, documents, monitoring, tests, indexes
Each is currently absent rather than inadequate, so the reasoning is the same in every case:
these are the difference between software that works and software that can be operated,
audited and sold to an enterprise buyer.

The indexes deserve singling out: with effectively none across 136 entity classes, adding
servers multiplies database load rather than throughput. **Performance work must precede
scaling work.** (`PLAT-06`)

---

## 3. What does **not** change

Unification can sound like a rewrite. It is not.

| Preserved |
|---|
| Tax calculation logic, old and new regime |
| Pay run rules and statutory computation |
| Leave accrual and consumption rules |
| Timesheet and project handling |
| Investment proof and reimbursement workflows |
| The controller → service → serviceimpl → repository layering, already used by both backends |

**Genuinely new code is a short list:** subscription and entitlement, the approval engine,
the audit trail, the reporting hierarchy, the loss-of-pay policy, and the pay input ledger.
Everything else moves, merges, or is deleted as duplicate.

---

## 4. Known risks

| Risk | Why it is real | Mitigation |
|---|---|---|
| **Employee master merge** | Two different models, both with live data. Deduplication by email will not resolve every case | Written rules agreed before any row moves; reconciliation report; nothing deleted |
| **Two loss-of-pay figures** | HRMS computes it from leave requests; Payroll from its own consumption tables. They may already disagree | Mismatch log reviewed by finance before cutover; per-tenant policy records which rule produced each figure (`CORE-09`) |
| **Duplicate entities are live, not dead** | Investigation found the older leave request entity still reachable via update and delete routes, and **both** timesheet systems fully live | Each retirement confirmed individually. `OQ-01` blocks the HRMS module port |
| **HRMS free text → master data** | Department, designation and location are free text in HRMS, entities in Payroll. No clean deduplication rule exists | Treated as its own feature, not absorbed into the employee merge |
| **Tenancy retrofit volume** | 39 HRMS entities plus 34 Payroll entities lack tenant scoping | Repetitive but not difficult; automated checks that fail the build on an unscoped query |
| **Upstream divergence** | Production repos keep changing while the new one is built | One-way sync until a hard cutoff, then manual porting. Cost grows with delay (`OQ-05`) |
| **Configurable loss of pay** | Every combination affects real pay | Each combination tested; the pay run records which policy produced each figure |
| **Cloudinary migration** | Every existing document reference must be rewritten | Scoped with the document store work, not assumed into the Azure setup |
| **Tax area size** | Roughly a third of Payroll's tables, and the highest compliance exposure | Touched last, never first |

---

## 5. Live issues found while designing this

Not target-state matters. Present-tense problems, recorded so they are not lost.

| Finding | Where |
|---|---|
| `POST /public/get-employee-leaves` on HRMS requires **no authentication**; CSRF disabled globally; `/register` is also open | `agents/outputs/2026-09-11-security-finding-public-endpoint.md` |
| The HRMS→Payroll leave integration is listed as frozen and superseded, but the controller is **live and reachable** | Same file. `OQ-03` |
| Two complete timesheet systems run side by side, live | `agents/outputs/2026-09-11-hrms-duplicate-entities.md` |
| Payroll has **no notification or email capability at all** | `agents/outputs/2026-09-11-core-boundary-payroll.md` |
| Payroll stores attendance preferences for an attendance system it does not have | Same file |

**The first row should be checked before anything else in this programme**, because whether
that endpoint is reachable from the public internet decides if it is critical or minor.

---

## Related

- Capabilities: `01` · Tables: `02` · Decisions and open questions: `07`
- Plan, phases and estimates: `MANAGEMENT_SUMMARY_AND_PLAN.md`
