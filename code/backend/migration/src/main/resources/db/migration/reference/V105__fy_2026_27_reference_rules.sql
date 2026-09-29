-- FY 2026-27 statutory reference rules for W-32:
-- 1. House rent allowance (Section 10(13A))
-- 2. Let-out property (Section 24(a))
-- Values are CARRIED FORWARD from FY 2025-26 unchanged. They have NOT been reviewed against
-- the law in force from 1 April 2026. The tax-rule owner must confirm them before release.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. House rent allowance, Section 10(13A)
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.hra_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.hra_rule_master
    (financial_year, tax_regime, metro_percent, non_metro_percent, basic_da_percent_threshold,
     pan_mandatory_threshold, is_month_wise_calculation, is_active, effective_from, effective_to)
SELECT
    '2026-2027',
    tax_regime,
    metro_percent,
    non_metro_percent,
    basic_da_percent_threshold,
    pan_mandatory_threshold,
    is_month_wise_calculation,
    is_active,
    DATE '2026-04-01',
    NULL
FROM reference.hra_rule_master
WHERE financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.hra_rule_master
       WHERE financial_year = '2026-2027' AND tax_regime = 'OLD'
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Let-out property, Section 24(a)
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.let_out_property_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.let_out_property_rule_master
    (financial_year, tax_regime, standard_deduction_percent, max_loss_setoff_limit,
     is_home_loan_interest_allowed, is_loss_carry_forward_allowed, is_active, effective_from, effective_to)
SELECT
    '2026-2027',
    tax_regime,
    standard_deduction_percent,
    max_loss_setoff_limit,
    is_home_loan_interest_allowed,
    is_loss_carry_forward_allowed,
    is_active,
    DATE '2026-04-01',
    NULL
FROM reference.let_out_property_rule_master
WHERE financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.let_out_property_rule_master
       WHERE financial_year = '2026-2027' AND tax_regime = 'OLD'
  );
