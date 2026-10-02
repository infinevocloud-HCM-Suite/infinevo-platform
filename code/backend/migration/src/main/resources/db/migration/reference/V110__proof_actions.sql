-- Migration: V110__proof_actions.sql
-- Description: W-34.1 reference.action proof-of-investment codes and grants for system roles

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Actions in reference.action
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.action (code, name, module, description) VALUES
    ('payroll.proof.read', 'View proofs of investment', 'payroll',
        'Read any employee''s proof of investment across the tenant'),
    ('payroll.proof.review', 'Review proofs of investment', 'payroll',
        'Decide the items and the outcome of a submitted proof of investment'),
    ('payroll.proof.read_own', 'View own proof of investment', 'payroll',
        'Read your own proof of investment and download its files'),
    ('payroll.proof.submit_own', 'Submit own proof of investment', 'payroll',
        'Attach files to your declared items and submit the proof')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Grants for existing tenants
--
-- A dedicated seed function, as V070: core.seed_system_roles is redefined wholesale by
-- several scripts, and a later copy taken from an older body drops the grants added in between.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.seed_proof_roles(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin',  'payroll.proof.read'),
        ('platform-admin',  'payroll.proof.review'),
        ('platform-admin',  'payroll.proof.read_own'),
        ('platform-admin',  'payroll.proof.submit_own'),
        ('tenant-admin',    'payroll.proof.read'),
        ('tenant-admin',    'payroll.proof.review'),
        ('tenant-admin',    'payroll.proof.read_own'),
        ('tenant-admin',    'payroll.proof.submit_own'),
        ('payroll-officer', 'payroll.proof.read'),
        ('hr',              'payroll.proof.read'),
        ('hr',              'payroll.proof.review'),
        ('employee',        'payroll.proof.read_own'),
        ('employee',        'payroll.proof.submit_own')
    ) AS grants(role_code, action_code)
    JOIN core.role r
      ON r.tenant_id = p_tenant_id
     AND r.code = grants.role_code
     AND r.is_system = true
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_proof_roles(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_seed_proof_roles()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.seed_proof_roles(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_seed_proof_roles() FROM PUBLIC;

-- Postgres fires AFTER INSERT triggers in name order. The system roles are created by
-- tenant_seed_system_roles, so this name has to sort after it or the grants find no role.
CREATE TRIGGER tenant_seed_tax_proof_roles
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_seed_proof_roles();

SELECT core.seed_proof_roles(t.tenant_id) FROM core.tenant t;
