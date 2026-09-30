-- Migration: V059__payrun_job_progress.sql
-- Description: W-29.4 the pay run computes on the worker. The run carries the job that computes it,
-- the attempt number and the progress the payroll screen shows; each employee row carries the
-- attempt that last computed it, so a run abandoned mid-way resumes instead of starting again.
-- Expand only: columns with defaults, nothing dropped.

ALTER TABLE payroll.payrun
    -- Soft reference to core.job_status.job_id (VARCHAR(64), V006): no FK across schemas (W-52).
    ADD COLUMN job_id VARCHAR(64),
    -- Incremented by every POST /compute; the job id ends with it.
    ADD COLUMN compute_attempt INT NOT NULL DEFAULT 0,
    ADD COLUMN compute_started_at TIMESTAMPTZ,
    -- Employees computed so far, out of the included count, for the progress bar.
    ADD COLUMN progress_done INT NOT NULL DEFAULT 0,
    ADD COLUMN progress_total INT NOT NULL DEFAULT 0;

ALTER TABLE payroll.employee_payrun
    -- The attempt that last computed this row. A resumed attempt skips rows already at its number.
    ADD COLUMN computed_attempt INT NOT NULL DEFAULT 0;

-- The worker's job-to-run lookup (DEBT-018).
CREATE INDEX idx_payrun_tenant_job ON payroll.payrun (tenant_id, job_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- The payroll officer follows the job POST /compute returns on GET /api/v1/jobs/{jobId}, which
-- needs core.job.read (V025); only the admin roles held it (W-29.4 §4).
--
-- A function and a trigger of their own rather than a replacement of core.seed_system_roles, the
-- pattern of V037: every lane that rewrites that function has to carry every other lane's grants.
-- tenant_seed_system_roles_payrun_job sorts after tenant_seed_system_roles (V022), so the roles
-- exist when this runs. Only system roles are granted, as V022 does.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_payrun_job_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('payroll-officer', 'core.job.read')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_payrun_job_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_payrun_job_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_payrun_job_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_payrun_job_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_payrun_job
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_payrun_job_actions();

-- Tenants that exist before this script ran get the grant too.
SELECT core.grant_payrun_job_actions(t.tenant_id) FROM core.tenant t;
