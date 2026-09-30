-- Migration: V110__leave_type.sql
-- Description: W-16.1 core.leave_type — tenant leave types with RLS isolation

CREATE TABLE core.leave_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_paid BOOLEAN NOT NULL,
    unit VARCHAR(16) NOT NULL,
    allow_half_day BOOLEAN NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_leave_type_unit CHECK (unit IN ('DAYS', 'days', 'DAY', 'day'))
);

CREATE UNIQUE INDEX uk_leave_type_tenant_code ON core.leave_type (tenant_id, code);
CREATE INDEX idx_leave_type_tenant_active ON core.leave_type (tenant_id, is_active, valid_from);

ALTER TABLE core.leave_type ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.leave_type
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.leave_type TO app_user;

