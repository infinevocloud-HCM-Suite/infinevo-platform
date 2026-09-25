-- Migration: V040__report_definition.sql
-- Description: W-23.1 core.report_definition — a named, tenant-scoped export definition over one registered report source, with row-level security isolation, plus the three system definitions seeded per tenant

CREATE TABLE core.report_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    -- A ReportSource.code() — a bean the code knows how to query — never free SQL. A stored query
    -- would be a report builder and an injection surface in one column (W-23.1 spec section 6).
    source VARCHAR(32) NOT NULL,
    -- The chosen columns, by name, from that source's allow-list. A JSON array of strings.
    columns JSONB NOT NULL,
    default_filters JSONB NULL,
    format VARCHAR(8) NOT NULL,
    -- The second gate on an export: an export of salary data must not reach someone who cannot see
    -- salary data on screen. A real action, so a typo cannot silently open a definition to nobody.
    required_action VARCHAR(64) NOT NULL REFERENCES reference.action(code),
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT report_definition_format_check CHECK (format IN ('CSV', 'XLSX')),
    CONSTRAINT report_definition_columns_check CHECK (jsonb_typeof(columns) = 'array' AND jsonb_array_length(columns) > 0)
);

-- No money column. Exported amounts are formatted from numeric(19,4) sources at export time; nothing
-- here stores an amount.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX uk_report_definition_tenant_code ON core.report_definition (tenant_id, code);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.report_definition ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.report_definition
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- The three system definitions every tenant starts with (spec section 13, decision 1: seeded system
-- definitions plus the tenant's own). One per Core source registered today — employees, the org
-- masters and the audit log (decision D3). A leave-balance and a pay-input source arrive with W-16.2 and
-- W-19, each as one ReportSource bean plus a definition of its own.
--
-- The column names here are the sources' allow-lists (EmployeeReportSource, OrgMasterReportSource,
-- AuditLogReportSource). ThreeConsumersIT exports all three, so a name that drifts fails there.
--
-- SECURITY DEFINER, owned by migration_user, so it can seed a tenant's rows without a tenant bound —
-- the pattern core.seed_system_roles set in V022. Not callable by the application.
CREATE OR REPLACE FUNCTION core.seed_report_definitions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.report_definition
        (tenant_id, code, name, source, columns, format, required_action, is_system)
    VALUES
        (p_tenant_id, 'employees', 'Employees', 'employee',
         '["employee_number", "first_name", "middle_name", "last_name", "gender", "date_of_joining",
           "termination_date", "status", "work_email", "mobile", "department", "designation",
           "work_location"]'::jsonb,
         'XLSX', 'core.employee.export', true),
        (p_tenant_id, 'org-masters', 'Departments, designations and work locations', 'org_master',
         '["kind", "code", "name", "active"]'::jsonb,
         'XLSX', 'core.org.read', true),
        (p_tenant_id, 'audit-log', 'Audit log', 'audit_log',
         '["occurred_at", "actor", "operation", "entity_schema", "entity_table", "entity_id",
           "changed_columns"]'::jsonb,
         'CSV', 'core.audit.read', true)
    ON CONFLICT (tenant_id, code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_report_definitions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_seed_report_definitions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.seed_report_definitions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_seed_report_definitions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_report_definitions
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_seed_report_definitions();

-- Tenants that exist before this script ran get theirs too.
SELECT core.seed_report_definitions(t.tenant_id) FROM core.tenant t;

-- ─────────────────────────────────────────────────────────────────────────────
-- Who may run exports beyond the two admin roles — W-23.1's grant, in its own script (W-11.3 spec
-- section 2), by the pattern V037 gives for documents and for the same reason:
--
--   hr        core.report.read   HR already holds the action each seeded definition requires
--                                (core.employee.export, core.org.read, core.audit.read), so HR can
--                                run all three; core.report.read alone runs nothing
--
-- core.report.manage stays with the admin roles: defining a report decides what leaves the system.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_report_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('hr', 'core.report.read')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_report_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_report_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_report_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_report_actions() FROM PUBLIC;

-- Sorts after tenant_seed_system_roles (V022), so the hr role exists when this fires.
CREATE TRIGGER tenant_seed_system_roles_report
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_report_actions();

SELECT core.grant_report_actions(t.tenant_id) FROM core.tenant t;
