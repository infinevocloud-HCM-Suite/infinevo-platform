-- Migration: V111__proof_window_columns.sql
-- Description: W-34.1 proof-of-investment settings on the per-year window payroll.income_tax_declaration

ALTER TABLE payroll.income_tax_declaration
    ADD COLUMN poi_opens_on DATE,
    ADD COLUMN poi_due_date DATE,
    ADD COLUMN poi_locked BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN poi_attachment_mandatory BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN poi_comment_mandatory BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_income_tax_declaration_poi_dates
        CHECK (poi_due_date IS NULL OR poi_opens_on IS NULL OR poi_due_date >= poi_opens_on);
