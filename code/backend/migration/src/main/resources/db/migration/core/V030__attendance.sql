-- Migration: V030__attendance.sql
-- Description: W-39.1 core.attendance — attendance capture (basic) per employee per date with row-level security isolation

CREATE TABLE core.attendance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    attendance_date DATE NOT NULL,
    status          VARCHAR(16) NOT NULL,
    source          VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    remarks         VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT attendance_status_check CHECK (status IN ('PRESENT','HALF_DAY','ABSENT')),
    CONSTRAINT attendance_source_check CHECK (source IN ('ADMIN','CLOCK'))
);

CREATE UNIQUE INDEX uk_attendance_tenant_employee_date ON core.attendance (tenant_id, employee_id, attendance_date);
CREATE INDEX idx_attendance_tenant_date ON core.attendance (tenant_id, attendance_date DESC);

ALTER TABLE core.attendance ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.attendance
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
