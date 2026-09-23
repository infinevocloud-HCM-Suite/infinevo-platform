-- Migration: V014__employee_org_columns.sql
-- Description: W-14.1 core.employee — the expand half: three nullable foreign keys to the org masters created in V011-V013

-- All three columns are NULLABLE, and that is deliberate, not an oversight.
-- W-13.1 shipped core.employee without them by design (V010__employee.sql, "Deliberately absent"),
-- so employees created before this ticket carry no department, designation or work location. Making
-- any of these NOT NULL would break those existing rows — spec §9 names exactly that as a medium
-- risk, and spec §6 names nullable-plus-no-destructive-step as the expand half of expand/contract.
-- If a NOT NULL is ever wanted, it is a later contract step after a backfill, never an edit here
-- (migration/README.md §forward-only — no destructive steps).
ALTER TABLE core.employee
    ADD COLUMN department_id UUID NULL REFERENCES core.department(id),
    ADD COLUMN designation_id UUID NULL REFERENCES core.designation(id),
    ADD COLUMN work_location_id UUID NULL REFERENCES core.work_location(id);

-- This script creates no table, so it needs no ENABLE ROW LEVEL SECURITY and no tenant_isolation
-- policy of its own. core.employee already has both, added in V010__employee.sql where the table
-- was created, and they cover these columns like every other. The omission is correct — please do
-- not "fix" it; a second policy on the table would be a defect (migration/README.md §row-level
-- security: exactly one isolation policy per table).

-- Note that the foreign keys alone do not prevent a cross-tenant reference: migration_user owns the
-- tables and bypasses RLS. The check has to run as app_user, which is what W-14.1's
-- EmployeeAssignmentIT does (spec §7).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus
-- lookup columns). One per new column: the queries these serve are "every employee in this
-- department / designation / location", which is also what the delete-is-refused-while-assigned
-- check in spec §4 runs for each of the three masters. Each is a plain, non-unique index — many
-- employees share one department. No index on the columns alone: tenant_id must lead, and a lookup
-- by id without a tenant is not a query this schema supports.
CREATE INDEX idx_employee_tenant_department_id ON core.employee (tenant_id, department_id);
CREATE INDEX idx_employee_tenant_designation_id ON core.employee (tenant_id, designation_id);
CREATE INDEX idx_employee_tenant_work_location_id ON core.employee (tenant_id, work_location_id);
