# 03 — Code Structure

> Target state. How the code is organised. No Azure resources here — that is `05`.

---

## 1. Two repositories

| Repository | Contents | Why separate |
|---|---|---|
| `infinevo-platform` | Everything that runs the product | One release cycle, one pipeline |
| `infinevo-website` | Public marketing site | Content changes weekly; product code does not. Different people, different risk, **no integration** (`D-13`) |

The four current repositories (`HRMS_Backend`, `HRMS_Frontend`, `Payroll-Bend-SBoot`,
`Payroll-Fend-react`) remain **production and read-only** until cutover, and are never
pushed to. Code is **ported deliberately, once** — there is no sync mechanism (`D-17`, §7).

---

## 2. Platform repository layout

```
infinevo-platform/
├── code/
│   ├── backend/
│   ├── core/                    Maven module — employee, leave, identity, workflow, audit
│   ├── hrms/                    Maven module — attendance, overtime, projects, timesheets
│   ├── payroll/                 Maven module — pay runs, tax, claims, statutory
│   ├── app/                     Spring Boot — web role
│   ├── worker/                  Spring Boot — batch role
│   ├── shared/                  cross-cutting: tenant context, error envelope, money types
│   └── migration/               Flyway scripts
│       ├── core/
│       ├── hrms/
│       ├── payroll/
│       └── reference/
├── frontend/
│   ├── shell/                   layout, navigation driven by entitlement
│   ├── core/                    employee, leave, holidays, org setup
│   ├── hrms/                    attendance, timesheets, projects
│   ├── payroll/                 pay runs, tax, claims
│   └── shared/                  design system, api client, auth
├── infra/                       everything about running it - no application code
│   ├── azure/                   Azure definitions (Bicep)
│   ├── docker/                  Dockerfiles, local compose stack
│   └── keycloak/                realm export, theme
├── .github/workflows/           build, test, deploy (GitHub reads only this path)
├── legacy/                      the four frozen applications, read-only
└── docs/                        target-state/ and legacy/, plus conventions
```

---

## 3. The module graph, and how it is enforced

```
        app ──┐                    worker ──┐
              ├──► hrms ──┐                 │  (same modules)
              ├──► payroll┤                 │
              └───────────┴──► core ──► shared
```

| Rule | Enforced by |
|---|---|
| `hrms` may depend on `core` | Maven dependency, declared |
| `payroll` may depend on `core` | Maven dependency, declared |
| **`hrms` may not depend on `payroll`, or the reverse** | **The build fails.** No dependency declared, so the classes are not on the classpath |
| `core` may not depend on either module | Same |
| Cross-module data passes via `core.pay_input` | Schema, plus code review |

**This is the point of the monorepo.** Today the four repositories cannot see each other, so
shared logic was *copied* — which is why cost-to-company calculation exists in more than one
place. One repository makes sharing possible; Maven modules stop it becoming a mess.

**Package convention inside a module:**

```
com.infinevo.<module>.<feature>
  ├── controller/      thin — no logic
  ├── service/         interface
  ├── serviceimpl/     all business logic
  ├── repository/
  ├── entity/
  ├── dto/
  └── mapper/
```

Carried forward from both current backends, which already use this layering.

---

## 4. Two roles, one image

`app` and `worker` are thin Spring Boot modules over the same three business modules. They
differ only in what they start.

| | `app` | `worker` |
|---|---|---|
| Serves HTTP | yes | health endpoint only |
| Consumes the queue | no | yes |
| Runs scheduled jobs | **no** | yes, with cluster locking |
| Scales on | request concurrency | queue depth |

**Why scheduled jobs move to the worker.** Two `@Scheduled` jobs run today with no locking.
The moment a second instance exists they fire twice. Confining them to a single worker role
with a lock turns a scaling blocker into a solved problem.

---

## 5. Frontend structure

One React application. Today there are two, on different build tools with different
component libraries.

| Concern | Decision |
|---|---|
| Build tool | Vite (from HRMS_Frontend). Payroll's Create React App setup is retired |
| Component library | **Open — `OQ-04`.** Ant Design (Payroll, 158 routes) vs MUI (HRMS). Payroll has far more screens |
| State | Redux Toolkit, from Payroll |
| Routing | Route groups per module, registered only when the module is entitled |
| API layer | **A real service layer.** Payroll frontend has none today — screens call axios directly with a global URL constant and read tenant from local storage |
| Auth | Keycloak adapter, from Payroll |

**Navigation is entitlement-driven.** The shell asks for the tenant's modules and registers
only those route groups. A hidden menu item with a live endpoint behind it is a security
bug, so the server enforces independently (`01` §7).

---

## 6. Migration scripts

```
migration/
├── core/       V001__tenant.sql, V002__employee.sql, ...
├── hrms/
├── payroll/
└── reference/  seeded tax rules, versioned like schema
```

| Rule |
|---|
| Flyway only. `ddl-auto` disabled permanently |
| Numbered, forward-only. Never edit an applied script |
| One script set applied together, in schema order: `reference` → `core` → `hrms` → `payroll` |
| Reference data seeded by migration, not by application startup |
| Every script reviewed like code |

---

## 7. No upstream sync (`D-17`)

**Decided 2026-09-11: there is no sync mechanism.** The new codebase is built here, once.
The subtree migration and the `sync-upstream` skill are both cancelled.

| Aspect | Position |
|---|---|
| The four origin repos | Remain production, and remain **read-only reference**. Never pushed to |
| Code arrival | Ported deliberately, module by module, as each is built |
| Branches to port from | `main`, `main`, `taxation`, `employee` — **not** `main` on the two Payroll repos |
| Production fixes made after today | **Do not arrive automatically.** Each must be re-applied by hand |

> ⚠️ **The risk this creates.** Payroll is under active development — its two repos had
> commits on 2026-09-09 and 2026-09-10. Divergence begins today and compounds. Either
> production development is frozen at an agreed point, or every post-cutoff fix is tracked
> and re-applied manually. **A list of production fixes made after today should be kept from
> now on**, or they will be silently lost at cutover.

## 8. Conventions carried forward

| Convention | Detail |
|---|---|
| Money | `BigDecimal`, never float. `compareTo` not `equals`. Explicit scale on divide |
| Response shape | Standardised envelope. Today it is hand-built `Map` objects, inconsistently |
| Tenant access | Never from a request header. Read from the token, set on the transaction |
| Errors | One global handler per module, not per controller |

### Load-bearing names that must **not** be silently renamed

These typos are in live code paths. Renaming them without a migration breaks things, and
search-and-replace will not find what you expect.

| Typo | Where |
|---|---|
| `timeshhet/` | Payroll — **in the live timesheet service path** |
| `leaveAndAttedance/` | Payroll — entity package for leave and attendance |
| `EmployyePortalContoller.java` | Payroll controller |

Rename them **deliberately**, as part of porting the module, with the migration written down.

---

## 9. Website repository

| Aspect | Decision |
|---|---|
| Contents | Module pages, feature comparison, pricing, lead capture, help centre, blog |
| Integration | **None.** No trial sign-up, no payment, no platform API calls (`D-13`, `D-14`) |
| Capability data | A published catalogue file, copied from the platform. Not a live feed |
| Editing | Marketing must be able to change copy without a developer |
| Hosting | Static, behind the same front door so the domain is unified |
| Critical path | **Not on it.** Can be built at any time, by anyone, in parallel |

---

## Related

- Containers: `04-runtime-containers.md` · Azure: `05-azure-architecture.md`
- Decisions: `07-decisions.md` · Today's conventions: `docs/CONVENTIONS.md`
