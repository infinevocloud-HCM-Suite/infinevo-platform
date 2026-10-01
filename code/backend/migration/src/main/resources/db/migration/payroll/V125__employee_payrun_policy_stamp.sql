-- Migration: V125__employee_payrun_policy_stamp.sql
-- Description: W-18.2 the policy stamp on every pay figure. Each computed payroll.employee_payrun
-- row records the loss-of-pay policy version, the working-day basis, the divisor, the payable days
-- and the rounding rule that produced its figure, so a disputed payslip can be explained later
-- without re-running today's code. The five columns V058 left out on purpose.
-- Expand only: nullable columns, nothing dropped. Nullable to be additive; the service refuses to
-- write a figure without them, and StampCompletenessIT asserts no computed row has a null.
-- No foreign key to core.lop_policy: a payroll table does not constrain a core one, and W-18.1
-- never deletes a policy version (rows are immutable per effective_from).

ALTER TABLE payroll.employee_payrun
    ADD COLUMN lop_policy_id UUID,
    -- W-18.1's WorkingDayBasis: ACTUAL_DAYS, ORG_DAYS, FIXED_30.
    ADD COLUMN working_day_basis VARCHAR(24),
    -- Day counts, not money: the day-count type (CONVENTIONS.md, 12-core-contracts.md).
    ADD COLUMN pay_divisor NUMERIC(10,2),
    ADD COLUMN payable_days NUMERIC(10,2),
    -- W-18.1's LopRounding: HALF_UP_2, HALF_UP_0, NONE.
    ADD COLUMN lop_rounding VARCHAR(16);

-- Every figure a policy produced is findable when that policy is questioned (DEBT-018).
CREATE INDEX idx_employee_payrun_tenant_lop_policy ON payroll.employee_payrun (tenant_id, lop_policy_id);
