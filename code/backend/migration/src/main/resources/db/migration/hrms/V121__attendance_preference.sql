-- Migration: V121__attendance_preference.sql
-- Description: W-40.1 hrms.attendance_preference — tenant-scoped attendance preferences with row-level security isolation

CREATE TABLE hrms.attendance_preference (
    id                                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                            UUID NOT NULL REFERENCES core.tenant(tenant_id),
    hours_calculation                    VARCHAR(24) NOT NULL DEFAULT 'EVERY_SESSION',
    full_day_minimum_hours               NUMERIC(4,2) NOT NULL DEFAULT 9.00,
    half_day_minimum_hours               NUMERIC(4,2) NOT NULL DEFAULT 4.50,
    regularization_window_days           INTEGER,
    max_regularizations_per_month        INTEGER,
    allow_regularization_without_session BOOLEAN NOT NULL DEFAULT true,
    created_at                           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                           VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at                           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                           VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT attendance_preference_calc_check  CHECK (hours_calculation IN ('FIRST_IN_LAST_OUT', 'EVERY_SESSION')),
    CONSTRAINT attendance_preference_hours_check CHECK (half_day_minimum_hours > 0
        AND half_day_minimum_hours < full_day_minimum_hours AND full_day_minimum_hours <= 24)
);

CREATE UNIQUE INDEX uk_attendance_preference_tenant ON hrms.attendance_preference (tenant_id);

ALTER TABLE hrms.attendance_preference ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.attendance_preference
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
