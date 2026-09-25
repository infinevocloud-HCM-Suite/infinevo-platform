# Feature: Catalogue correction — Core codes out of the `hrms.*` prefix

| Field | Value |
|---|---|
| **Feature ID** | `W-11.3` · from ticket `W-11` · `12-core-contracts.md` §4 |
| **Promoted to** | `docs/target-state/features/W-11-3-catalogue-correction.md` on branch `W-11-3-catalogue-correction` — **`W-11-3` with hyphens**, never `W-11.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/migration`, `code/backend/core` (tests and two comments only) |
| **Related gaps** | none — corrects `W-11.1`'s own seed |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing — `W-11.1` is on main |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `migration` (`core` changes are tests and comments) | 1 |
| Flyway migration | 1 script, no new table | 1 |
| Externally testable behaviour | after the script, `GET /api/v1/actions` lists `core.leave.*`, `core.attendance.*`, `core.holiday.*` and the 24 new codes, and no `hrms.leave|attendance.*|holiday.*` except `hrms.attendance.mark` | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

The catalogue forces the code prefix to equal the `module` column (`V020__action.sql:22`). A
module filter, when `W-12.2` adds one, strips every `hrms.*` code from a Payroll-only tenant. But
leave, holidays and administrator-entered attendance are Core features (`12-core-contracts.md:120-132`,
decision 1 at `:166`), and `V020` filed their 14 codes under `hrms` (`V020__action.sql:80-86,90-94,105-106`).

Two more defects sit in the same seed:

- 24 Core codes the specs cite do not exist yet (`12-core-contracts.md:128`).
- `core.seed_system_roles` grants `core.tenant.provision` to the tenant-seeded `platform-admin`
  (`V022__role_action.sql:86-87`), though the catalogue says it is "never granted to a customer
  role" (`V020__action.sql:43-44`) and every tenant gets that role (`V022__role_action.sql:74`).

`reference.action.code` is a primary key (`V020__action.sql:11`) referenced by
`core.role_action.action_code` (`V022__role_action.sql:9`), so a rename is not an `UPDATE` of one
column.

## 2. Scope

**In scope**

- Rename 14 codes `hrms.*` → `core.*` (list in §6), moving existing grants with them
- Add the 24 codes in `12-core-contracts.md:128`
- Remove `core.tenant.provision` from the tenant-seeded `platform-admin` grant, in the function and in every existing tenant
- Replace `core.seed_system_roles` so a new tenant gets the corrected list; re-run the backfill
- Fix the tests and comments in `core` that name the old codes or the old grant

**Out of scope**

- Who holds `core.tenant.provision` after this — `W-12.1` (provisioning) decides
- Granting the 24 new codes to `hr`, `manager`, `employee` — each feature ticket grants its own code to its functional role in its own script; here they reach the two admin roles only, which the seed function does on its own (`V022__role_action.sql:60-62`)
- `hrms.attendance.mark`, `hrms.timesheet.*` — stay `hrms` (`12-core-contracts.md:127`)

## 3. Flow

```
Flyway (migration_user) --> V025: INSERT core.* codes --> UPDATE core.role_action --> DELETE hrms.* codes
                        --> CREATE OR REPLACE core.seed_system_roles --> DELETE provision grant
                        --> SELECT core.seed_system_roles(t) FROM core.tenant t
```

## 4. Backend changes

No production Java changes. No built controller names a renamed code: every `@RequiresAction`
on main is `core.role.*`, `core.employee*.*`, `core.org.*` or `core.audit.read`
(`ActionController.java:30`, `RoleController.java:38-60`, `UserRoleController.java:31`,
`EmployeeController.java:59-80`, `EmployeeDetailController.java:94-152`,
`DepartmentController.java:42-67`, `DesignationController.java:36-61`,
`WorkLocationController.java:40-65`, `shared/.../AuditController.java:26`). `W-39.1` names
`hrms.attendance.*` in its spec only (`W-39-1-attendance-capture.md:94-96`); it is not built, and
`12-core-contracts.md:131-132` already says it switches to `core.attendance.*` once this lands.

| File | Change |
|---|---|
| `core/.../authz/RoleServiceImpl.java:82,275-277` | Comments say `platform-admin` holds `core.tenant.provision`. Reword: the role is still refused from inside a tenant; provisioning grants the action elsewhere (`W-12.1`) |
| `core/src/test/.../authz/RoleRlsIT.java:215-230` | Runs against the real catalogue; `hrms.leave.read`, `hrms.leave.approve`, `hrms.holiday.read` → `core.*` |
| `core/src/test/.../authz/RoleServiceTest.java:50,281-293` | Mocked catalogue; rename for consistency |
| `core/src/test/.../authz/ActionCatalogueIT.java:86-89` | Asserts `platform-admin` = whole catalogue and `tenant-admin` = size − 1. Both become catalogue minus `core.tenant.provision` |
| `migration/src/test/.../RoleCatalogueIT.java:128-139` | Same assertion in SQL; same change. The `r.code <> 'platform-admin'` filter at `:137` becomes "no role at all" |

**API contract** — unchanged. `GET /api/v1/actions` (`core.role.read`) returns the corrected list.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V025__catalogue_correction.sql` — **reserved 2026-09-25**; `W-13.4` takes `V026`, `W-09.1` takes `V027` | `reference.action`, `core.role_action` (rows), `core.seed_system_roles` (function) | rows carry `tenant_id`; no new table | forward-only |

Filed under `core/` because it replaces a `core` function; the `reference` statements are
schema-qualified as `migration/README.md` §"Always name the schema" requires.

```sql
-- 1. expand: the 14 renamed codes, same name and description, module 'core'
INSERT INTO reference.action (code, name, module, description)
SELECT regexp_replace(code, '^hrms\.', 'core.'), name, 'core', description
FROM reference.action
WHERE code IN ('hrms.leave.apply','hrms.leave.read_own','hrms.leave.read_team','hrms.leave.read',
               'hrms.leave.approve','hrms.leave_type.manage','hrms.leave_balance.manage',
               'hrms.attendance.read_own','hrms.attendance.read_team','hrms.attendance.read',
               'hrms.attendance.manage','hrms.attendance.export','hrms.holiday.read','hrms.holiday.manage');
-- 2. the 24 new codes (12-core-contracts.md:128), one VALUES row each, module 'core':
--    reporting_line.manage · approval_definition.manage · approval.read/decide/delegate/manage
--    lop_policy.read/manage · leave.manage · pay_input.read/write/lock · overtime.read/manage
--    notification_template.manage · reminder_rule.manage · document.read/read_own/upload/delete
--    report.read/manage · report_schedule.manage · job.read
-- 3. move the grants (migration_user owns core.role_action and bypasses RLS — README:105)
UPDATE core.role_action SET action_code = regexp_replace(action_code, '^hrms\.', 'core.'),
       updated_at = CURRENT_TIMESTAMP, updated_by = 'W-11.3'
WHERE action_code IN (/* the same 14 */);
-- 4. contract
DELETE FROM reference.action WHERE code IN (/* the same 14 */);
-- 5. CREATE OR REPLACE FUNCTION core.seed_system_roles — V022:65-185 verbatim except:
--    platform-admin: FROM reference.action a WHERE a.code <> 'core.tenant.provision'
--    every hrms.leave|attendance(not mark)|holiday grant in the VALUES list → core.*
-- 6. the grant no tenant may hold
DELETE FROM core.role_action ra USING core.role r
WHERE r.tenant_id = ra.tenant_id AND r.id = ra.role_id AND r.is_system
  AND r.code = 'platform-admin' AND ra.action_code = 'core.tenant.provision';
-- 7. backfill — idempotent (V022:60-62)
SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t;
```

Steps 1→3→4 in one script is acceptable here: nothing on main reads the old codes except the
`V022` seed, which step 5 replaces, and the whole script runs in one Flyway transaction.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | no new table |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| Money as `Money` / `BigDecimal` | no money column |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | no new index; `idx_role_action_tenant_role_action` (`V022:24`) still covers the moved rows |
| Expand / contract | expand (1–2), move (3), contract (4) in one script — justified above |
| No module references another | `core` only |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `migration/.../RoleCatalogueIT.java` (extend) | no `hrms.leave|holiday.*` and no `hrms.attendance.*` but `mark` remain; the 24 new codes exist; `count(*) FROM core.role_action WHERE action_code = 'core.tenant.provision'` is `0`; `hr` holds `core.leave.read`, `employee` holds `core.leave.apply`; `regexp` `module = split_part(code,'.',1)` still holds (`:76`) |
| Integration | `core/.../authz/ActionCatalogueIT.java` (fix `:86-89`) | admin roles = catalogue minus provision; a tenant inserted after the script gets the corrected grants |
| Integration | `core/.../authz/RoleRlsIT.java` (fix `:215-230`) | custom role created with `core.leave.read` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FILTER (WHERE code LIKE 'hrms.leave.%' OR code LIKE 'hrms.holiday.%' OR (code LIKE 'hrms.attendance.%' AND code <> 'hrms.attendance.mark')) AS old_left, count(*) FILTER (WHERE code LIKE 'core.%') AS core_codes FROM reference.action;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM core.role_action WHERE action_code = 'core.tenant.provision';"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| `old_left` / `core_codes` | `0` / `61` (23 in `V020:43-77` + 14 renamed + 24 added) |
| provision grants | `0` |
| Suite | green, no skips; `RoleCatalogueIT`, `ActionCatalogueIT`, `RoleRlsIT` run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A branch in flight hard-codes an `hrms.leave|holiday|attendance.*` string | low — none on main (§4) | `RoleRlsIT` fails loudly on an unknown code (`RoleRlsIT.java:222-224`) |
| Nobody holds `core.tenant.provision` until `W-12.1` | certain | No provisioning endpoint exists yet (`12-core-contracts.md:53` is spec); nothing loses access |
| `W-09.1` / `W-13.4` take the same version number | medium | Flyway fails loudly on a duplicate (`migration/README.md:30`); renumber at rebase |

## 10. Rollback

Revert the test and comment commit. The script stays applied; Flyway is forward-only. The old
codes are gone, so a reverted `RoleRlsIT` would fail — do not revert it without a second script
that re-inserts them.
