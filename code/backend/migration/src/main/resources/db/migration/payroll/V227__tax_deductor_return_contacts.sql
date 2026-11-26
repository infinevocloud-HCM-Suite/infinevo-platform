-- Migration: V227__tax_deductor_return_contacts.sql
-- Description: W-89 (Claude, 2026-11-25) deductor contact details for the Form 138 quarterly TDS file

-- W-89: what the Form 138 batch header needs beyond Form 16's deductor details. Optional here; the file
-- refuses to build, naming each missing one, until they are filled. The address is the filing work location's.
ALTER TABLE payroll.tax_deductor
    ADD COLUMN IF NOT EXISTS deductor_type CHAR(1) NULL
        CHECK (deductor_type IN ('A','S','D','E','G','H','L','N','K','M','P','T','J','B','Q','F')),
    ADD COLUMN IF NOT EXISTS email VARCHAR(75) NULL,
    ADD COLUMN IF NOT EXISTS phone VARCHAR(10) NULL CHECK (phone ~ '^[1-9][0-9]{5,9}$'),
    ADD COLUMN IF NOT EXISTS gstin VARCHAR(15) NULL CHECK (gstin ~ '^[0-9A-Z]{15}$'),
    ADD COLUMN IF NOT EXISTS responsible_pan CHAR(10) NULL CHECK (responsible_pan ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    ADD COLUMN IF NOT EXISTS responsible_email VARCHAR(75) NULL,
    ADD COLUMN IF NOT EXISTS responsible_phone VARCHAR(10) NULL CHECK (responsible_phone ~ '^[1-9][0-9]{5,9}$');
