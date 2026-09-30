-- Migration: V119__lop_policy_unique_effective_from.sql
-- Description: W-18.1 one core.lop_policy version per tenant per effective date (CORE-09, D-60)
--
-- A policy version is immutable once saved so that past payslips stay explainable. Without this
-- key two concurrent saves for the same effective_from both pass the service's existence check and
-- leave two rows, and "the policy in force" becomes whichever the planner returns first.
-- Additive: V116 has not been deployed, so no existing duplicates need resolving.

ALTER TABLE core.lop_policy
    ADD CONSTRAINT uk_lop_policy_tenant_effective_from UNIQUE (tenant_id, effective_from);
