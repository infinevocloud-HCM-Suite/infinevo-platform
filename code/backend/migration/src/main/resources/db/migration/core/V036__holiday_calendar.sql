-- Migration: V036__holiday_calendar.sql
-- Description: W-17 Holiday calendar, holidays and location assignments with row-level security isolation

-- 1. core.holiday_calendar — named calendar owned by a tenant, with at most one default calendar per tenant
CREATE TABLE core.holiday_calendar (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name VARCHAR(128) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Composite index, tenant_id leading (DEBT-018)
CREATE INDEX idx_holiday_calendar_tenant ON core.holiday_calendar (tenant_id);

-- At most one default calendar per tenant (12-core-contracts.md §5 row 9)
CREATE UNIQUE INDEX idx_holiday_calendar_tenant_default ON core.holiday_calendar (tenant_id) WHERE is_default;

-- Row-level security for holiday_calendar
ALTER TABLE core.holiday_calendar ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.holiday_calendar
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 2. core.holiday — dated entry supporting date range and restricted holiday flag
CREATE TABLE core.holiday (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    calendar_id UUID NOT NULL REFERENCES core.holiday_calendar(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    is_restricted BOOLEAN NOT NULL DEFAULT false,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_holiday_date_range CHECK (to_date >= from_date)
);

-- Composite lookup index, tenant_id leading (DEBT-018)
CREATE INDEX idx_holiday_tenant_calendar_from ON core.holiday (tenant_id, calendar_id, from_date);

-- Row-level security for holiday
ALTER TABLE core.holiday ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.holiday
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 3. core.holiday_calendar_location — calendar to work location assignment
-- Exactly one calendar per work location (12-core-contracts.md §5 row 9)
CREATE TABLE core.holiday_calendar_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    calendar_id UUID NOT NULL REFERENCES core.holiday_calendar(id) ON DELETE CASCADE,
    work_location_id UUID NOT NULL REFERENCES core.work_location(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique (tenant_id, work_location_id) ensures a location resolves to exactly one calendar
CREATE UNIQUE INDEX idx_holiday_calendar_location_tenant_location ON core.holiday_calendar_location (tenant_id, work_location_id);
CREATE INDEX idx_holiday_calendar_location_tenant_calendar ON core.holiday_calendar_location (tenant_id, calendar_id);

-- Row-level security for holiday_calendar_location
ALTER TABLE core.holiday_calendar_location ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.holiday_calendar_location
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
