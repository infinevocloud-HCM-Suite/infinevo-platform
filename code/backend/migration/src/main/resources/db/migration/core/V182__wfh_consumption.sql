-- Migration: V182__wfh_consumption.sql
-- Description: W-74 core.wfh_consumption — append-only immutable WFH quota consumption ledger with RLS isolation

CREATE TABLE core.wfh_consumption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    allocation_id UUID NOT NULL REFERENCES core.wfh_allocation(id) ON DELETE RESTRICT,
    wfh_request_id UUID NULL REFERENCES core.wfh_request(id) ON DELETE RESTRICT,
    consumed_days NUMERIC(10,2) NOT NULL,
    consumed_on DATE NOT NULL,
    period CHAR(7) NOT NULL,
    reverses_id UUID NULL REFERENCES core.wfh_consumption(id) ON DELETE RESTRICT,
    reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_wfh_consumption_period CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')
);

CREATE INDEX idx_wfh_consumption_tenant_emp_period ON core.wfh_consumption (tenant_id, employee_id, period);
CREATE INDEX idx_wfh_consumption_tenant_allocation ON core.wfh_consumption (tenant_id, allocation_id);
CREATE INDEX idx_wfh_consumption_tenant_request ON core.wfh_consumption (tenant_id, wfh_request_id);
CREATE INDEX idx_wfh_consumption_tenant_reverses ON core.wfh_consumption (tenant_id, reverses_id);
CREATE UNIQUE INDEX uk_wfh_consumption_request ON core.wfh_consumption (tenant_id, wfh_request_id) WHERE reverses_id IS NULL;

ALTER TABLE core.wfh_consumption ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.wfh_consumption
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Append-only audit ledger: app_user is refused UPDATE and DELETE
GRANT SELECT, INSERT ON core.wfh_consumption TO app_user;
REVOKE UPDATE, DELETE ON core.wfh_consumption FROM app_user;
