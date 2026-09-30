-- Migration: V117__leave_monthly_lop.sql
-- Description: W-16.4a core.leave_monthly_lop — per-employee per-month loss-of-pay delta ledger with RLS isolation

CREATE TABLE core.leave_monthly_lop (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    period CHAR(7) NOT NULL,
    leave_type_id UUID NULL REFERENCES core.leave_type(id) ON DELETE RESTRICT,
    leave_request_id UUID NULL REFERENCES core.leave_request(id) ON DELETE RESTRICT,
    lop_days NUMERIC(10,2) NOT NULL,
    reverses_id UUID NULL REFERENCES core.leave_monthly_lop(id) ON DELETE RESTRICT,
    pay_input_id UUID NULL REFERENCES core.pay_input(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_leave_monthly_lop_period CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')
);

CREATE INDEX idx_leave_monthly_lop_tenant_emp_period ON core.leave_monthly_lop (tenant_id, employee_id, period);
CREATE INDEX idx_leave_monthly_lop_tenant_request ON core.leave_monthly_lop (tenant_id, leave_request_id);
CREATE INDEX idx_leave_monthly_lop_tenant_type ON core.leave_monthly_lop (tenant_id, leave_type_id);
CREATE INDEX idx_leave_monthly_lop_tenant_reverses ON core.leave_monthly_lop (tenant_id, reverses_id);
CREATE INDEX idx_leave_monthly_lop_tenant_pay_input ON core.leave_monthly_lop (tenant_id, pay_input_id);


ALTER TABLE core.leave_monthly_lop ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.leave_monthly_lop
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Append-only: app_user is refused UPDATE and DELETE (spec §6, matching V008 and V031)
REVOKE UPDATE, DELETE ON core.leave_monthly_lop FROM app_user;
