-- V003 test fixture — hrms schema
-- tenant_id as leading index column (02-data-model.md:363-372, spec §3).
CREATE TABLE hrms.hrms_fixture (
    id        bigserial NOT NULL,
    tenant_id uuid      NOT NULL,
    label     text      NOT NULL,
    CONSTRAINT hrms_fixture_pk PRIMARY KEY (id)
);

CREATE INDEX ON hrms.hrms_fixture (tenant_id, id);
