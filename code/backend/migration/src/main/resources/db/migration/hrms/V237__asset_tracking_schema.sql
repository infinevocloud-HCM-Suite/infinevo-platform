-- Migration: V237__asset_tracking_schema.sql
-- Description: W-111 Asset Tracking schema (company_asset, asset_assignment)

-- 1. Company Asset Inventory
CREATE TABLE hrms.company_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    asset_tag VARCHAR(64) NOT NULL,
    asset_name VARCHAR(120) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'LAPTOP',
    brand VARCHAR(60),
    model_number VARCHAR(80),
    serial_number VARCHAR(100),
    purchase_date DATE,
    purchase_cost NUMERIC(19,4),
    warranty_expiry DATE,
    vendor_name VARCHAR(120),
    current_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    item_condition VARCHAR(32) NOT NULL DEFAULT 'GOOD',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_company_asset_tag UNIQUE (tenant_id, asset_tag)
);

ALTER TABLE hrms.company_asset ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.company_asset
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_company_asset_tenant_status ON hrms.company_asset(tenant_id, current_status, category);
CREATE INDEX idx_company_asset_serial ON hrms.company_asset(tenant_id, serial_number);

-- 2. Asset Assignment & History
CREATE TABLE hrms.asset_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    asset_id UUID NOT NULL REFERENCES hrms.company_asset(id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    assigned_date DATE NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    assignment_condition VARCHAR(32) NOT NULL DEFAULT 'GOOD',
    assignment_notes TEXT,
    returned_date DATE,
    returned_by VARCHAR(100),
    return_condition VARCHAR(32),
    return_notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.asset_assignment ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.asset_assignment
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_asset_assignment_employee ON hrms.asset_assignment(tenant_id, employee_id, is_active);
CREATE INDEX idx_asset_assignment_asset ON hrms.asset_assignment(tenant_id, asset_id, is_active);

-- Enforce at most ONE active assignment per asset at any time
CREATE UNIQUE INDEX uk_asset_single_active_assignment
    ON hrms.asset_assignment (tenant_id, asset_id)
    WHERE is_active = true;

