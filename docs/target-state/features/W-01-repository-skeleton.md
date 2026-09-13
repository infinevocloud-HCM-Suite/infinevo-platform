# Feature: W-01 — Repository & module skeleton

| Field | Value |
|---|---|
| **Work item** | `W-01` |
| **Stream / track** | Stream A — Platform foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | M · Backend |
| **Blocked by** | — *(one of only two items that can start on day one)* |
| **Blocks** | `W-02` `W-03` `W-04` `W-05` `W-49` — and transitively everything else |
| **Capabilities** | — *(enabling item; delivers no customer-facing capability)* |
| **Decisions** | `D-17` no upstream sync · `D-29` Ant Design · `D-30` Vite · `D-38`–`D-41` toolchain |
| **Owner** | Founder / lead |
| **Status** | **Approved — ready to build** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-13 |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

The repository exists but holds only documents, the harness, and the frozen
applications under `legacy/`. There is no `backend/`, no `frontend/`, no build.

Every other work item depends on this one, and one property of it is the reason it
must come first: **the module boundary between HRMS and Payroll has to be enforced
by the build, and it has to be enforced before any code exists.**

The evidence for why is in the frozen code. Today the four repositories cannot see
each other, so shared logic was copied rather than shared — cost-to-company
calculation exists in more than one place (`03-code-structure.md` §3). A monorepo
makes sharing possible; without a declared module graph it also makes tangling
possible, and a tangle discovered in month four is not refactored, it is lived with.

> From `09-build-order.md` §3: *"Get the graph right now. Retrofitting a module
> boundary after code lands is the expensive version."*

---

## 2. Scope

**In scope**

- Maven multi-module backend: `shared`, `core`, `hrms`, `payroll`, `app`, `worker`, `migration`
- The dependency graph declared in POMs, **and a build that fails when it is violated**
- Package conventions under each module, layering carried forward from both frozen backends
- `frontend/` skeleton: Vite + React + Ant Design, split `shell` / `core` / `hrms` / `payroll` / `shared`
- `shared` module contents: tenant context holder, error envelope, money types
- Empty but real directories for `infra/`, `docker/`, `keycloak/`, `.github/workflows/`
- Branch rules and the pull-request template
- The `infinevo-website` repository, created empty

**Out of scope** — each is its own work item

| Not here | Belongs to |
|---|---|
| Any business logic, entity or endpoint | Waves 2+ |
| Flyway runner and migration scripts | `W-06` |
| `tenant_id` standard and row-level security | `W-07` |
| Docker images and the compose stack | `W-49`, `W-02` |
| CI gates actually running | `W-03` |
| Postgres server and schemas | `W-05` |
| Any website content | `W-66` |

---

## 3. What gets built

```
infinevo-platform/
├── backend/
│   ├── pom.xml                  parent — dependency management, Java version, plugins
│   ├── shared/                  tenant context · error envelope · money types
│   ├── core/                    depends on: shared
│   ├── hrms/                    depends on: core, shared
│   ├── payroll/                 depends on: core, shared
│   ├── app/                     Spring Boot web role — depends on: core, hrms, payroll
│   ├── worker/                  Spring Boot batch role — depends on: core, hrms, payroll
│   └── migration/               Flyway script tree (empty; W-06 fills it)
│       ├── reference/  core/  hrms/  payroll/
├── frontend/                    Vite root
│   ├── vite.config.js  .eslintrc.cjs  index.html
│   └── src/
│       ├── shell/               AppShell, store, route registry
│       ├── core/  hrms/  payroll/
│       └── shared/              theme.js, api/client.js
├── keycloak/                    (empty — W-10)
├── infra/                       (empty — W-50)
├── docker/                      (empty — W-49)
└── .github/
    ├── workflows/               (empty — W-03)
    └── pull_request_template.md
```

### The module graph

```
        app ──┐                    worker ──┐
              ├──► hrms ──┐                 │  (same modules)
              ├──► payroll┤                 │
              └───────────┴──► core ──► shared
```

| Rule | How it is enforced |
|---|---|
| `hrms` may depend on `core` and `shared` | Declared in `hrms/pom.xml` |
| `payroll` may depend on `core` and `shared` | Declared in `payroll/pom.xml` |
| **`hrms` must not depend on `payroll`, or the reverse** | Not declared, so the classes are not on the classpath and it will not compile. Plus an explicit `maven-enforcer-plugin` `bannedDependencies` rule, so the failure names the problem instead of reading as a missing import |
| `core` must not depend on `hrms` or `payroll` | Same |
| Cross-module data passes through `core.pay_input` | Schema and review — not this item |

**The enforcer rule is the deliverable, not the folder tree.** A convention that
only lives in a document decays; this one has to fail a build.

### Package convention

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

Carried forward from both frozen backends, which already use this layering.

> **Do not carry the package-name typos forward.** `timeshhet/`,
> `leaveAndAttedance/`, `EmployyePortalContoller.java` are load-bearing *inside
> `legacy/`* and must never be renamed there. New code uses correct spellings.

### `shared` module contents

| Class | Purpose | Used from |
|---|---|---|
| `TenantContext` | Holds the current `tenant_id` for the request or job. `ThreadLocal`, set once, cleared in a `finally` | `W-08` binding filter |
| `ApiError` / `ApiErrorResponse` | One error envelope for every endpoint | Everywhere |
| `Money` | `BigDecimal` wrapper with fixed scale and explicit rounding | Every payroll calculation |

Only these three. `shared` is for cross-cutting primitives; anything with domain
meaning belongs in `core`.

---

## 4. Decisions this item needs

Four could not be deferred past this item, because every module inherits them. **Confirmed by the founder on 2026-09-13 at approval.**

| # | Question | **Confirmed** | Decision | Reason |
|---|---|---|---|---|
| 1 | Java version | **21 (LTS)** | `D-38` | `HRMS_Backend` is already on 21; Payroll is on 17. 21 is LTS with support to 2031, and nothing in the frozen Payroll code blocks it |
| 2 | Spring Boot version | **3.3.x, latest patch** | `D-39` | Frozen apps are on 3.2.4 / 3.2.5. Starting one minor ahead avoids an upgrade in month two |
| 3 | Maven coordinates | `com.infinevo` / `infinevo-platform` | `D-40` | Neither frozen groupId carries forward — `com.phegondev` is a template artefact |
| 4 | Node version | **24 LTS** | ~~`D-41`~~ → `D-42` | Node 20 was proposed and approved, then found to be **end-of-life since April 2026** during the build. Corrected to the current active LTS |

---

## 5. Backend changes

Not applicable — no controller, service, entity or endpoint is created by this
item. The deliverable is build structure.

**API contract:** none.

---

## 6. Frontend changes

Skeleton only. No screen, no route, no API call.

| Path | Holds | Empty? |
|---|---|---|
| `frontend/src/shell/` | Layout and navigation, later driven by entitlement | Yes — one placeholder route |
| `frontend/src/core/` | Employee, leave, holidays, org setup | Yes |
| `frontend/src/hrms/` | Attendance, timesheets, projects | Yes |
| `frontend/src/payroll/` | Pay runs, tax, claims | Yes |
| `frontend/src/shared/` | Design system, API client, auth | **No** — the API client stub and the Ant Design theme land here |

Vite (`D-30`), React 18, Ant Design 5 (`D-29`), Redux Toolkit.

> **The API client stub matters.** The frozen Payroll frontend has no service
> layer — screens call axios directly with a global URL constant and read the
> tenant from local storage (`03-code-structure.md` §5). Creating the service
> layer now is what stops that pattern being copied back in during the port.

---

## 7. Database changes

None. No schema, no migration, no Flyway. `W-05` creates the server and schemas;
`W-06` adds the runner.

- [x] No tables created — the tenant checklist does not apply
- [x] `ddl-auto` must be **absent** from every configuration file written here

---

## 8. Tests

The test that matters is the one proving the boundary cannot be crossed.

| Type | File | Covers |
|---|---|---|
| Build | `backend/pom.xml` enforcer config | `bannedDependencies` — `hrms` ✗ `payroll`, `payroll` ✗ `hrms`, `core` ✗ both |
| Unit | `shared/src/test/.../TenantContextTest` | Set, read, clear; cleared after an exception |
| Unit | `shared/src/test/.../MoneyTest` | Scale, rounding mode, equality; no floating-point type anywhere |
| Manual, once | — | The deliberate-violation check in §9 |

Full test infrastructure is `W-04`. These three are all this item may assume exists.

---

## 9. Verification

```bash
# 1. Everything builds from clean
cd backend && ./mvnw -q clean verify

# 2. The frontend builds
cd frontend && npm ci && npm run build

# 3. THE ACCEPTANCE TEST — the boundary must be un-crossable.
#    Temporarily add to backend/hrms/pom.xml:
#        <dependency>
#          <groupId>com.infinevo</groupId>
#          <artifactId>payroll</artifactId>
#        </dependency>
cd backend && ./mvnw -q clean verify     # MUST FAIL, naming the banned dependency
git checkout backend/hrms/pom.xml        # revert

# 4. No ddl-auto anywhere
grep -rn "ddl-auto" backend/ ; echo "expect: no matches"
```

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | `mvnw clean verify` | BUILD SUCCESS, seven modules | ✅ 8 reactor entries green, 21 tests pass |
| 2 | `npm run build` + `npm run lint` | Builds, Ant Design theme applied | ✅ 1443 modules, 432 kB, lint clean |
| 3 | **Deliberate `hrms` → `payroll` dependency** | **BUILD FAILURE naming the banned dependency** | ✅ fails at `enforce-module-boundary`, message names both modules |
| 4 | `grep -rn ddl-auto backend/` | No matches | ✅ none |
| 5 | `infinevo-website` repository | Exists, private, empty | ✅ created |
| 6 | `guard-edit` on `legacy/` | Exit 2, blocked | ✅ blocked with reason |
| 7 | `verify-app` on `backend/` | Runs a compile | ✅ `PASS backend compile (3.3s) — clean` |
| 8 | Branch protection on `main` | Pull request required | ❌ **refused — Free plan.** See `D-43` |

**Check 3 is the definition of done.** If it passes the build, the item is not
finished, however complete the folder tree looks.

---

## 10. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Graph declared but not enforced — folders exist, nothing fails | **High.** It is the easy mistake | Check 3 is mandatory and must be evidenced in the pull request |
| A "temporary" `hrms` → `payroll` dependency added later under deadline | Medium | The enforcer rule makes removing it a visible, reviewable act |
| Java or Spring Boot version churn later | Low | Settled at approval, §4 |
| `shared` grows into a dumping ground | Medium | Three classes only. Anything with domain meaning goes to `core` — enforced at review |
| The harness still points at the frozen apps | **Certain** | §12 |

---

## 11. Rollback

Nothing is deployed and no data exists. Revert the merge commit.

The only irreversible act is creating the `infinevo-website` repository, which is
independently deletable.

---

## 12. Also in this item

Two harness changes, because once this merges the harness points at the wrong place:

1. **`verify-app.mjs`** — its `APPS` map lists the four frozen application folders.
   Those now live under `legacy/` and resolve to no app, so **no compile check runs
   on any edit today.** Repoint it at `backend/` (Maven) and `frontend/` (lint).
2. **`implementer` agent** — scoped to "one named app folder", which no longer
   describes anything editable. Rescope it to the module being worked in.

And one guard gap worth closing here rather than later:

3. **`guard-edit.mjs`** — blocks `docs/` and `*.properties`, but not `legacy/`. The
   frozen code is protected by convention only. Add `legacy/**`.

---

## 13. Done when

1. `./mvnw clean verify` succeeds on seven modules from clean
2. **A deliberate `hrms` → `payroll` dependency fails the build, naming it**
3. `npm run build` succeeds on the frontend skeleton
4. `TenantContext`, `ApiError` and `Money` exist in `shared`, with tests
5. `ddl-auto` appears in no configuration file
6. `infinevo-website` exists, private and empty
7. ~~Branch rules and~~ the pull-request template are in place — **branch protection is not
   possible on the Free plan for a private repository (`D-43`); it is convention for now**
8. The three harness items in §12 are done
9. This spec is updated to match what was actually built

---

## Related

`08-work-plan.md` W-01 · `09-build-order.md` §3 Track P · `10-scoping.md` §3 ·
`03-code-structure.md` §2–§5 · `07-decisions.md` `D-17` `D-29` `D-30` ·
`../../CONVENTIONS.md` · `CONTRIBUTING.md`
