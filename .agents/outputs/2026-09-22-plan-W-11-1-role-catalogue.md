# Feature: Role and action catalogue

| Field | Value |
|---|---|
| **Feature ID** | `W-11.1` · from ticket #12 · `CORE-03` |
| **Promoted to** | `docs/target-state/features/W-11-1-role-catalogue.md` on branch `W-11-1-role-catalogue` — **`W-11-1` with hyphens**, never `W-11.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-10` — a role is granted to a `core.user_account` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 4 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | a role holding a set of actions is defined and granted, and the grant is readable per tenant | 1 |
| Frontend area | none | 1 |

Within cap. The four tables are one aggregate: a role, the actions it may hold, the mapping
between them, and the grant to a user.

---

## 1. Problem

Both products have roles and actions, and they disagree on the two things that matter: what
a permission is, and whether a role belongs to a tenant.

- **HRMS roles are global.** The `role` entity has no organisation column, so a role defined for one employer is a role for all of them
- HRMS checks permissions by **role name** in the security configuration — `hasAuthority("admin")`, `hasAuthority("user")` and the rest, at `legacy/HRMS_Backend/.../config/SecurityConfig.java:39-43`. The URL prefix decides the rule
- HRMS seeds about **88 actions** at startup through `createActionIfNotExist()` — `ActionServiceImpl.java:152-290` — a hard-coded list in Java
- **Payroll roles are organisation-scoped**, which is the shape the platform needs
- Payroll checks by **action**, not role — `AuthzServiceImpl.canPerform()` at `:84-89`
- Payroll creates actions at runtime with no seed — `ActionServiceImpl.java:24-34`

So the target takes Payroll's structure — tenant-scoped roles, action-based checks — and
HRMS's discipline of a known action catalogue rather than whatever rows happen to exist.

`02-data-model.md:59` confirms the merge: `role_action` comes from both
`organization_role_action` and `user_action_mapping`.

## 2. Scope

**In scope**

- `reference.action` — the catalogue of what can be done, seeded by migration
- `core.role` — tenant-scoped, with **seven** system roles seeded for every tenant (decision 2)
- `core.role_action` — which actions a role holds
- `core.user_role` — which roles a user holds, in which tenant
- CRUD for roles and their action sets

**Out of scope**

- **The permission check itself and its cache** — `W-11.2`. This ticket stores the answer; it does not enforce it
- Entitlement by purchased module — `W-12.2`. A tenant may hold an action its subscription does not cover, and that is `W-12`'s problem to refuse
- Keycloak realm roles — `W-10` owns the three realm roles. They are coarse, they gate nothing, and §13 decision 2 sets out how they relate to the seven seeded here

## 3. Flow

```
[tenant admin] --> [RoleController] --> [RoleService]
   --> TenantContext bound by W-08 --> [core.role, role_action, user_role under RLS]

[W-11.2, later] --> [PermissionService.actionsOf(user, tenant)] --> Set<action code>
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../authz/RoleController.java`, `ActionController.java` | new |
| Service | `core/.../authz/RoleService.java` | new |
| Service | `core/.../authz/PermissionReadService.java` | new — the seam `W-11.2` caches |
| Entity | `core/.../authz/Role.java`, `Action.java`, `RoleAction.java`, `UserRole.java` | new, each `@Table(schema="core")` |
| Repository | four, one per entity | new |
| DTO | `core/.../authz/*Request.java`, `*Response.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/actions` | — | the catalogue | Bearer, tenant bound |
| POST | `/api/v1/roles` | name, action codes | `201` | Bearer, tenant bound |
| GET | `/api/v1/roles` | — | the tenant's roles with their actions | Bearer, tenant bound |
| PUT | `/api/v1/roles/{id}` | name, action codes | `200` | Bearer, tenant bound |
| DELETE | `/api/v1/roles/{id}` | — | `204`, refused if granted to anyone | Bearer, tenant bound |
| PUT | `/api/v1/users/{id}/roles` | role ids | `200` | Bearer, tenant bound |

`reference.action` is read-only over the API. Actions are code, not data — a role may hold an
action, but nobody invents one at runtime the way Payroll does.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V0NN__action.sql` | **`reference.action`** | no — `D-08` exemption | additive |
| `core/V0NN__role.sql` | `core.role` | yes | additive |
| `core/V0NN__role_action.sql` | `core.role_action` | yes | additive |
| `core/V0NN__user_role.sql` | `core.user_role` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`action`: `code varchar(64) PRIMARY KEY` · `name varchar(128)` · `module varchar(16) NOT NULL` —
core, hrms or payroll · `description` · four audit columns. Seeded by the migration.

`role`: `id uuid` · `tenant_id uuid NOT NULL` · `code varchar(64) NOT NULL` ·
`name varchar(128) NOT NULL` · `is_system boolean NOT NULL DEFAULT false` · four audit columns.

`role_action`: `id uuid` · `tenant_id uuid NOT NULL` · `role_id uuid NOT NULL REFERENCES core.role(id)` ·
`action_code varchar(64) NOT NULL REFERENCES reference.action(code)` · four audit columns.

`user_role`: `id uuid` · `tenant_id uuid NOT NULL` ·
`user_account_id uuid NOT NULL REFERENCES core.user_account(id)` ·
`role_id uuid NOT NULL REFERENCES core.role(id)` · four audit columns.

- [x] `tenant_id` present on `role`, `role_action`, `user_role` — leading index column on each
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, code)` unique on role, `(tenant_id, role_id)` on role_action, `(tenant_id, user_account_id)` on user_role
- [x] Money columns — none
- [x] Expand / contract — new tables only

**The action catalogue lives in `reference`, settled by the founder on 2026-09-22.** An action
is a name for something the code can do; every tenant has the same list and only a release
changes it, which is exactly what `reference` holds and why `D-08` exempts it from `tenant_id`
(`02-data-model.md:16`). A `core.action` with a `tenant_id` would have meant 88 identical rows
per tenant that nobody can edit.

Two consequences for the migration:

- The script is `reference/V0NN__action.sql` and is numbered **below** the `core` scripts in its batch — `migration/README.md:17-31`
- `core.role_action.action_code` references `reference.action(code)` **across schemas**. That is legal, and `app_user` already holds read access on `reference` (`02-data-model.md:398`), so no new grant is needed for the read — but the foreign key must be written schema-qualified on both sides, per `migration/README.md:33-50`

RLS and the `tenant_isolation` policy in the exact `CASE` form on the three tenant-scoped
tables — `migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../authz/RoleServiceTest.java` | a role cannot hold an unknown action; deleting a granted role is refused; system roles cannot be edited |
| Integration | `core/.../authz/RoleRlsIT.java` | tenant A cannot read or grant tenant B's roles as `app_user` |
| Integration | `core/.../authz/ActionCatalogueIT.java` | every action code referenced by a seeded system role exists in the catalogue |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in role role_action user_role; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) AS actions FROM reference.action;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT DISTINCT module FROM reference.action ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on the three tenant tables | `t` three times |
| Action count | non-zero, and matching the seed script |
| Modules | `core`, `hrms`, `payroll` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The action script is written under `core/` out of habit and CI gate B rejects the branch | medium | It belongs in `reference/`, numbered below `core` in its batch — `migration/README.md:17-31` |
| The cross-schema foreign key is written unqualified and lands in the wrong schema | medium | `migration/README.md:43-50` — a missing schema prefix is silent and permanent |
| Actions are created at runtime, as Payroll does | medium | The catalogue is read-only over the API and seeded by migration |
| Roles drift to global because HRMS's are | medium | `tenant_id` on every role; `RoleRlsIT` asserts it |
| The seeded seven and the realm's three are treated as one system and drift apart | **medium — decision 2 created this** | The realm role gates nothing; `W-11.2` checks actions only, and `ActionCatalogueIT` asserts every seeded role's actions exist |
| The 88 HRMS actions are copied wholesale without review | medium | The seed is written against the new platform's endpoints, not transcribed from `ActionServiceImpl.java:152-290` |
| A permission check appears here "just to test it" | medium | Named in **Out of scope**; `W-11.2` owns enforcement |

## 10. Rollback

Nothing is deployed. All scripts are additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | on `role`, `role_action`, `user_role`. **`reference.action` is inside the `D-08` exemption**, so it correctly has neither |
| Flyway only, `ddl-auto` nowhere | four scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | `tenant_id` leads every index on the three scoped tables |
| Expand / contract | new tables only |
| No module references another module | `core` only; the `module` column on an action is a label, not a dependency |

## 12. Gap inventory

| ID | Decision |
|---|---|
| HRMS roles global, not tenant-scoped (`role` entity has no org column) | **Fixed.** Every role carries `tenant_id` |
| Permission by URL prefix and role name (`SecurityConfig.java:39-43`) | **Fixed by replacement.** Checks are action-based; `W-11.2` enforces |
| Actions created at runtime (`ActionServiceImpl.java:24-34`, Payroll) | **Fixed.** Seeded by migration, read-only over the API |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Where does the action catalogue live? | **`reference.action`** — settled 2026-09-22. `D-08`'s exemption applies; no tenant column, no duplication |
| 2 | Which system roles does every tenant get? | **A richer set**, settled 2026-09-22 against my recommendation of three: `platform-admin`, `tenant-admin`, `hr`, `manager`, `payroll-officer`, `finance`, `employee` |

**Decision 2 means the database roles no longer map one-to-one to the login roles, and the
spec must say how they relate rather than leave it implied:**

| Layer | Holds | Decides |
|---|---|---|
| Keycloak realm role (`W-10`) | three coarse values — `platform-admin`, `tenant-admin`, `employee` | which broad kind of user this is, carried in the token |
| `core.role` (this ticket) | seven seeded roles, plus any the tenant creates | what the user may actually do, resolved per tenant from the database |

The realm role is **not** a permission. It never gates an endpoint; `W-11.2` gates on actions
only. Its single job is to distinguish your staff from a customer's staff from an employee
before any tenant is bound.

That separation is what stops the two becoming parallel permission systems — which is exactly
what HRMS has today, where `SecurityConfig.java:39-43` gates URLs on role names while a
separate action table goes unused.
