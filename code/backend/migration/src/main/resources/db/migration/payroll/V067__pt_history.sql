-- Migration: V067__pt_history.sql
-- Description: W-31.2 payroll.pt_history — audit log of professional tax override changes with RLS isolation

CREATE TABLE payroll.pt_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    state_code VARCHAR(10) NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('OVERRIDE_SET', 'OVERRIDE_RESET')),
    before_slabs JSONB,
    after_slabs JSONB,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by UUID NOT NULL
);

CREATE INDEX idx_pt_history_tenant_state_changed
    ON payroll.pt_history (tenant_id, state_code, changed_at DESC);

ALTER TABLE payroll.pt_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.pt_history
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
