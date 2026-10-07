-- Migration: V153__epf_edli_admin_split.sql
-- Description: D-39 payroll.epf_setting keeps EDLI and EPF admin charges as separate choices, as the frozen
-- Payroll app does (editEPF.js:443,457). V062 merged them into one pair of switches; this splits each into two.
-- Backfill: every existing row keeps its behaviour, so the EDLI and admin switch both take the merged value.
-- No new table, so no RLS change; the existing tenant_isolation policy covers the new columns.
-- The merged columns stay: the previous release still maps them while this one rolls out (migration
-- README, "drop the old structure in a later release"). They keep their defaults, nothing reads them from
-- this release on, and a later migration drops them.

ALTER TABLE payroll.epf_setting
    ADD COLUMN include_edli_in_ctc BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN include_admin_in_ctc BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN include_edli_in_structure BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN include_admin_in_structure BOOLEAN NOT NULL DEFAULT false;

UPDATE payroll.epf_setting
SET include_edli_in_ctc = include_edli_admin_in_ctc,
    include_admin_in_ctc = include_edli_admin_in_ctc,
    include_edli_in_structure = include_edli_admin_in_structure,
    include_admin_in_structure = include_edli_admin_in_structure;
