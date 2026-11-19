-- Migration: V196__employee_goal_check_in.sql
-- Description: Employee goal check-in history table with RLS isolation (W-106.2)

CREATE TABLE hrms.employee_goal_check_in (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id),
    goal_id             UUID NOT NULL REFERENCES hrms.employee_goal(id) ON DELETE CASCADE,
    current_value       NUMERIC(12,2) NOT NULL,
    progress_percentage INTEGER NOT NULL CHECK (progress_percentage BETWEEN 0 AND 100),
    notes               VARCHAR(2000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_goal_check_in_tenant_goal ON hrms.employee_goal_check_in (tenant_id, goal_id, created_at DESC);

ALTER TABLE hrms.employee_goal_check_in ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.employee_goal_check_in
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT ON hrms.employee_goal_check_in TO app_user;

