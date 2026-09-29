-- Migration: V071__income_tax_declaration.sql
-- Description: W-32.1 payroll.income_tax_declaration — tenant-level window settings per financial year

CREATE TABLE payroll.income_tax_declaration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    financial_year VARCHAR(9) NOT NULL,
    window_opens_on DATE NOT NULL,
    window_closes_on DATE NOT NULL,
    is_locked BOOLEAN NOT NULL DEFAULT false,
    default_tax_regime VARCHAR(3) NOT NULL DEFAULT 'NEW' CHECK (default_tax_regime IN ('OLD', 'NEW')),
    can_change_tax_regime BOOLEAN NOT NULL DEFAULT true,
    pan_required_for_rent_over_threshold BOOLEAN NOT NULL DEFAULT true,
    notify_on_lock BOOLEAN NOT NULL DEFAULT false,
    notify_on_release BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_it_decl_window_dates CHECK (window_closes_on >= window_opens_on)
);

CREATE UNIQUE INDEX uk_income_tax_declaration_tenant_fy ON payroll.income_tax_declaration (tenant_id, financial_year);

ALTER TABLE payroll.income_tax_declaration ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.income_tax_declaration
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
