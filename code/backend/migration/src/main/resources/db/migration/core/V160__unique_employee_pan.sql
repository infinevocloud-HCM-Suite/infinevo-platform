-- Migration: V160__unique_employee_pan.sql
-- Description: Enforce tenant-scoped uniqueness for employee identification PAN numbers

CREATE UNIQUE INDEX idx_employee_identification_tenant_pan_unique
    ON core.employee_identification (tenant_id, pan_number)
    WHERE pan_number IS NOT NULL;
