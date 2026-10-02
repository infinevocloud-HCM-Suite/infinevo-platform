-- Migration: V085__hrms_project_actions.sql
-- Description: W-41 reference.action — the four hrms.project.* action codes

INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.project.read', 'View all projects and tasks', 'hrms', 'View all projects and tasks'),
    ('hrms.project.read_team', 'View projects I manage', 'hrms', 'View projects I manage'),
    ('hrms.project.read_own', 'View my projects and tasks', 'hrms', 'View my projects and tasks'),
    ('hrms.project.manage', 'Create and change projects, tasks and assignments', 'hrms', 'Create and change projects, tasks and assignments')
ON CONFLICT (code) DO NOTHING;

-- The role grants and the tenant backfill live in V135__hrms_project_seed_roles.sql. This script must not
-- redefine core.seed_system_roles: V097 (W-35.1) does too, and whichever copy ran last used to drop the
-- other's grants.
