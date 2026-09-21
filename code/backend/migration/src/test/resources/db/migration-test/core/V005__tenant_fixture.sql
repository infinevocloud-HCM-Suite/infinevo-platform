-- V005 test fixture — core schema
-- Renumbered from V002 to avoid version-number collision with production
-- V002__user_tenant.sql (W-08). The two Flyway location trees are separate,
-- but keeping version numbers distinct eliminates any ambiguity.
-- tenant_id as leading index column (02-data-model.md:363-372, spec §3).
-- FK to reference.country_fixture proves cross-schema ordering: reference must run first.
CREATE TABLE core.tenant_fixture (
    id           bigserial    NOT NULL,
    tenant_id    uuid         NOT NULL,
    country_code char(2)      NOT NULL,
    CONSTRAINT tenant_fixture_pk PRIMARY KEY (id),
    CONSTRAINT tenant_fixture_country_fk FOREIGN KEY (country_code)
        REFERENCES reference.country_fixture (code)
);

CREATE INDEX ON core.tenant_fixture (tenant_id, id);
