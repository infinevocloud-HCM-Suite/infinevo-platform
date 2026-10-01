-- Migration: V128__leave_allocation.sql
-- Description: W-16.2 core.leave_allocation — employee leave allocations and balance tracking with RLS isolation

CREATE TABLE core.leave_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES core.leave_type(id) ON DELETE CASCADE,
    leave_year VARCHAR(9) NOT NULL,
    year_start_date DATE NOT NULL,
    year_end_date DATE NOT NULL,
    entitlement_days NUMERIC(10,2) NOT NULL,
    accrued_days NUMERIC(10,2) NOT NULL DEFAULT 0,
    carried_forward_days NUMERIC(10,2) NOT NULL DEFAULT 0,
    carry_forward_expires_on DATE NULL,
    pro_rate_factor NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    last_accrued_on DATE NULL,
    last_reset_on DATE NULL,
    policy_id UUID NOT NULL REFERENCES core.leave_policy(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_leave_allocation UNIQUE (tenant_id, employee_id, leave_type_id, leave_year)
);

CREATE INDEX idx_leave_allocation_accrual_sweep ON core.leave_allocation (tenant_id, last_accrued_on);
CREATE INDEX idx_leave_allocation_lookup ON core.leave_allocation (tenant_id, employee_id, year_start_date, year_end_date);

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE core.leave_allocation
    ADD CONSTRAINT no_overlapping_leave_allocation
    EXCLUDE USING gist (
        tenant_id WITH =,
        employee_id WITH =,
        leave_type_id WITH =,
        daterange(year_start_date, year_end_date, '[]') WITH &&
    );

ALTER TABLE core.leave_allocation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.leave_allocation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
