-- Migration: V126__leave_policy.sql
-- Description: W-16.1 core.leave_policy — leave policy matrix and effective-date versioning with RLS isolation

CREATE TABLE core.leave_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES core.leave_type(id) ON DELETE CASCADE,
    annual_days NUMERIC(10,2) NOT NULL,
    accrual_enabled BOOLEAN NULL,
    accrual_frequency VARCHAR(16) NULL,
    accrual_units NUMERIC(10,2) NULL,
    reset_enabled BOOLEAN NULL,
    reset_frequency VARCHAR(16) NULL,
    carry_forward_enabled BOOLEAN NULL,
    carry_forward_cap NUMERIC(10,2) NULL,
    carry_forward_expires_after_months INT NULL,
    requires_document BOOLEAN NOT NULL DEFAULT false,
    past_booking_limit_days INT NULL,
    future_booking_limit_days INT NULL,
    include_weekend BOOLEAN NULL,
    include_holiday BOOLEAN NULL,
    exceed_balance_mode VARCHAR(16) NOT NULL,
    exceed_balance_limit_days NUMERIC(10,2) NULL,
    pro_rate_enabled BOOLEAN NULL,
    max_days_per_application NUMERIC(10,2) NULL,
    gender VARCHAR(32) NULL,
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_leave_policy_accrual_frequency CHECK (accrual_frequency IS NULL OR accrual_frequency IN ('monthly','yearly')),
    CONSTRAINT chk_leave_policy_reset_frequency CHECK (reset_frequency IS NULL OR reset_frequency IN ('yearly','monthly','quarterly','halfYearly')),
    CONSTRAINT chk_leave_policy_exceed_balance_mode CHECK (exceed_balance_mode IN ('noLimit','yearEndLimit','markAsLOP')),
    CONSTRAINT chk_leave_policy_exceed_balance_limit CHECK (
        (exceed_balance_mode = 'yearEndLimit' AND exceed_balance_limit_days IS NOT NULL) OR
        (exceed_balance_mode <> 'yearEndLimit' AND exceed_balance_limit_days IS NULL)
    )
);

CREATE INDEX idx_leave_policy_lookup ON core.leave_policy (tenant_id, leave_type_id, effective_from DESC);

ALTER TABLE core.leave_policy ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.leave_policy
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
