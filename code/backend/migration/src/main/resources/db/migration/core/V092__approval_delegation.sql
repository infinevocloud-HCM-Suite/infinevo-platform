-- Migration: V092__approval_delegation.sql
-- Description: W-15.3 core.approval_delegation and approval_step delegation/escalation/reassign columns

CREATE TABLE core.approval_delegation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    delegator_employee_id UUID NOT NULL REFERENCES core.employee(id),
    delegate_employee_id UUID NOT NULL REFERENCES core.employee(id),
    flow_types VARCHAR(256) NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_approval_delegation_no_self CHECK (delegator_employee_id <> delegate_employee_id),
    CONSTRAINT chk_approval_delegation_dates CHECK (effective_from <= effective_to)
);

CREATE INDEX idx_approval_delegation_tenant_lookup ON core.approval_delegation (tenant_id, delegator_employee_id, effective_from, effective_to);
CREATE INDEX idx_approval_delegation_tenant_delegate ON core.approval_delegation (tenant_id, delegate_employee_id);

ALTER TABLE core.approval_delegation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.approval_delegation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Add additive columns to core.approval_step (spec section 6)
ALTER TABLE core.approval_step
    ADD COLUMN delegated_from_employee_id UUID NULL REFERENCES core.employee(id),
    ADD COLUMN escalated_from_employee_id UUID NULL REFERENCES core.employee(id),
    ADD COLUMN reassigned_from_employee_id UUID NULL REFERENCES core.employee(id),
    ADD COLUMN reassign_reason VARCHAR(500) NULL;

CREATE INDEX idx_approval_step_escalation_sweep ON core.approval_step (tenant_id, decision, created_at);
