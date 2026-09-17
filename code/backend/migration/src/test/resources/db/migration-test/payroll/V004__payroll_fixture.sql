-- V004 test fixture — payroll schema
-- tenant_id as leading index column (02-data-model.md:363-372, spec §3).
CREATE TABLE payroll.payroll_fixture (
    id        bigserial NOT NULL,
    tenant_id uuid      NOT NULL,
    label     text      NOT NULL,
    CONSTRAINT payroll_fixture_pk PRIMARY KEY (id)
);

CREATE INDEX ON payroll.payroll_fixture (tenant_id, id);
