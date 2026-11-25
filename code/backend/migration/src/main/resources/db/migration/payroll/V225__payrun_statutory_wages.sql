-- Migration: V225__payrun_statutory_wages.sql
-- Description: W-87 persist EPF, EPS, and EDLI statutory calculation wage bases on employee_payrun

ALTER TABLE payroll.employee_payrun
    ADD COLUMN IF NOT EXISTS epf_wage NUMERIC(14, 4) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS eps_wage NUMERIC(14, 4) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS edli_wage NUMERIC(14, 4) DEFAULT NULL;

COMMENT ON COLUMN payroll.employee_payrun.epf_wage IS 'Statutory EPF calculation wage base for the employee';
COMMENT ON COLUMN payroll.employee_payrun.eps_wage IS 'Statutory EPS calculation wage base capped at statutory ceiling';
COMMENT ON COLUMN payroll.employee_payrun.edli_wage IS 'Statutory EDLI calculation wage base capped at statutory ceiling';

