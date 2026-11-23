-- Migration: V192__employee_onboarding_tasks.sql
-- Description: W-102 Employee onboarding task templates and employee task instances with RLS isolation

CREATE TABLE core.onboarding_task_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    task_type VARCHAR(50) NOT NULL,
    document_label VARCHAR(100) NULL,
    assigned_role VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    is_mandatory BOOLEAN NOT NULL DEFAULT true,
    due_days_after_joining INT NOT NULL DEFAULT 7,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_onboarding_template_task_type CHECK (
        task_type IN ('DOCUMENT_UPLOAD', 'PROFILE_UPDATE', 'POLICY_ACKNOWLEDGEMENT', 'MANUAL_TASK')
    ),
    CONSTRAINT chk_onboarding_template_due_days CHECK (due_days_after_joining >= 0)
);

CREATE INDEX idx_onboarding_template_tenant ON core.onboarding_task_template (tenant_id, is_active, sort_order);

ALTER TABLE core.onboarding_task_template ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.onboarding_task_template
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.onboarding_task_template TO app_user;

CREATE TABLE core.employee_onboarding_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    template_id UUID NULL REFERENCES core.onboarding_task_template(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    task_type VARCHAR(50) NOT NULL,
    document_label VARCHAR(100) NULL,
    assigned_role VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    is_mandatory BOOLEAN NOT NULL DEFAULT true,
    due_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMPTZ NULL,
    completed_by UUID NULL,
    document_id UUID NULL REFERENCES core.document(id) ON DELETE SET NULL,
    remarks TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_employee_onboarding_task_type CHECK (
        task_type IN ('DOCUMENT_UPLOAD', 'PROFILE_UPDATE', 'POLICY_ACKNOWLEDGEMENT', 'MANUAL_TASK')
    ),
    CONSTRAINT chk_employee_onboarding_task_status CHECK (
        status IN ('PENDING', 'SUBMITTED', 'COMPLETED', 'WAIVED')
    )
);

CREATE INDEX idx_employee_onboarding_task_emp ON core.employee_onboarding_task (tenant_id, employee_id, status);
CREATE INDEX idx_employee_onboarding_task_due ON core.employee_onboarding_task (tenant_id, due_date, status);

ALTER TABLE core.employee_onboarding_task ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_onboarding_task
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.employee_onboarding_task TO app_user;

-- 1. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('core.onboarding.manage', 'Manage onboarding templates and tasks', 'core', 'Configure onboarding templates and manage all employee tasks'),
    ('core.onboarding.read_own', 'View own onboarding tasks', 'core', 'Employee self-service view of own onboarding checklist'),
    ('core.onboarding.update_own', 'Update own onboarding tasks', 'core', 'Employee self-service complete or upload document for onboarding task')
ON CONFLICT (code) DO NOTHING;

-- 2. Function to seed templates and role grants per tenant
CREATE OR REPLACE FUNCTION core.grant_onboarding_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    -- Role action grants
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin', 'core.onboarding.manage'),
        ('platform-admin', 'core.onboarding.read_own'),
        ('platform-admin', 'core.onboarding.update_own'),

        ('tenant-admin', 'core.onboarding.manage'),
        ('tenant-admin', 'core.onboarding.read_own'),
        ('tenant-admin', 'core.onboarding.update_own'),

        ('hr', 'core.onboarding.manage'),
        ('hr', 'core.onboarding.read_own'),
        ('hr', 'core.onboarding.update_own'),

        ('manager', 'core.onboarding.read_own'),
        ('manager', 'core.onboarding.update_own'),

        ('employee', 'core.onboarding.read_own'),
        ('employee', 'core.onboarding.update_own'),

        ('payroll-officer', 'core.onboarding.read_own'),
        ('payroll-officer', 'core.onboarding.update_own'),

        ('finance', 'core.onboarding.read_own'),
        ('finance', 'core.onboarding.update_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;

    -- Seed standard onboarding templates if none exist
    IF NOT EXISTS (SELECT 1 FROM core.onboarding_task_template WHERE tenant_id = p_tenant_id) THEN
        INSERT INTO core.onboarding_task_template (
            tenant_id, title, description, task_type, document_label, assigned_role, is_mandatory, due_days_after_joining, sort_order, is_active
        ) VALUES
            (p_tenant_id, 'Submit Identity & Address Proof', 'Upload valid government-issued ID proof (PAN/Aadhaar/Passport)', 'DOCUMENT_UPLOAD', 'Identity & Address Proof', 'EMPLOYEE', true, 7, 1, true),
            (p_tenant_id, 'Previous Employment Relieving & Experience Letter', 'Upload relieving certificate or service letter from prior employer', 'DOCUMENT_UPLOAD', 'Relieving Letter', 'EMPLOYEE', false, 14, 2, true),
            (p_tenant_id, 'Acknowledge Employee Handbook & Code of Conduct', 'Review and acknowledge company handbook, IT security and code of conduct', 'POLICY_ACKNOWLEDGEMENT', NULL, 'EMPLOYEE', true, 3, 3, true),
            (p_tenant_id, 'Verify Emergency Contacts & Personal Details', 'Review personal, emergency contact and family details on profile', 'PROFILE_UPDATE', NULL, 'EMPLOYEE', true, 5, 4, true),
            (p_tenant_id, 'Manager Orientation & IT Setup', 'Complete 1-on-1 team orientation and verify IT equipment receipt', 'MANUAL_TASK', NULL, 'EMPLOYEE', false, 7, 5, true);
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_onboarding_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_onboarding_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_onboarding_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_onboarding_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_onboarding ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_onboarding
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_onboarding_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_onboarding_actions(t.tenant_id) FROM core.tenant t;

