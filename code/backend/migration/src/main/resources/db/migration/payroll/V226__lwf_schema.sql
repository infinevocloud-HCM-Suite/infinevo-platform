-- Migration: V226__lwf_schema.sql
-- Description: W-92 Labour Welfare Fund (LWF) reference tables, statutory seed data, and tenant settings

-- 1. National reference table of states and LWF levy status
CREATE TABLE IF NOT EXISTS reference.lwf_state (
    state_code VARCHAR(10) PRIMARY KEY REFERENCES reference.state(code),
    state_name VARCHAR(64) NOT NULL,
    levies_lwf BOOLEAN NOT NULL DEFAULT true
);

-- Seed reference.lwf_state from reference.state for Indian states/UTs
INSERT INTO reference.lwf_state (state_code, state_name, levies_lwf)
SELECT
    s.code,
    s.name,
    CASE WHEN s.code IN ('MH', 'KA', 'GJ', 'HR', 'TN', 'DL', 'AP', 'TS', 'KL', 'WB', 'MP', 'CH', 'PB', 'GA')
         THEN true ELSE false END
FROM reference.state s
WHERE s.country_code = 'IN'
ON CONFLICT (state_code) DO NOTHING;

-- 2. National dated reference table for LWF statutory calculation rules
CREATE TABLE IF NOT EXISTS reference.lwf_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_code VARCHAR(10) NOT NULL REFERENCES reference.lwf_state(state_code),
    effective_from DATE NOT NULL,
    effective_to DATE DEFAULT NULL,
    deduction_months INTEGER[] NOT NULL,
    calc_mode VARCHAR(32) NOT NULL, -- 'FLAT' or 'PERCENTAGE_WITH_CAP'
    min_gross NUMERIC(14, 4) NOT NULL DEFAULT 0.0000,
    max_gross NUMERIC(14, 4) DEFAULT NULL,
    employee_amount NUMERIC(14, 4) DEFAULT NULL,
    employer_amount NUMERIC(14, 4) DEFAULT NULL,
    employee_rate NUMERIC(6, 4) DEFAULT NULL,
    employer_rate NUMERIC(6, 4) DEFAULT NULL,
    employee_cap NUMERIC(14, 4) DEFAULT NULL,
    employer_cap NUMERIC(14, 4) DEFAULT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lwf_rule_lookup
    ON reference.lwf_rule (state_code, effective_from);

-- Seed statutory reference rules for the 14 states
INSERT INTO reference.lwf_rule (
    state_code, effective_from, effective_to, deduction_months, calc_mode,
    min_gross, max_gross, employee_amount, employer_amount, employee_rate, employer_rate, employee_cap, employer_cap
) VALUES
    ('MH', '2024-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 25.0000, 75.0000, NULL, NULL, NULL, NULL),
    ('KA', '2024-01-01', NULL, ARRAY[12], 'FLAT', 0.0000, NULL, 50.0000, 100.0000, NULL, NULL, NULL, NULL),
    ('GJ', '2020-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 6.0000, 12.0000, NULL, NULL, NULL, NULL),
    ('HR', '2020-01-01', NULL, ARRAY[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], 'PERCENTAGE_WITH_CAP', 0.0000, NULL, NULL, NULL, 0.0020, 0.0040, 25.0000, 50.0000),
    ('TN', '2020-01-01', NULL, ARRAY[12], 'FLAT', 0.0000, NULL, 20.0000, 40.0000, NULL, NULL, NULL, NULL),
    ('DL', '2020-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 0.7500, 2.2500, NULL, NULL, NULL, NULL),
    ('AP', '2020-01-01', NULL, ARRAY[12], 'FLAT', 0.0000, NULL, 20.0000, 50.0000, NULL, NULL, NULL, NULL),
    ('TS', '2020-01-01', NULL, ARRAY[12], 'FLAT', 0.0000, NULL, 20.0000, 50.0000, NULL, NULL, NULL, NULL),
    ('KL', '2020-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 20.0000, 20.0000, NULL, NULL, NULL, NULL),
    ('WB', '2020-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 3.0000, 15.0000, NULL, NULL, NULL, NULL),
    ('MP', '2020-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 10.0000, 30.0000, NULL, NULL, NULL, NULL),
    ('CH', '2020-01-01', NULL, ARRAY[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], 'FLAT', 0.0000, NULL, 5.0000, 20.0000, NULL, NULL, NULL, NULL),
    ('PB', '2020-01-01', NULL, ARRAY[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], 'FLAT', 0.0000, NULL, 5.0000, 20.0000, NULL, NULL, NULL, NULL),
    ('GA', '2020-01-01', NULL, ARRAY[6, 12], 'FLAT', 0.0000, NULL, 60.0000, 180.0000, NULL, NULL, NULL, NULL);

-- 3. Tenant LWF registration and toggle (no amount or cycle columns per founder decision)
CREATE TABLE IF NOT EXISTS payroll.org_lwf_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    state_code VARCHAR(10) NOT NULL REFERENCES reference.lwf_state(state_code),
    registration_number VARCHAR(32) DEFAULT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_org_lwf_setting UNIQUE (tenant_id, state_code)
);

CREATE INDEX IF NOT EXISTS idx_org_lwf_setting_tenant
    ON payroll.org_lwf_setting (tenant_id);

ALTER TABLE payroll.org_lwf_setting ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.org_lwf_setting
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

