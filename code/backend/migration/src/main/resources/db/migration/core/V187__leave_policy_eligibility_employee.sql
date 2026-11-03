-- Migration: V187__leave_policy_eligibility_employee.sql
-- Schema: core
-- Purpose: core.leave_policy_eligibility.dimension accepts 'employee' (W-79.1)
--
-- A leave policy can be limited to named employees, as legacy gave a leave type to picked
-- employees (leave_type_employee_ids). value_id holds the employee's id. A policy with any
-- 'employee' rows is eligible only for those employees; it combines with the other dimensions
-- by AND (LeaveEligibilityServiceImpl).
--
-- Widening only: the four existing dimensions are kept, so every stored row still passes.
-- tenant_id, the RLS policy and idx_leave_policy_eligibility_lookup (tenant_id, policy_id) are
-- unchanged. V128 is not edited — an applied script's checksum must not change.

ALTER TABLE core.leave_policy_eligibility DROP CONSTRAINT chk_leave_policy_eligibility_dimension;

ALTER TABLE core.leave_policy_eligibility
    ADD CONSTRAINT chk_leave_policy_eligibility_dimension CHECK (dimension IN (
        'department', 'designation', 'work_location', 'employment_type', 'employee'));
