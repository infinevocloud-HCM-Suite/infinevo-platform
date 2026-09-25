-- Migration: V033__tenant_locale_columns.sql
-- Schema: core
-- Purpose: Add locale and operational defaults to core.tenant (W-12.1, 12-core-contracts.md §7 row 1)

ALTER TABLE core.tenant
    ADD COLUMN country_code CHAR(2) NULL DEFAULT 'IN',
    ADD COLUMN timezone VARCHAR(64) NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN leave_year_start_month SMALLINT NULL DEFAULT 4 CHECK (leave_year_start_month BETWEEN 1 AND 12);
