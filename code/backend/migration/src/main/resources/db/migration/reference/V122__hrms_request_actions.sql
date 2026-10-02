-- Migration: V122__hrms_request_actions.sql
-- Description: W-40.2 reference.action — hrms.overtime.request code

INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.overtime.request', 'Request overtime', 'hrms', 'Request overtime approval for extra hours worked')
ON CONFLICT (code) DO NOTHING;

-- The role grants and the tenant backfill live in core/V139__hrms_request_seed_roles.sql. This script must not
-- redefine core.seed_system_roles: V135 (W-41) does too, and whichever copy runs last defines the function.
