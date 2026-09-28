-- Migration: V051__fbp.sql
-- Description: W-27.1 payroll.fbp — flexible benefit plan configuration with RLS isolation

CREATE TABLE payroll.fbp (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    is_enabled BOOLEAN NOT NULL DEFAULT false,
    window_opens_on DATE,
    window_closes_on DATE,
    is_locked BOOLEAN NOT NULL DEFAULT false,
    locked_at TIMESTAMPTZ,
    notify_on_release BOOLEAN NOT NULL DEFAULT true,
    notify_on_lock BOOLEAN NOT NULL DEFAULT true,
    reminder_days_before_close SMALLINT[] NOT NULL DEFAULT '{5,1}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique plan per tenant
CREATE UNIQUE INDEX uk_fbp_tenant ON payroll.fbp (tenant_id);

-- Row-Level Security
ALTER TABLE payroll.fbp ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.fbp
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
