-- Migration: V108__document_kind_form16.sql
-- Description: W-36.5 core.document — add FORM16_PART_A to document_kind_check

ALTER TABLE core.document DROP CONSTRAINT document_kind_check;
ALTER TABLE core.document ADD CONSTRAINT document_kind_check CHECK (kind IN (
    'EMPLOYEE_DOCUMENT', 'LEAVE_ATTACHMENT', 'REIMBURSEMENT_RECEIPT',
    'INVESTMENT_PROOF', 'PAYSLIP', 'EXPORT', 'FORM16_PART_A'));
