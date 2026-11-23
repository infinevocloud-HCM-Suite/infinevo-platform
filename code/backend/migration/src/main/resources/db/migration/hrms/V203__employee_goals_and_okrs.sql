-- Migration: V194__employee_goals_and_okrs.sql
-- Description: W-106.2 Employee Goals and OKRs tracking with RLS tenant isolation

CREATE TABLE hrms.employee_goal (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id                 UUID NOT NULL REFERENCES core.employee(id),
    cycle_id                    UUID REFERENCES hrms.performance_cycle(id) ON DELETE SET NULL,
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT,
    category                    VARCHAR(30) NOT NULL DEFAULT 'INDIVIDUAL',
    metric_type                 VARCHAR(30) NOT NULL DEFAULT 'PERCENTAGE',
    target_value                NUMERIC(12, 2) NOT NULL DEFAULT 100.00,
    current_value               NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    metric_unit                 VARCHAR(30) NOT NULL DEFAULT '%',
    progress_percentage         INTEGER NOT NULL DEFAULT 0,
    start_date                  DATE NOT NULL,
    due_date                    DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_goal_dates CHECK (due_date >= start_date),
    CONSTRAINT chk_goal_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'ACHIEVED', 'CANCELLED')),
    CONSTRAINT chk_goal_category CHECK (category IN ('INDIVIDUAL', 'TEAM', 'COMPANY')),
    CONSTRAINT chk_goal_metric_type CHECK (metric_type IN ('PERCENTAGE', 'NUMERIC', 'MILESTONE')),
    CONSTRAINT chk_goal_progress CHECK (progress_percentage BETWEEN 0 AND 100)
);

CREATE INDEX idx_goal_tenant_emp_status ON hrms.employee_goal (tenant_id, employee_id, status);
CREATE INDEX idx_goal_tenant_cycle ON hrms.employee_goal (tenant_id, cycle_id);
CREATE INDEX idx_goal_tenant_due_date ON hrms.employee_goal (tenant_id, due_date);

ALTER TABLE hrms.employee_goal ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.employee_goal
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON hrms.employee_goal TO app_user;

-- 1. Seed reference.action catalogue for goals and OKRs
INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.goal.manage', 'Manage tenant goals and OKR configurations', 'hrms', 'Create, oversee, and configure tenant-wide goals and OKRs'),
    ('hrms.goal.review_team', 'Review team goals and OKRs', 'hrms', 'View, review, and monitor goals and progress for team members'),
    ('hrms.goal.read_own', 'View own goals and OKRs', 'hrms', 'Employee self-service view of own goals, key results, and progress'),
    ('hrms.goal.update_own', 'Create and update own goals and progress', 'hrms', 'Employee self-service create, modify, and check in progress on own goals')
ON CONFLICT (code) DO NOTHING;

-- 2. Function to seed goal role grants per tenant
CREATE OR REPLACE FUNCTION core.grant_goal_actions(p_tenant_id UUID)
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
        ('platform-admin', 'hrms.goal.manage'),
        ('platform-admin', 'hrms.goal.review_team'),
        ('platform-admin', 'hrms.goal.read_own'),
        ('platform-admin', 'hrms.goal.update_own'),

        ('tenant-admin', 'hrms.goal.manage'),
        ('tenant-admin', 'hrms.goal.review_team'),
        ('tenant-admin', 'hrms.goal.read_own'),
        ('tenant-admin', 'hrms.goal.update_own'),

        ('hr', 'hrms.goal.manage'),
        ('hr', 'hrms.goal.review_team'),
        ('hr', 'hrms.goal.read_own'),
        ('hr', 'hrms.goal.update_own'),

        ('manager', 'hrms.goal.review_team'),
        ('manager', 'hrms.goal.read_own'),
        ('manager', 'hrms.goal.update_own'),

        ('employee', 'hrms.goal.read_own'),
        ('employee', 'hrms.goal.update_own'),

        ('payroll-officer', 'hrms.goal.read_own'),
        ('payroll-officer', 'hrms.goal.update_own'),

        ('finance', 'hrms.goal.read_own'),
        ('finance', 'hrms.goal.update_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_goal_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_goal_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_goal_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_goal_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_goals ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_goals
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_goal_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_goal_actions(t.tenant_id) FROM core.tenant t;

