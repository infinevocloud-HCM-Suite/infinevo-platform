-- Migration: V013__work_location.sql
-- Description: W-14.1 core.work_location — the tenant's physical work locations, each with a full address and the statutory filing-address flag, with row-level security isolation

CREATE TABLE core.work_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    state_code VARCHAR(8),
    zip_code VARCHAR(16),
    country_code CHAR(2),
    is_filing_address BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- state_code is deliberately a separate column from state, and not derived from it. Payroll
-- filings need the state's code, and free text cannot supply one: "Karnataka", "karnataka" and
-- "KA " are the same state to a human and three different values to a filing. The same reasoning
-- settled the address shape in W-13.2 (spec §6).

-- address_line1/2, city and state are free text at the lengths the rest of the schema uses for
-- names and addresses (VARCHAR(255) for lines, VARCHAR(100) to match core.employee's name
-- columns). country_code is CHAR(2) — ISO 3166-1 alpha-2, fixed width by definition.

-- Statutory registrations that hang off a filing address are W-31, not this ticket (spec §2).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- code is unique WITHIN a tenant, never globally: one tenant's coding scheme must not block another's.
CREATE UNIQUE INDEX idx_work_location_tenant_code ON core.work_location (tenant_id, code);
CREATE INDEX idx_work_location_tenant_is_active ON core.work_location (tenant_id, is_active);

-- At most ONE filing address per tenant (spec §9 — "more than one filing address per tenant",
-- mitigated by a partial unique index plus FilingAddressIT). The WHERE clause is what makes this
-- work: only the rows with is_filing_address = true take part in the index, so a tenant may hold
-- any number of ordinary work locations and exactly one that files. A plain UNIQUE (tenant_id,
-- is_filing_address) would instead cap the tenant at one non-filing location, which is wrong.
CREATE UNIQUE INDEX idx_work_location_tenant_filing_address
    ON core.work_location (tenant_id)
    WHERE is_filing_address;

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.work_location ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.work_location
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
