-- Migration: V108__document_kind_form16.sql
-- Schema: core
-- Purpose: core.document.kind accepts FORM16_PART_A (W-36.5, spec section 6)
--
-- A Form 16 Part A is the TRACES certificate of tax deposited. The officer uploads the ZIP from
-- TRACES and payroll files each certificate against its employee through DocumentService.storeFile.
-- DocumentKind.FORM16_PART_A is a system kind (DocumentKind.SYSTEM_GENERATED), so no client can
-- upload one through /api/v1/documents.
--
-- Widening only: the six existing kinds are kept, so every row already stored still passes.
-- V037 is not edited — it is applied wherever the branch has been built, and an applied script's
-- checksum must not change.

ALTER TABLE core.document DROP CONSTRAINT document_kind_check;

ALTER TABLE core.document
    ADD CONSTRAINT document_kind_check CHECK (kind IN (
        'EMPLOYEE_DOCUMENT', 'LEAVE_ATTACHMENT', 'REIMBURSEMENT_RECEIPT',
        'INVESTMENT_PROOF', 'PAYSLIP', 'EXPORT', 'FORM16_PART_A'));
