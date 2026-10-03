-- FY 2026-27 tax rules for the W-33 calculators (W-33.3 § 2, § 6): slab masters and their
-- brackets, 87A rebate, surcharge and cess, standard deduction. V105 and V106 carried the
-- declaration rules; this carries the rest that TaxRuleReader reads.
-- Values are CARRIED FORWARD from FY 2025-26 unchanged (founder decision 2026-10-02). They have
-- NOT been reviewed against the law in force from 1 April 2026. The tax-rule owner must confirm
-- them before the first real pay run.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Slab masters (NEW GENERAL; OLD GENERAL, SENIOR, SUPER_SENIOR)
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.tax_slab_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.tax_slab_master
    (regime, financial_year, assessment_year, age_category, effective_from, effective_to, description, is_active)
SELECT
    m.regime,
    '2026-2027',
    '2027-2028',
    m.age_category,
    DATE '2026-04-01',
    NULL,
    m.regime || ' regime, FY 2026-27 (carried forward from FY 2025-26)',
    m.is_active
FROM reference.tax_slab_master m
WHERE m.financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.tax_slab_master n
       WHERE n.financial_year = '2026-2027'
         AND n.regime = m.regime
         AND n.age_category = m.age_category
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Slab brackets, copied onto the matching 2026-27 master
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.tax_slab_detail_history
    (slab_master_id, from_amount, to_amount, tax_rate_percent, slab_order)
SELECT
    n.id,
    d.from_amount,
    d.to_amount,
    d.tax_rate_percent,
    d.slab_order
FROM reference.tax_slab_master m
JOIN reference.tax_slab_detail_history d ON d.slab_master_id = m.id
JOIN reference.tax_slab_master n
  ON n.financial_year = '2026-2027'
 AND n.regime = m.regime
 AND n.age_category = m.age_category
WHERE m.financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.tax_slab_detail_history x
       WHERE x.slab_master_id = n.id
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Section 87A rebate
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.section87a_rebate_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.section87a_rebate_rule_master
    (financial_year, regime, income_threshold, max_rebate_amount, is_full_rebate, is_active, remarks,
     effective_from, effective_to)
SELECT
    '2026-2027',
    r.regime,
    r.income_threshold,
    r.max_rebate_amount,
    r.is_full_rebate,
    r.is_active,
    r.remarks,
    DATE '2026-04-01',
    NULL
FROM reference.section87a_rebate_rule_master r
WHERE r.financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.section87a_rebate_rule_master n
       WHERE n.financial_year = '2026-2027' AND n.regime = r.regime
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Surcharge and cess
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.cess_surcharge_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.cess_surcharge_rule_master
    (financial_year, rule_type, tax_regime, income_from, income_to, rate, is_marginal_relief_applicable,
     is_active, remarks, effective_from, effective_to)
SELECT
    '2026-2027',
    c.rule_type,
    c.tax_regime,
    c.income_from,
    c.income_to,
    c.rate,
    c.is_marginal_relief_applicable,
    c.is_active,
    c.remarks,
    DATE '2026-04-01',
    NULL
FROM reference.cess_surcharge_rule_master c
WHERE c.financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.cess_surcharge_rule_master n
       WHERE n.financial_year = '2026-2027'
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Standard deduction
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE reference.standard_deduction_rule_master
   SET effective_to = DATE '2026-03-31'
 WHERE financial_year = '2025-2026' AND effective_to IS NULL;

INSERT INTO reference.standard_deduction_rule_master
    (financial_year, regime, amount, description, is_active, effective_from, effective_to)
SELECT
    '2026-2027',
    s.regime,
    s.amount,
    s.description,
    s.is_active,
    DATE '2026-04-01',
    NULL
FROM reference.standard_deduction_rule_master s
WHERE s.financial_year = '2025-2026'
  AND NOT EXISTS (
      SELECT 1 FROM reference.standard_deduction_rule_master n
       WHERE n.financial_year = '2026-2027' AND n.regime = s.regime
  );
