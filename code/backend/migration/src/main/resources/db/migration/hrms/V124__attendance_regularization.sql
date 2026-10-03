-- Migration: V124__attendance_regularization.sql
-- Description: W-40.4 hrms.attendance_regularization — an employee's request to correct a day's clock times, decided
-- through the REGULARIZATION approval flow; plus hrms.clock_session.regularization_id for the session it inserts

CREATE TABLE hrms.attendance_regularization (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id          UUID NOT NULL REFERENCES core.employee(id),
    attendance_date      DATE NOT NULL,
    requested_in_at      TIMESTAMPTZ NOT NULL,
    requested_out_at     TIMESTAMPTZ NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approval_instance_id UUID REFERENCES core.approval_instance(id),
    decided_at           TIMESTAMPTZ,
    decided_by           UUID,
    decision_comment     VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT attendance_regularization_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT attendance_regularization_order_check  CHECK (requested_out_at > requested_in_at)
);

CREATE UNIQUE INDEX uk_attendance_regularization_tenant_employee_date_pending
    ON hrms.attendance_regularization (tenant_id, employee_id, attendance_date) WHERE status = 'PENDING';
CREATE INDEX idx_attendance_regularization_tenant_employee_date
    ON hrms.attendance_regularization (tenant_id, employee_id, attendance_date DESC);
CREATE INDEX idx_attendance_regularization_tenant_status_date
    ON hrms.attendance_regularization (tenant_id, status, attendance_date DESC);
CREATE INDEX idx_attendance_regularization_tenant_instance
    ON hrms.attendance_regularization (tenant_id, approval_instance_id);

ALTER TABLE hrms.attendance_regularization ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.attendance_regularization
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

ALTER TABLE hrms.clock_session ADD COLUMN regularization_id UUID REFERENCES hrms.attendance_regularization(id);
CREATE INDEX idx_clock_session_tenant_regularization ON hrms.clock_session (tenant_id, regularization_id);
