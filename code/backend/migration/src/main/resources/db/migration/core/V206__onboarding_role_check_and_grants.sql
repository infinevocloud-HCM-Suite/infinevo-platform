-- Migration: V197__onboarding_role_check_and_grants.sql
-- Description: Add assigned_role CHECK constraints and ensure onboarding role grants (W-102)

-- 1. Add CHECK constraints on assigned_role
ALTER TABLE core.onboarding_task_template
    ADD CONSTRAINT chk_onboarding_template_assigned_role
    CHECK (assigned_role IN ('EMPLOYEE', 'HR', 'MANAGER', 'IT'));

ALTER TABLE core.employee_onboarding_task
    ADD CONSTRAINT chk_employee_task_assigned_role
    CHECK (assigned_role IN ('EMPLOYEE', 'HR', 'MANAGER', 'IT'));

-- 2. Update core.grant_onboarding_actions(uuid) function
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

    -- Default task templates for tenant
    IF NOT EXISTS (SELECT 1 FROM core.onboarding_task_template WHERE tenant_id = p_tenant_id) THEN
        INSERT INTO core.onboarding_task_template (
            tenant_id, title, description, task_type, document_label, assigned_role, is_mandatory, due_days_after_joining, sort_order, is_active, created_by, updated_by
        ) VALUES
        (
            p_tenant_id,
            'Submit Identity & Address Proof',
            'Upload government-issued photo ID (Aadhaar / Passport) and current address proof.',
            'DOCUMENT_UPLOAD',
            'ID_PROOF',
            'EMPLOYEE',
            true,
            7,
            1,
            true,
            'system',
            'system'
        ),
        (
            p_tenant_id,
            'Previous Employment Relieving & Experience Letter',
            'Upload relieving letter and latest salary slip from previous employer.',
            'DOCUMENT_UPLOAD',
            'CERTIFICATE',
            'EMPLOYEE',
            false,
            14,
            2,
            true,
            'system',
            'system'
        ),
        (
            p_tenant_id,
            'Acknowledge Employee Handbook & Code of Conduct',
            'Review company policies, information security guidelines, and workplace code of conduct.',
            'POLICY_ACKNOWLEDGEMENT',
            NULL,
            'EMPLOYEE',
            true,
            3,
            3,
            true,
            'system',
            'system'
        ),
        (
            p_tenant_id,
            'Verify Emergency Contacts & Personal Details',
            'Confirm blood group, emergency contact phone numbers, and permanent address on your profile.',
            'PROFILE_UPDATE',
            NULL,
            'EMPLOYEE',
            true,
            5,
            4,
            true,
            'system',
            'system'
        ),
        (
            p_tenant_id,
            'Manager Orientation & IT Setup',
            'Complete 1-on-1 welcome session with your reporting manager and verify laptop/access credentials.',
            'MANUAL_TASK',
            NULL,
            'EMPLOYEE',
            false,
            7,
            5,
            true,
            'system',
            'system'
        );
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_onboarding_actions(UUID) FROM PUBLIC;

-- 3. Backfill existing non-platform tenants
SELECT core.grant_onboarding_actions(t.tenant_id) FROM core.tenant t;

