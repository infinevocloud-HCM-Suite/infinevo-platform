-- Migration: V193__performance_review_cycles.sql
-- Description: W-106.1 Performance review cycles, self-appraisals, and manager ratings with RLS isolation

CREATE TABLE hrms.performance_cycle (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name                        VARCHAR(150) NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    self_review_deadline        DATE NOT NULL,
    manager_review_deadline     DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    rating_scale_max            INTEGER NOT NULL DEFAULT 5,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_perf_cycle_dates CHECK (period_end >= period_start),
    CONSTRAINT chk_perf_cycle_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    CONSTRAINT chk_perf_cycle_scale CHECK (rating_scale_max BETWEEN 3 AND 10)
);

CREATE INDEX idx_perf_cycle_tenant_dates 
    ON hrms.performance_cycle (tenant_id, period_start DESC, period_end DESC);

ALTER TABLE hrms.performance_cycle ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.performance_cycle
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON hrms.performance_cycle TO app_user;

CREATE TABLE hrms.performance_review (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id),
    cycle_id                    UUID NOT NULL REFERENCES hrms.performance_cycle(id) ON DELETE CASCADE,
    employee_id                 UUID NOT NULL REFERENCES core.employee(id),
    manager_id                  UUID NOT NULL REFERENCES core.employee(id),
    self_rating                 INTEGER,
    self_comments               TEXT,
    self_submitted_at           TIMESTAMPTZ,
    manager_rating              INTEGER,
    manager_comments            TEXT,
    manager_submitted_at        TIMESTAMPTZ,
    status                      VARCHAR(30) NOT NULL DEFAULT 'PENDING_SELF_REVIEW',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_perf_review_status CHECK (status IN ('PENDING_SELF_REVIEW', 'PENDING_MANAGER_REVIEW', 'COMPLETED')),
    CONSTRAINT chk_perf_self_rating CHECK (self_rating IS NULL OR self_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_perf_mgr_rating CHECK (manager_rating IS NULL OR manager_rating BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uk_perf_review_tenant_cycle_emp 
    ON hrms.performance_review (tenant_id, cycle_id, employee_id);

CREATE INDEX idx_perf_review_tenant_manager 
    ON hrms.performance_review (tenant_id, manager_id, status);

CREATE INDEX idx_perf_review_tenant_emp 
    ON hrms.performance_review (tenant_id, employee_id, status);

ALTER TABLE hrms.performance_review ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.performance_review
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON hrms.performance_review TO app_user;

-- 1. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.performance.manage', 'Manage performance review cycles', 'hrms', 'Create, activate, close, and manage performance appraisal cycles'),
    ('hrms.performance.review_team', 'Review team performance appraisals', 'hrms', 'Evaluate direct reports, input ratings and appraisal feedback'),
    ('hrms.performance.read_own', 'View own performance reviews', 'hrms', 'Employee self-service view of own review cycles and appraisals'),
    ('hrms.performance.update_own', 'Submit own performance self-review', 'hrms', 'Employee self-service submit self-rating and achievement comments')
ON CONFLICT (code) DO NOTHING;

-- 2. Function to seed role grants per tenant
CREATE OR REPLACE FUNCTION core.grant_performance_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin', 'hrms.performance.manage'),
        ('platform-admin', 'hrms.performance.review_team'),
        ('platform-admin', 'hrms.performance.read_own'),
        ('platform-admin', 'hrms.performance.update_own'),

        ('tenant-admin', 'hrms.performance.manage'),
        ('tenant-admin', 'hrms.performance.review_team'),
        ('tenant-admin', 'hrms.performance.read_own'),
        ('tenant-admin', 'hrms.performance.update_own'),

        ('hr', 'hrms.performance.manage'),
        ('hr', 'hrms.performance.review_team'),
        ('hr', 'hrms.performance.read_own'),
        ('hr', 'hrms.performance.update_own'),

        ('manager', 'hrms.performance.review_team'),
        ('manager', 'hrms.performance.read_own'),
        ('manager', 'hrms.performance.update_own'),

        ('employee', 'hrms.performance.read_own'),
        ('employee', 'hrms.performance.update_own'),

        ('payroll-officer', 'hrms.performance.read_own'),
        ('payroll-officer', 'hrms.performance.update_own'),

        ('finance', 'hrms.performance.read_own'),
        ('finance', 'hrms.performance.update_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_performance_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_performance_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_performance_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_performance_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_performance ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_performance
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_performance_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_performance_actions(t.tenant_id) FROM core.tenant t;

