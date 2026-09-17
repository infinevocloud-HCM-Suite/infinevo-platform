-- V001 test fixture — reference schema
-- No tenant_id, by design (D-08, 02-data-model.md:16, spec §13 R6).
-- reference holds shared national data. W-07's build check explicitly excludes this schema.
CREATE TABLE reference.country_fixture (
    code char(2)     NOT NULL,
    name text        NOT NULL,
    CONSTRAINT country_fixture_pk PRIMARY KEY (code)
);

INSERT INTO reference.country_fixture (code, name) VALUES ('IN', 'India');

