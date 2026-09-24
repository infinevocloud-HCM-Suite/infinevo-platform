-- Migration: V011__index_standard_optimizations.sql
-- Description: Composite index optimizations adhering to W-55 standards (DEBT-018, PLAT-06)

-- 1. core.employee: Active status listing index covering tenant_id and soft-delete flag (02-data-model.md:391)
CREATE INDEX idx_employee_tenant_active_status
    ON core.employee (tenant_id, is_deleted, status);

-- 2. core.job_status: Polling and listing index for queue jobs ordered by recency
CREATE INDEX idx_job_status_tenant_status
    ON core.job_status (tenant_id, status, created_at DESC);
