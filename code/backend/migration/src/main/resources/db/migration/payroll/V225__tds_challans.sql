-- Migration: V225__tds_challans.sql
-- Description: W-89.1 TDS challans and their allocation to pay run TDS (Claude rebuild of mohit's V192, 2026-11-25)
--   payroll.tds_challan — records government ITNS 281 tax deposit challans.
--   payroll.tds_challan_allocation — links challan deposits to pay runs and employee TDS deductions.

CREATE TABLE payroll.tds_challan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    quarter VARCHAR(2) NOT NULL CHECK (quarter IN ('Q1', 'Q2', 'Q3', 'Q4')),
    bsr_code VARCHAR(7) NOT NULL CHECK (bsr_code ~ '^[0-9]{7}$'),
    challan_serial_no VARCHAR(5) NOT NULL CHECK (challan_serial_no ~ '^[0-9]{1,5}$'),
    tender_date DATE NOT NULL,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (tax_amount >= 0),
    surcharge_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (surcharge_amount >= 0),
    cess_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (cess_amount >= 0),
    interest_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (interest_amount >= 0),
    fee_penalty_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (fee_penalty_amount >= 0),
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount = tax_amount + surcharge_amount + cess_amount + interest_amount + fee_penalty_amount),
    minor_head VARCHAR(3) NOT NULL DEFAULT '200' CHECK (minor_head IN ('200', '400')),
    remarks VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_tds_challan_bsr_serial_date
    ON payroll.tds_challan (tenant_id, bsr_code, challan_serial_no, tender_date);

CREATE INDEX idx_tds_challan_tenant_fy_quarter
    ON payroll.tds_challan (tenant_id, financial_year, quarter);

ALTER TABLE payroll.tds_challan ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tds_challan
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

CREATE TABLE payroll.tds_challan_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    challan_id UUID NOT NULL REFERENCES payroll.tds_challan(id) ON DELETE CASCADE,
    pay_run_id UUID NOT NULL REFERENCES payroll.payrun(id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    allocated_tax NUMERIC(14,2) NOT NULL CHECK (allocated_tax >= 0),
    allocated_surcharge NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (allocated_surcharge >= 0),
    allocated_cess NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (allocated_cess >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- pay_run_id and employee_id are checked against the caller's tenant in TdsChallanServiceImpl.allocate
-- before the insert: a plain FK is not tenant-scoped.

CREATE UNIQUE INDEX uk_tds_challan_alloc_emp_run
    ON payroll.tds_challan_allocation (tenant_id, challan_id, pay_run_id, employee_id);

CREATE INDEX idx_tds_challan_alloc_challan
    ON payroll.tds_challan_allocation (tenant_id, challan_id);

CREATE INDEX idx_tds_challan_alloc_pay_run
    ON payroll.tds_challan_allocation (tenant_id, pay_run_id);

ALTER TABLE payroll.tds_challan_allocation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tds_challan_allocation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- W-89: one row per quarter whose return has been filed. Its challans and allocations are locked while it exists,
-- and its 15-digit token number goes into the next quarter's file (Form 138 batch header field 9).
CREATE TABLE payroll.tds_return (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    quarter VARCHAR(2) NOT NULL CHECK (quarter IN ('Q1', 'Q2', 'Q3', 'Q4')),
    token_number VARCHAR(15) NOT NULL CHECK (token_number ~ '^[0-9]{15}$'),
    filed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_tds_return_quarter ON payroll.tds_return (tenant_id, financial_year, quarter);

ALTER TABLE payroll.tds_return ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tds_return
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
