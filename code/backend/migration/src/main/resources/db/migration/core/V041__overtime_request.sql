-- Migration: V041__overtime_request.sql
-- Description: W-39.2 core.overtime_request — a dated record of approved overtime hours per
-- employee, entered by an administrator, written through core.pay_input (W-19) so a
-- Payroll-only tenant can pay it (D-35). No pricing here: hours and an optional amount are stored
-- exactly as typed (spec §4 decision, "keep it dumb" — 09-build-order.md:199).

CREATE TABLE core.overtime_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    overtime_date   DATE NOT NULL,
    hours           NUMERIC(10, 2) NOT NULL,
    amount          NUMERIC(19, 4),
    status          VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    source          VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    remarks         VARCHAR(255),
    -- Nullable only because this row is inserted before core.pay_input answers: the service sets
    -- both columns in the same transaction, right after the ledger call (spec §6).
    pay_input_id    UUID REFERENCES core.pay_input(id),
    posted_period   CHAR(7),
    cancelled_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT overtime_request_hours_check  CHECK (hours > 0 AND hours <= 24),
    CONSTRAINT overtime_request_amount_check CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT overtime_request_status_check CHECK (status IN ('APPROVED', 'CANCELLED')),
    CONSTRAINT overtime_request_source_check CHECK (source IN ('ADMIN', 'REQUEST'))
);

-- Tenant-leading (DEBT-018): the list read, the per-employee read, and the reverse lookup from a
-- pay input back to the overtime row that posted it.
CREATE INDEX idx_overtime_request_tenant_date          ON core.overtime_request (tenant_id, overtime_date DESC);
CREATE INDEX idx_overtime_request_tenant_employee_date ON core.overtime_request (tenant_id, employee_id, overtime_date DESC);
CREATE INDEX idx_overtime_request_tenant_pay_input     ON core.overtime_request (tenant_id, pay_input_id);

ALTER TABLE core.overtime_request ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.overtime_request
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- No REVOKE here, unlike core.pay_input (V031): a cancel is a status change on this row (APPROVED
-- to CANCELLED, cancelled_at set), and pay_input_id/posted_period are set by an UPDATE right after
-- the row is inserted, before the ledger answers (spec §6). "No edit" (spec §2) is a rule the
-- service enforces — there is no PUT endpoint — not one the database needs to.
