-- Migration: V058__employee_payrun_days.sql
-- Description: W-29.3 the day figures behind loss of pay on payroll.employee_payrun, and the
-- negative-net count on payroll.payrun. Expand only: columns with defaults, nothing dropped.
-- Not added here, on purpose: payable_days, pay_divisor, lop_policy_id, working_day_basis and
-- lop_rounding are W-18.2's stamp columns.

ALTER TABLE payroll.employee_payrun
    -- Day counts at two decimals: a half day of loss of pay survives (BUG-003).
    ADD COLUMN lop_days NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN unpaid_days NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN paid_days NUMERIC(10,2) NOT NULL DEFAULT 0,
    -- Overtime rows with hours and no amount: not paid, counted so nobody misses them.
    ADD COLUMN unpriced_input_count INT NOT NULL DEFAULT 0;

ALTER TABLE payroll.payrun
    -- Rows whose net pay came out below zero; stored negative, never floored.
    ADD COLUMN negative_net_count INT NOT NULL DEFAULT 0;
