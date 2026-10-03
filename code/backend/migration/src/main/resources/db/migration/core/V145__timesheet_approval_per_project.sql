-- Migration: V145__timesheet_approval_per_project.sql
-- Description: W-42.2 the TIMESHEET approval step is per item (one instance per project entry), so a project-manager
--              step carries the project it routes by. New tenants get it from the seed function below; existing
--              tenants get it where their definition is still the untouched seed. No table.

-- Seed function for a tenant's default approval definitions (all seven flows). The one current copy: it replaces V089's,
-- and the only change is "per_item": true in the TIMESHEET block. A later ticket edits this list, not V089's.
CREATE OR REPLACE FUNCTION core.seed_approval_definitions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    -- 1. LEAVE: strict two-stage ladder (sequential, manager then HR)
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'LEAVE',
        'SEQUENTIAL',
        '[
            {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false},
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 2. OVERTIME: permissive two-stage set (any order)
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'OVERTIME',
        'ANY_ORDER',
        '[
            {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false},
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 3. REIMBURSEMENT: single tenant-admin step with approved amount
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'REIMBURSEMENT',
        'SEQUENTIAL',
        '[
            {"kind": "ROLE", "assignee": "tenant-admin", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'SHARED',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 4. PROOF_OF_INVESTMENT: per-item stage then final decision
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'PROOF_OF_INVESTMENT',
        'SEQUENTIAL',
        '[
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": true},
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'SHARED',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 5. REGULARIZATION: single step to reporting manager
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'REGULARIZATION',
        'SEQUENTIAL',
        '[
            {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 6. PAY_RUN: single payroll-officer step
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'PAY_RUN',
        'SEQUENTIAL',
        '[
            {"kind": "ROLE", "assignee": "payroll-officer", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'SHARED',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 7. TIMESHEET: single project-manager step
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'TIMESHEET',
        'SEQUENTIAL',
        '[
            {"kind": "PROJECT_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": true}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_approval_definitions(UUID) FROM PUBLIC;

-- Existing tenants: change only a row still on the untouched seed. A tenant who edited their timesheet definition
-- keeps it; if it is not per_item, the new validation refuses its next save with a clear message.
UPDATE core.approval_definition
   SET steps = jsonb_set(steps, '{0,per_item}', 'true'::jsonb),
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'V145'
 WHERE flow_type = 'TIMESHEET'
   AND created_by = 'system'
   AND steps = '[{"kind": "PROJECT_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false}]'::jsonb;
