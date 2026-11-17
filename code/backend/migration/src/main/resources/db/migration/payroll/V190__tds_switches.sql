-- Migration: V190__tds_switches.sql
-- Description: D-141 two switches that hold TDS out of the pay run without touching the tax records.
--   payroll.tds_setting — company switch "Deduct TDS in pay run", one row per tenant, on by default.
--     A tenant with no row deducts TDS, as before this migration.
--   payroll.employee_statutory_profile.is_tds_applicable — employee switch, on by default, seeded off
--     for anyone whose core.employee_personal.is_eligible_for_full_tax_exemption is true (V015).
-- The existing tenant_isolation policy on employee_statutory_profile (V050) covers the new column.

CREATE TABLE payroll.tds_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_tds_setting_tenant UNIQUE (tenant_id)
);

ALTER TABLE payroll.tds_setting ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tds_setting
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

ALTER TABLE payroll.employee_statutory_profile
    ADD COLUMN is_tds_applicable BOOLEAN NOT NULL DEFAULT true;

UPDATE payroll.employee_statutory_profile p
SET is_tds_applicable = false
FROM core.employee_personal ep
WHERE ep.employee_id = p.employee_id
  AND ep.tenant_id = p.tenant_id
  AND ep.is_eligible_for_full_tax_exemption;

-- An exempt employee with no profile row would still read as applicable: the service answers true
-- when no profile exists (EmployeeStatutoryProfileServiceImpl.isTdsApplicable). So give each such
-- employee a profile row with the flag off. Every other column takes its V050 default (eligibility
-- flags false), which is exactly how a missing profile already reads, so nothing else changes.
INSERT INTO payroll.employee_statutory_profile (tenant_id, employee_id, is_tds_applicable, created_by, updated_by)
SELECT DISTINCT ep.tenant_id, ep.employee_id, false, 'V190', 'V190'
FROM core.employee_personal ep
WHERE ep.is_eligible_for_full_tax_exemption
  AND NOT EXISTS (
      SELECT 1
      FROM payroll.employee_statutory_profile p
      WHERE p.tenant_id = ep.tenant_id
        AND p.employee_id = ep.employee_id
  );
