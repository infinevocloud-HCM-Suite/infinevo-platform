-- Migration: V116__lop_policy.sql
-- Description: W-18.1 Loss-of-pay and working-day policy model with row-level security and seed (CORE-09, D-60)

-- 1. core.lop_policy — per-tenant, effective-dated policy deciding what a day of pay is worth
CREATE TABLE core.lop_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    working_day_basis VARCHAR(24) NOT NULL CHECK (working_day_basis IN ('ACTUAL_DAYS', 'ORG_DAYS', 'FIXED_30')),
    configured_days_per_month NUMERIC(10,2) NULL,
    weekends_payable BOOLEAN NOT NULL DEFAULT true,
    holidays_payable BOOLEAN NOT NULL DEFAULT true,
    lop_rounding VARCHAR(16) NOT NULL DEFAULT 'HALF_UP_2' CHECK (lop_rounding IN ('HALF_UP_2', 'HALF_UP_0', 'NONE')),
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Index on tenant_id plus lookup columns (DEBT-018)
CREATE INDEX idx_lop_policy_tenant_effective ON core.lop_policy (tenant_id, effective_from DESC);

-- Row-level security for lop_policy
ALTER TABLE core.lop_policy ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.lop_policy
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 2. Seed default ACTUAL_DAYS policy for every tenant in core.tenant that has none (W-18.1 §6, D-60)
INSERT INTO core.lop_policy (
    id,
    tenant_id,
    working_day_basis,
    configured_days_per_month,
    weekends_payable,
    holidays_payable,
    lop_rounding,
    effective_from,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    t.tenant_id,
    'ACTUAL_DAYS',
    NULL,
    true,
    true,
    'HALF_UP_2',
    '1900-01-01',
    'system',
    'system'
FROM core.tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM core.lop_policy p WHERE p.tenant_id = t.tenant_id
);

-- 3. Update core.provision_tenant to insert the default ACTUAL_DAYS policy in the same transaction (W-18.1 §6, D-60)
CREATE OR REPLACE FUNCTION core.provision_tenant(
    p_name text,
    p_country_code char(2),
    p_timezone varchar(64),
    p_leave_year_start_month smallint,
    p_modules text[]
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_tenant_id uuid := gen_random_uuid();
    v_sub_id uuid := gen_random_uuid();
    v_mod text;
BEGIN
    INSERT INTO core.tenant (
        tenant_id,
        name,
        country_code,
        timezone,
        leave_year_start_month,
        created_by,
        updated_by
    ) VALUES (
        v_tenant_id,
        p_name,
        coalesce(p_country_code, 'IN'),
        coalesce(p_timezone, 'Asia/Kolkata'),
        coalesce(p_leave_year_start_month, 4::smallint),
        'system',
        'system'
    );

    INSERT INTO core.subscription (
        id,
        tenant_id,
        status,
        started_on,
        created_by,
        updated_by
    ) VALUES (
        v_sub_id,
        v_tenant_id,
        'ACTIVE',
        CURRENT_DATE,
        'system',
        'system'
    );

    IF p_modules IS NOT NULL THEN
        FOREACH v_mod IN ARRAY p_modules LOOP
            IF v_mod IS NOT NULL AND trim(v_mod) <> '' THEN
                INSERT INTO core.subscription_module (
                    tenant_id,
                    subscription_id,
                    module,
                    granted_on,
                    created_by,
                    updated_by
                ) VALUES (
                    v_tenant_id,
                    v_sub_id,
                    trim(v_mod),
                    CURRENT_DATE,
                    'system',
                    'system'
                )
                ON CONFLICT DO NOTHING;
            END IF;
        END LOOP;
    END IF;

    -- W-18.1: Seed initial ACTUAL_DAYS policy for the newly provisioned tenant
    INSERT INTO core.lop_policy (
        id,
        tenant_id,
        working_day_basis,
        configured_days_per_month,
        weekends_payable,
        holidays_payable,
        lop_rounding,
        effective_from,
        created_by,
        updated_by
    ) VALUES (
        gen_random_uuid(),
        v_tenant_id,
        'ACTUAL_DAYS',
        NULL,
        true,
        true,
        'HALF_UP_2',
        '1900-01-01',
        'system',
        'system'
    );

    RETURN v_tenant_id;
END;
$$;
