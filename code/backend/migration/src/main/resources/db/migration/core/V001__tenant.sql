-- W-07: core.tenant — root of every tenant relationship.
-- Every table in core, hrms, payroll carries a tenant_id column referencing
-- this table's tenant_id column. migration/README.md §tenant_id column standard.

CREATE TABLE core.tenant (
    id          bigserial    NOT NULL,
    tenant_id   uuid         NOT NULL,
    name        text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  text         NOT NULL DEFAULT 'system',
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  text         NOT NULL DEFAULT 'system',
    CONSTRAINT tenant_pkey PRIMARY KEY (id)
);

-- tenant_id is the external identifier shared with all other tables.
CREATE UNIQUE INDEX ON core.tenant (tenant_id);

-- Standard tenant-scoped index (migration/README.md §tenant_id column standard).
CREATE INDEX ON core.tenant (tenant_id, id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.tenant ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.tenant
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
