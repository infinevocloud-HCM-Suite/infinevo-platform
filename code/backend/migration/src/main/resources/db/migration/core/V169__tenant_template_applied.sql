-- Migration: V169__tenant_template_applied.sql
-- Schema: core
-- Purpose: W-73.9 core.tenant_template_applied - what each country-template run did for a tenant:
--          one row per section per run, APPLIED when the section wrote the tenant's rows, SKIPPED
--          when the tenant already had its own (or does not hold the section's module). Records the
--          template version the tenant got, so a later rate change can tell who has the old one.
--
-- The reference table it reads is reference.country_template (V162). Kept in its own script so the
-- reference migration stays tenant-free.

CREATE TABLE core.tenant_template_applied (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    country_code CHAR(2) NOT NULL,
    section VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_tenant_template_applied_outcome CHECK (outcome IN ('APPLIED', 'SKIPPED'))
);

-- tenant_id leading (DEBT-018).
CREATE INDEX idx_tenant_template_applied_tenant_section ON core.tenant_template_applied (tenant_id, section, applied_at);

ALTER TABLE core.tenant_template_applied ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.tenant_template_applied
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

COMMENT ON TABLE core.tenant_template_applied IS
    'W-73.9: one row per country-template section per run - APPLIED or SKIPPED, with the template version';
