-- Migration: V193__leave_encashment.sql
-- Description: W-90.1 Leave Encashment on Exit & Tax Exemption under Section 10(10AA)
--   payroll.leave_encashment_settlement — records accrued earned/privilege leave encashment calculations and payouts at exit.

CREATE TABLE payroll.leave_encashment_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    leave_type_id UUID NOT NULL REFERENCES core.leave_type(id),
    encashable_days NUMERIC(6,2) NOT NULL CHECK (encashable_days >= 0),
    per_day_rate NUMERIC(14,2) NOT NULL CHECK (per_day_rate >= 0),
    gross_encashment_amount NUMERIC(14,2) NOT NULL CHECK (gross_encashment_amount >= 0),
    tax_exempt_amount NUMERIC(14,2) NOT NULL CHECK (tax_exempt_amount >= 0),
    taxable_amount NUMERIC(14,2) NOT NULL CHECK (taxable_amount >= 0),
    statutory_ceiling NUMERIC(14,2) NOT NULL DEFAULT 2500000.00,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_leave_encashment_emp_type
    ON payroll.leave_encashment_settlement (tenant_id, employee_id, leave_type_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_leave_encashment_tenant_emp
    ON payroll.leave_encashment_settlement (tenant_id, employee_id);

ALTER TABLE payroll.leave_encashment_settlement ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.leave_encashment_settlement
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
