# W-05 — Postgres & Schemas

| Field | Value |
|---|---|
| **Work item** | `W-05` · issue [#6](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/6) |
| **Kind** | **Infra / Data** — database server, schema isolation, security roles |
| **Stream / track** | Stream A — Foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | S · DATA |
| **Owner** | SayInfi |
| **Blocked by** | — (`W-01` merged) |
| **Blocks** | `W-06` Flyway migrations · `W-07` Tenant & RLS foundation · `W-08`–`W-13` feature tables |
| **Capabilities** | `PLAT-05` database isolation & security |
| **Decisions** | `D-08` reference schema · `D-09` no ddl-auto / Flyway only · `D-38` Java 21 |
| **Gaps addressed** | `DEBT-002` ddl-auto · `BUG-004` uncontrolled schema drift |
| **Status** | **Draft — Pending Founder Approval** |
| **Approved by** | Pending Founder Approval |
| **Approved on** | — |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

In the legacy codebase, two MySQL databases (`HRMS_Backend` and `Payroll-Bend-SBoot`) are managed via Hibernate `spring.jpa.hibernate.ddl-auto=update` without database migration tooling (`DEBT-002`, `BUG-004`). Database connections execute as the superuser/owner account, exposing the entire database structure to application-level DDL queries and schema drift.

This architecture presents four critical flaws:
1. **Uncontrolled Schema Drift:** Hibernate auto-generates and alters tables on startup in all environments, resulting in schema variance between local, staging, and production environments without an audit trail or rollback mechanism.
2. **Missing Schema Boundaries:** Domain models are split across separate database instances rather than logical schema boundaries within a unified platform database.
3. **Privilege Over-Granting:** The application connects as database owner, allowing application bugs or SQL injection vulnerabilities to execute destructive DDL commands (`DROP TABLE`, `ALTER TABLE`).
4. **Bypassed Row-Level Security (RLS) Controls:** In PostgreSQL, table owners and superusers bypass Row-Level Security policies by default (`BYPASSRLS`). Connecting the application as schema owner neutralises database-enforced multi-tenant isolation.

**Baseline, measured 2026-09-15 on local PostgreSQL:**

| Check | Result |
|---|---|
| Platform database `infinevo` | Not provisioned / unstandardised |
| Target schemas (`core`, `hrms`, `payroll`, `reference`) | Missing |
| Database roles (`infinevo_owner`, `migration_user`, `app_user`) | Missing |
| Application DDL boundary | Unenforced (`app_user` role absent) |

---

## 2. Scope

**In scope**

- **Database Provisioning Specification:** Standardization of PostgreSQL 16 server configuration and creation of the `infinevo` platform database.
- **Four-Schema Architecture:** Creation and isolation of four distinct schemas: `core`, `hrms`, `payroll`, and `reference`.
- **Three-Role Security Model:** Definition and creation of three distinct database roles: `infinevo_owner`, `migration_user`, and `app_user`.
- **Strict Permission Hierarchy & DDL Block:** Configuration of explicit GRANT/REVOKE privileges ensuring `app_user` has DML access (SELECT, INSERT, UPDATE, DELETE) on tenant schemas and read-only access on `reference`, while being strictly denied DDL privileges (CREATE, ALTER, DROP, TRUNCATE).
- **Default Privilege Automation:** Automated `ALTER DEFAULT PRIVILEGES` setup so future tables created by `migration_user` automatically inherit correct DML permissions for `app_user`.

**Out of scope**

- **Flyway runner configuration & migration scripts.** Owned by `W-06` (#7).
- **Tenant entity, RLS policies, and session variables.** Owned by `W-07` (#8).
- **Employee, Payroll, or HRMS table migrations.** Owned by `W-08` through `W-13`.
- **Azure PostgreSQL Flexible Server IaC scripts.** Owned by `W-48` / `W-50`.
- **Application source code modifications.** Owned by feature tickets.

---

## 3. Database Architecture

The target database architecture unifies platform data into a single PostgreSQL 16 database named `infinevo` divided into four logical schemas.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Database: infinevo                                    │
│                                                                                 │
│ ┌───────────────────────────┐                 ┌───────────────────────────────┐ │
│ │          core             │                 │             hrms              │ │
│ │ Tenant, Subscription,     │                 │ Clock sessions, Projects,     │ │
│ │ User, Employee master,    │                 │ Timesheets                    │ │
│ │ Leave, Org Structure      │                 │                               │ │
│ └───────────────────────────┘                 └───────────────────────────────┘ │
│ ┌───────────────────────────┐                 ┌───────────────────────────────┐ │
│ │         payroll           │                 │           reference           │ │
│ │ Components, Pay runs,     │                 │ Shared National Rules         │ │
│ │ Statutory overrides,      │                 │ Tax slabs, HRA, Lookups       │ │
│ │ Tax declarations          │                 │ (NO tenant_id column)         │ │
│ └───────────────────────────┘                 └───────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Schema Responsibilities & Ownership

| Schema | Purpose | Tenant Column Requirement | Ownership |
|---|---|---|---|
| `core` | Shared tenant master, user accounts, employee profiles, org structure, leave engine, platform audit logs | `tenant_id` mandatory on all tables | Owned by `infinevo_owner`; DDL by `migration_user` |
| `hrms` | Time tracking, clock sessions, project assignments, timesheet entries | `tenant_id` mandatory on all tables | Owned by `infinevo_owner`; DDL by `migration_user` |
| `payroll` | Salary structures, statutory configurations, pay runs, tax declarations, reimbursement claims | `tenant_id` mandatory on all tables | Owned by `infinevo_owner`; DDL by `migration_user` |
| `reference` | Shared national statutory data (tax slabs, HRA rules, bank lookups, country codes) | **NO `tenant_id` column by design** (`D-08`) | Owned by `infinevo_owner`; DDL by `migration_user` |

---

## 4. Database Roles & Security Boundaries

Three database roles enforce strict separation of duties between server administration, schema migration, and application execution.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               DATABASE ROLES                                    │
├───────────────────┬───────────────────────────────┬─────────────────────────────┤
│   infinevo_owner  │        migration_user         │          app_user           │
│  (Database Owner) │      (Migration Engine)       │  (Application Runtime)      │
├───────────────────┼───────────────────────────────┼─────────────────────────────┤
│ • Superuser/Admin │ • Executed by Flyway CI/CD    │ • Executed by Web/Worker    │
│ • Creates DB &    │ • Owns schema migrations      │ • DML only (SELECT, INSERT, │
│   Schemas         │ • Runs CREATE/ALTER/DROP      │   UPDATE, DELETE)           │
│ • Manages roles   │ • Bypasses RLS during DDL     │ • DENIED ALL DDL            │
│                   │                               │ • RLS Enforced per tenant   │
└───────────────────┴───────────────────────────────┴─────────────────────────────┤
```

### Role Specifications

1. `infinevo_owner` **(Database Owner & Administrative Role)**
   - **Responsibility:** Provisions the `infinevo` database, creates the four schemas, and configures role privileges.
   - **Security Boundary:** Used exclusively during database provisioning and maintenance. Never embedded in application connection pools or pipeline secrets.

2. `migration_user` **(Flyway Migration Execution Role)**
   - **Responsibility:** Executes DDL migration scripts (`V1__...sql`) during CI/CD deployment pipelines (`W-06`).
   - **Security Boundary:** Has full DDL privileges (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`) on all schemas. Bypasses RLS (`BYPASSRLS`) to perform structural alterations and data backfills.

3. `app_user` **(Application Runtime Role)**
   - **Responsibility:** Used by `app` (web API), `worker` (batch processing), and integration test suites (`W-04`).
   - **Security Boundary:** Denied all DDL permissions. Granted DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on `core`, `hrms`, and `payroll` schemas, and read-only (`SELECT`) on `reference`. Subject to database RLS policies (`W-07`).

---

## 5. Permissions Matrix

The following matrix specifies the exact privilege boundaries enforced across all four schemas:

| Schema | Role | SELECT | INSERT | UPDATE | DELETE | CREATE (DDL) | ALTER (DDL) | DROP (DDL) | GRANT (DCL) |
|---|---|---|---|---|---|---|---|---|---|
| `core` | `infinevo_owner` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `core` | `migration_user` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `core` | `app_user` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `hrms` | `infinevo_owner` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `hrms` | `migration_user` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `hrms` | `app_user` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `payroll` | `infinevo_owner` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `payroll` | `migration_user` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `payroll` | `app_user` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `reference` | `infinevo_owner` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `reference` | `migration_user` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `reference` | `app_user` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 6. Integration with Future Features

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   W-05   │ ──► │   W-06   │ ──► │   W-07   │ ──► │   W-13   │
│ Postgres │     │  Flyway  │     │ Tenant & │     │ Employee │
│ Schemas  │     │ Runner   │     │   RLS    │     │ Entity   │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
```

1. **Relationship with `W-06` (Flyway Migrations):**
   - `W-05` provisions the underlying database, schemas, and `migration_user` account.
   - `W-06` attaches Flyway to `infinevo`, creating the `flyway_schema_history` table using `migration_user` and establishing SQL migration conventions (`db/migration/{schema}/`).

2. **Relationship with `W-07` (Tenant & RLS Foundation):**
   - `W-05` establishes the `app_user` non-owner role boundary.
   - `W-07` applies PostgreSQL Row-Level Security policies (`CREATE POLICY ... ON ...`) to tables in `core`, `hrms`, and `payroll` for `app_user`, enforcing `tenant_id = current_setting('app.current_tenant_id')`.

3. **Relationship with `W-13` (Employee Master Migration):**
   - `W-13` delivers the first major domain table migration (`core.employee`), executed by `migration_user` (`W-05`/`W-06`) and queried by `app_user` (`W-05`/`W-07`).

---

## 7. Proving the Security Boundaries

To prove that the permission boundaries are real, the following deliberate failure scenarios must be verified on a throwaway test script:

| # | Deliberate Action | Expected System Behavior |
|---|---|---|
| 1 | `app_user` executes `CREATE TABLE core.test_table (id INT);` | **FAILURE** — `ERROR: permission denied for schema core` |
| 2 | `app_user` executes `ALTER TABLE core.tenant ADD COLUMN test TEXT;` | **FAILURE** — `ERROR: must be owner of table tenant` |
| 3 | `app_user` executes `DROP SCHEMA hrms;` | **FAILURE** — `ERROR: must be owner of schema hrms` |
| 4 | `app_user` executes `INSERT INTO reference.country VALUES ('XX', 'Test');` | **FAILURE** — `ERROR: permission denied for table country` |
| 5 | `app_user` executes `SELECT * FROM core.tenant;` | **SUCCESS** — Query succeeds (returns zero rows before W-07 data migration) |
| 6 | `migration_user` executes `CREATE TABLE core.test_migration (id INT);` | **SUCCESS** — Table created successfully |

---

## 8. Verification

Run by the **verifier** on a clean checkout or local PostgreSQL instance.

```bash
# 1 - Verify database connection as infinevo_owner and inspect schemas
psql -U infinevo_owner -d infinevo -c "\dn"
# Expected output: core, hrms, payroll, reference schemas listed

# 2 - Verify app_user cannot run DDL on any schema (MUST FAIL with exit code 1)
psql -U app_user -d infinevo -c "CREATE TABLE core.should_fail(id int);" || echo "DDL_BLOCKED_SUCCESS"
# Expected output: ERROR: permission denied for schema core / DDL_BLOCKED_SUCCESS

# 3 - Verify app_user cannot insert into reference schema (MUST FAIL with exit code 1)
psql -U app_user -d infinevo -c "CREATE TABLE reference.should_fail(id int);" || echo "REF_DDL_BLOCKED_SUCCESS"
# Expected output: ERROR: permission denied for schema reference / REF_DDL_BLOCKED_SUCCESS

# 4 - Verify migration_user CAN run DDL on core schema (MUST SUCCEED)
psql -U migration_user -d infinevo -c "CREATE TABLE core.verifier_test(id int); DROP TABLE core.verifier_test;"
# Expected output: CREATE TABLE / DROP TABLE (exit code 0)
```

| Check | Expected Output | Result |
|---|---|---|
| 4 Schemas Created | `core`, `hrms`, `payroll`, `reference` exist in `infinevo` DB | |
| 3 Roles Provisioned | `infinevo_owner`, `migration_user`, `app_user` exist | |
| `app_user` DDL Block | `ERROR: permission denied for schema core` (Exit 1) | |
| `app_user` Read-Only `reference` | Write/DDL denied on `reference` schema | |
| `migration_user` DDL Success | DDL executes cleanly for schema migrations | |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Developer or test harness attempts to connect as `infinevo_owner` | **Medium** | Enforce non-owner connection string checks in `AbstractIntegrationTest` (`W-04`) and Spring `application-local.yml` (`W-02`). |
| New tables created by `migration_user` forget to grant DML to `app_user` | **Medium** | Execute `ALTER DEFAULT PRIVILEGES IN SCHEMA core, hrms, payroll GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;` during initial schema setup. |
| Application attempts to write to national statutory tables in `reference` | **Low** | `app_user` is explicitly revoked `INSERT`, `UPDATE`, and `DELETE` on `reference` schema; write attempts throw SQL exceptions. |

---

## 10. Rollback

If database role or schema provisioning fails:
1. Revoke privileges and drop database roles (`DROP ROLE IF EXISTS app_user, migration_user;`).
2. Drop schemas (`DROP SCHEMA IF EXISTS core, hrms, payroll, reference CASCADE;`).
3. Drop platform database (`DROP DATABASE IF EXISTS infinevo;`).
No production data exists; rollback is completely non-destructive.

---

## 11. Done When

1. Database `infinevo` is provisioned with four schemas: `core`, `hrms`, `payroll`, `reference`.
2. Three database roles exist: `infinevo_owner`, `migration_user`, and `app_user`.
3. `app_user` is granted DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on `core`, `hrms`, `payroll`, and `SELECT` on `reference`.
4. `app_user` is strictly denied DDL permissions (`CREATE`, `ALTER`, `DROP`) across all four schemas.
5. `migration_user` is granted DDL permissions across all four schemas to support Flyway migrations (`W-06`).
6. Default privileges (`ALTER DEFAULT PRIVILEGES`) are configured so future tables created by `migration_user` automatically grant DML to `app_user`.
7. All 6 verification steps in §8 pass on a clean environment.
