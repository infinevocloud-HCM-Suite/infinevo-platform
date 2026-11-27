-- Migration: V261__employee_bank_transfer_type_default_ft.sql
-- Description: the default bank transfer type is FT, not NEFT (founder 2026-11-27, same day as V260).
-- V260 is on main and may already be applied, so it is not edited; this changes the default and moves
-- the rows V260 filled with NEFT to FT.

ALTER TABLE core.employee_bank ALTER COLUMN transfer_type SET DEFAULT 'FT';

DO $$
BEGIN
    IF to_regclass('migration.flyway_schema_history') IS NOT NULL THEN
        -- Only rows nobody has saved since V260 ran: their NEFT came from V260's default, not from a
        -- person. A row saved after V260 (updated_at later than V260's install time) keeps its choice.
        UPDATE core.employee_bank b
           SET transfer_type = 'FT'
         WHERE b.transfer_type = 'NEFT'
           AND b.updated_at <= (SELECT h.installed_on
                                  FROM migration.flyway_schema_history h
                                 WHERE h.version = '260' AND h.success);
    ELSE
        -- No Flyway history: a schema built by running the scripts in a row (test schemas), where
        -- nobody can have chosen NEFT between V260 and here.
        UPDATE core.employee_bank SET transfer_type = 'FT' WHERE transfer_type = 'NEFT';
    END IF;
END
$$;
