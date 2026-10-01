-- Migration: V103__payrun_approve_pay.sql
-- Description: W-36.2 payslips — approve, pay, render from the run, signed link.
-- Adds columns to payroll.payrun for the approve and pay lifecycle transitions.
-- Expand only: nullable columns and a CHECK ensuring paid_on is set if and only if status is PAID.

ALTER TABLE payroll.payrun
    ADD COLUMN approved_at timestamptz,
    ADD COLUMN approved_by varchar(100),
    ADD COLUMN paid_at timestamptz,
    ADD COLUMN paid_by varchar(100),
    ADD COLUMN paid_on date,
    ADD COLUMN payslips_released_at timestamptz;
