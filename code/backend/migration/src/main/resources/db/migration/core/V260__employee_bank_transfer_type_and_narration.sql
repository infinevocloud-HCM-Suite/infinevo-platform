-- Migration: V260__employee_bank_transfer_type_and_narration.sql
-- Description: core.employee_bank gets the bank transfer type (FT, IMPS, NEFT, RTGS) and the credit
-- narration (Salary, Consultant fees, Other + free text), so a bank upload file can carry both
-- (founder 2026-11-27). No new table: RLS and tenant_id are already on core.employee_bank (V019).

-- transfer_type is separate from payment_mode on purpose: payment_mode (BANK_TRANSFER, CHEQUE, CASH)
-- decides who is in the bank file at all; transfer_type is how the bank moves the money.
-- Existing rows and new rows start at NEFT and SALARY (founder 2026-11-27).
ALTER TABLE core.employee_bank
    ADD COLUMN transfer_type VARCHAR(16) NOT NULL DEFAULT 'NEFT',
    ADD COLUMN credit_narration VARCHAR(32) NOT NULL DEFAULT 'SALARY',
    ADD COLUMN credit_narration_other VARCHAR(100);

ALTER TABLE core.employee_bank
    ADD CONSTRAINT chk_employee_bank_transfer_type
        CHECK (transfer_type IN ('FT', 'IMPS', 'NEFT', 'RTGS')),
    ADD CONSTRAINT chk_employee_bank_credit_narration
        CHECK (credit_narration IN ('SALARY', 'CONSULTANT_FEES', 'OTHER')),
    -- The free text belongs to OTHER only, and OTHER needs it.
    ADD CONSTRAINT chk_employee_bank_credit_narration_other
        CHECK ((credit_narration = 'OTHER') = (credit_narration_other IS NOT NULL AND btrim(credit_narration_other) <> ''));
