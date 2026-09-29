-- FY 2026-27 statutory reference rules for W-33 (out of W-32 scope):
-- 1. Home loan interest and deductions (Section 24(b), 80EE, 80EEA)
-- 2. Other income savings deductions (Section 80TTA, 80TTB)
-- Values are CARRIED FORWARD from FY 2025-26 unchanged. They have NOT been reviewed against
-- the law in force from 1 April 2026. The tax-rule owner must confirm them before release.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Home loan, Section 24(b), 80EE and 80EEA (carry-forward for W-33)
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.home_loan_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.home_loan_rule_master
    (financial_year, section_code, section_name, component, property_type, max_limit,
     loan_sanction_from, loan_sanction_to, is_first_time_buyer, is_active, remarks, effective_from, effective_to)
SELECT
    '2026-2027',
    section_code,
    section_name,
    component,
    property_type,
    max_limit,
    loan_sanction_from,
    loan_sanction_to,
    is_first_time_buyer,
    is_active,
    remarks,
    DATE '2026-04-01',
    NULL
FROM reference.home_loan_rule_master
WHERE financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.home_loan_rule_master
       WHERE financial_year = '2026-2027'
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Other income: Sections 80TTA and 80TTB (carry-forward for W-33)
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.other_income_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.other_income_rule_master
    (financial_year, section_code, section_name, rule_type, tax_regime, max_limit,
     deduction_percent, is_proof_required, is_conditional, is_allowed, is_active, effective_from, effective_to)
SELECT
    '2026-2027',
    section_code,
    section_name,
    rule_type,
    tax_regime,
    max_limit,
    deduction_percent,
    is_proof_required,
    is_conditional,
    is_allowed,
    is_active,
    DATE '2026-04-01',
    NULL
FROM reference.other_income_rule_master
WHERE financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.other_income_rule_master
       WHERE financial_year = '2026-2027'
  );
