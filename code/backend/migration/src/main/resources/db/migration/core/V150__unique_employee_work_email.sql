-- Enforce uniqueness of employee work email within a tenant for active (non-deleted) employees (BUG-D4-05A).
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_tenant_work_email
ON core.employee (tenant_id, LOWER(work_email))
WHERE deleted = false AND work_email IS NOT NULL AND work_email <> '';
