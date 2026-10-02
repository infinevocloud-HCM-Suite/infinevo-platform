-- Migration: V123__clock_session.sql
-- Description: W-40.3 hrms.clock_session — clock-in and clock-out session tracking with row-level security

CREATE TABLE hrms.clock_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    attendance_date DATE NOT NULL,
    clock_in_at     TIMESTAMPTZ NOT NULL,
    clock_out_at    TIMESTAMPTZ,
    origin          VARCHAR(16) NOT NULL DEFAULT 'CLOCK',
    voided_at       TIMESTAMPTZ,
    void_reason     VARCHAR(24),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT clock_session_order_check  CHECK (clock_out_at IS NULL OR clock_out_at > clock_in_at),
    CONSTRAINT clock_session_origin_check CHECK (origin IN ('CLOCK', 'REGULARIZATION')),
    CONSTRAINT clock_session_void_check   CHECK ((voided_at IS NULL) = (void_reason IS NULL)
        AND (void_reason IS NULL OR void_reason IN ('NOT_CLOCKED_OUT', 'REGULARIZED')))
);

CREATE UNIQUE INDEX uk_clock_session_tenant_employee_open
    ON hrms.clock_session (tenant_id, employee_id) WHERE clock_out_at IS NULL AND voided_at IS NULL;
CREATE INDEX idx_clock_session_tenant_employee_date ON hrms.clock_session (tenant_id, employee_id, attendance_date DESC);
CREATE INDEX idx_clock_session_tenant_date          ON hrms.clock_session (tenant_id, attendance_date DESC);

ALTER TABLE hrms.clock_session ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.clock_session
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
