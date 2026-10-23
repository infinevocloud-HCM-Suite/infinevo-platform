-- Migration: V181__wfh_request.sql
-- Description: W-74 core.wfh_request — employee WFH applications with non-overlapping approved dates and RLS isolation

CREATE TABLE core.wfh_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    is_half_day BOOLEAN NOT NULL DEFAULT false,
    half_day_period VARCHAR(8) NULL,
    working_days NUMERIC(10,2) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    approval_instance_id UUID NULL REFERENCES core.approval_instance(id) ON DELETE SET NULL,
    on_behalf BOOLEAN NOT NULL DEFAULT false,
    decided_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_wfh_request_dates CHECK (from_date <= to_date),
    CONSTRAINT chk_wfh_request_half_day_period CHECK (half_day_period IS NULL OR half_day_period IN ('first', 'second')),
    CONSTRAINT chk_wfh_request_half_day_pair CHECK (
        (is_half_day = false AND half_day_period IS NULL) OR
        (is_half_day = true AND half_day_period IS NOT NULL)
    ),
    CONSTRAINT chk_wfh_request_status CHECK (
        status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'WITHDRAWN')
    ),
    CONSTRAINT chk_wfh_request_working_days CHECK (working_days >= 0)
);

CREATE INDEX idx_wfh_request_employee_date ON core.wfh_request (tenant_id, employee_id, from_date DESC);
CREATE INDEX idx_wfh_request_status ON core.wfh_request (tenant_id, status);
CREATE INDEX idx_wfh_request_approval_inst ON core.wfh_request (tenant_id, approval_instance_id);

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE core.wfh_request
    ADD CONSTRAINT no_overlapping_approved_wfh
    EXCLUDE USING gist (
        tenant_id WITH =,
        employee_id WITH =,
        daterange(from_date, to_date, '[]') WITH &&
    ) WHERE (status = 'APPROVED');

ALTER TABLE core.wfh_request ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.wfh_request
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.wfh_request TO app_user;
