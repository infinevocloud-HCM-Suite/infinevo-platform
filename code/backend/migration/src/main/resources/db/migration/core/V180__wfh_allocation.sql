-- Migration: V180__wfh_allocation.sql
-- Description: W-74 core.wfh_allocation — employee yearly WFH quota allocation with non-overlapping GiST bounds and RLS isolation

CREATE TABLE core.wfh_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES core.wfh_policy(id) ON DELETE RESTRICT,
    wfh_year VARCHAR(9) NOT NULL,
    year_start_date DATE NOT NULL,
    year_end_date DATE NOT NULL,
    entitlement_days NUMERIC(10,2) NOT NULL DEFAULT 12.00,
    pro_rate_factor NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_wfh_allocation UNIQUE (tenant_id, employee_id, wfh_year)
);

CREATE INDEX idx_wfh_allocation_lookup ON core.wfh_allocation (tenant_id, employee_id, year_start_date, year_end_date);

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE core.wfh_allocation
    ADD CONSTRAINT no_overlapping_wfh_allocation
    EXCLUDE USING gist (
        tenant_id WITH =,
        employee_id WITH =,
        daterange(year_start_date, year_end_date, '[]') WITH &&
    );

ALTER TABLE core.wfh_allocation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.wfh_allocation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.wfh_allocation TO app_user;
