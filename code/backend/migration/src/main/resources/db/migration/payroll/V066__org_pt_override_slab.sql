-- Migration: V066__org_pt_override_slab.sql
-- Description: W-31.2 payroll.org_pt_override_slab — per-tenant override slabs with RLS isolation

CREATE TABLE payroll.org_pt_override_slab (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    override_id UUID NOT NULL REFERENCES payroll.org_pt_override(id) ON DELETE CASCADE,
    from_amount NUMERIC(19,4) NOT NULL,
    to_amount NUMERIC(19,4),
    amount NUMERIC(19,4) NOT NULL,
    is_female_exempt BOOLEAN NOT NULL DEFAULT false,
    deduction_months VARCHAR(32),
    sort_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_org_pt_override_slab_tenant_override
    ON payroll.org_pt_override_slab (tenant_id, override_id, sort_order);

ALTER TABLE payroll.org_pt_override_slab ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.org_pt_override_slab
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
