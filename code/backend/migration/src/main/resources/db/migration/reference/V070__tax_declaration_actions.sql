-- Migration: V070__tax_declaration_actions.sql
-- Description: W-32.1 reference.action tax declaration codes and grants for system roles

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Actions in reference.action
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.action (code, name, module, description) VALUES
    ('payroll.tax_declaration.read', 'View tax declarations', 'payroll',
        'Read any employee''s tax declarations across the tenant'),
    ('payroll.tax_declaration.manage', 'Manage tax declarations', 'payroll',
        'Edit, submit, lock or unlock any tax declaration'),
    ('payroll.tax_declaration.read_own', 'View own tax declaration', 'payroll',
        'Read your own tax declarations'),
    ('payroll.tax_declaration.declare_own', 'Declare own tax savings', 'payroll',
        'Declare, save, submit and reopen own tax savings')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Grants for existing tenants
-- ─────────────────────────────────────────────────────────────────────────────

-- platform-admin and tenant-admin get all non-provisioning actions
INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, a.code
FROM core.role r
CROSS JOIN (
    VALUES
        ('payroll.tax_declaration.read'),
        ('payroll.tax_declaration.manage'),
        ('payroll.tax_declaration.read_own'),
        ('payroll.tax_declaration.declare_own')
) AS a(code)
WHERE r.code IN ('platform-admin', 'tenant-admin')
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;

-- payroll-officer holds read and manage
INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, a.code
FROM core.role r
CROSS JOIN (
    VALUES
        ('payroll.tax_declaration.read'),
        ('payroll.tax_declaration.manage')
) AS a(code)
WHERE r.code = 'payroll-officer'
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;

-- employee holds read_own and declare_own
INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, a.code
FROM core.role r
CROSS JOIN (
    VALUES
        ('payroll.tax_declaration.read_own'),
        ('payroll.tax_declaration.declare_own')
) AS a(code)
WHERE r.code = 'employee'
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Dedicated seed function and trigger so new tenants also receive these grants
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.seed_tax_declaration_roles(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin', 'payroll.tax_declaration.read'),
        ('platform-admin', 'payroll.tax_declaration.manage'),
        ('platform-admin', 'payroll.tax_declaration.read_own'),
        ('platform-admin', 'payroll.tax_declaration.declare_own'),
        ('tenant-admin',   'payroll.tax_declaration.read'),
        ('tenant-admin',   'payroll.tax_declaration.manage'),
        ('tenant-admin',   'payroll.tax_declaration.read_own'),
        ('tenant-admin',   'payroll.tax_declaration.declare_own'),
        ('payroll-officer', 'payroll.tax_declaration.read'),
        ('payroll-officer', 'payroll.tax_declaration.manage'),
        ('employee',        'payroll.tax_declaration.read_own'),
        ('employee',        'payroll.tax_declaration.declare_own')
    ) AS grants(role_code, action_code)
    JOIN core.role r
      ON r.tenant_id = p_tenant_id
     AND r.code = grants.role_code
     AND r.is_system = true
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_tax_declaration_roles(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_seed_tax_declaration_roles()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.seed_tax_declaration_roles(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_seed_tax_declaration_roles() FROM PUBLIC;

CREATE TRIGGER tenant_seed_tax_declaration_roles
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_seed_tax_declaration_roles();

SELECT core.seed_tax_declaration_roles(t.tenant_id) FROM core.tenant t;

