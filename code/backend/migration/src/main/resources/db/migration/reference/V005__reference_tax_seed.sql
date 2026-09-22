-- W-09: statutory seed for FY 2023-24, FY 2024-25 and FY 2025-26.
--
-- There is no legacy script to port. legacy/Payroll-Bend-SBoot ships no data.sql,
-- import.sql or seed SQL of any kind, and its only CommandLineRunner
-- (MasterDataInitializer.java:96-98) seeds EPF and ESI MasterConfig rows and nothing else.
-- The tax rules existed only as rows typed by hand into the running MySQL database under
-- ddl-auto=update (DEBT-002). Every value below is therefore written from the Finance Acts
-- rather than copied, and every one is asserted in ReferenceSchemaIT — an unasserted value
-- here would be a number nobody has checked against the law.
--
-- The annual update is one more file like this one: a new V0NN__ script inserting the new
-- financial year's rows. Nothing in this file is ever edited afterwards — Flyway records
-- its checksum, and a later edit fails validation rather than silently diverging from what
-- the deployed databases already ran.

-- ─────────────────────────────────────────────────────────────────────────────
-- Slab headers — three financial years, two regimes each, general age category.
-- SENIOR and SUPER_SENIOR are not seeded: the old regime's higher exemption limits for
-- them are a W-33 concern and would be unasserted rows here.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.tax_slab_master
    (regime, financial_year, assessment_year, age_category, effective_from, effective_to, description) VALUES
    ('OLD', '2023-2024', '2024-2025', 'GENERAL', DATE '2023-04-01', DATE '2024-03-31', 'Old regime, FY 2023-24'),
    ('NEW', '2023-2024', '2024-2025', 'GENERAL', DATE '2023-04-01', DATE '2024-03-31', 'New regime, FY 2023-24 (Finance Act 2023)'),
    ('OLD', '2024-2025', '2025-2026', 'GENERAL', DATE '2024-04-01', DATE '2025-03-31', 'Old regime, FY 2024-25'),
    ('NEW', '2024-2025', '2025-2026', 'GENERAL', DATE '2024-04-01', DATE '2025-03-31', 'New regime, FY 2024-25 (Finance (No. 2) Act 2024)'),
    ('OLD', '2025-2026', '2026-2027', 'GENERAL', DATE '2025-04-01', NULL,              'Old regime, FY 2025-26'),
    ('NEW', '2025-2026', '2026-2027', 'GENERAL', DATE '2025-04-01', NULL,              'New regime, FY 2025-26 (Union Budget 2025)');

-- ─────────────────────────────────────────────────────────────────────────────
-- Rate brackets. Joined to the header by (financial_year, regime) rather than by a
-- hardcoded id, because the ids are generated.
--
-- The old regime is unchanged across all three years: 2.5L / 5L / 10L at 0, 5, 20, 30.
-- The new regime changed in each of them, which is the whole reason these are rows and
-- not constants in the calculator.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.tax_slab_detail_history (slab_master_id, from_amount, to_amount, tax_rate_percent, slab_order)
SELECT m.id, v.from_amount, v.to_amount, v.rate, v.ord
FROM reference.tax_slab_master m
JOIN (VALUES
    -- Old regime — identical in FY 2023-24, FY 2024-25 and FY 2025-26.
    ('2023-2024', 'OLD', 1,       0.0000,   250000.0000,  0.00),
    ('2023-2024', 'OLD', 2,  250000.0000,   500000.0000,  5.00),
    ('2023-2024', 'OLD', 3,  500000.0000,  1000000.0000, 20.00),
    ('2023-2024', 'OLD', 4, 1000000.0000,  NULL,         30.00),
    ('2024-2025', 'OLD', 1,       0.0000,   250000.0000,  0.00),
    ('2024-2025', 'OLD', 2,  250000.0000,   500000.0000,  5.00),
    ('2024-2025', 'OLD', 3,  500000.0000,  1000000.0000, 20.00),
    ('2024-2025', 'OLD', 4, 1000000.0000,  NULL,         30.00),
    ('2025-2026', 'OLD', 1,       0.0000,   250000.0000,  0.00),
    ('2025-2026', 'OLD', 2,  250000.0000,   500000.0000,  5.00),
    ('2025-2026', 'OLD', 3,  500000.0000,  1000000.0000, 20.00),
    ('2025-2026', 'OLD', 4, 1000000.0000,  NULL,         30.00),

    -- New regime FY 2023-24 — six bands, 3L / 6L / 9L / 12L / 15L.
    ('2023-2024', 'NEW', 1,       0.0000,   300000.0000,  0.00),
    ('2023-2024', 'NEW', 2,  300000.0000,   600000.0000,  5.00),
    ('2023-2024', 'NEW', 3,  600000.0000,   900000.0000, 10.00),
    ('2023-2024', 'NEW', 4,  900000.0000,  1200000.0000, 15.00),
    ('2023-2024', 'NEW', 5, 1200000.0000,  1500000.0000, 20.00),
    ('2023-2024', 'NEW', 6, 1500000.0000,  NULL,         30.00),

    -- New regime FY 2024-25 — the second band widens to 7L and the third to 10L.
    ('2024-2025', 'NEW', 1,       0.0000,   300000.0000,  0.00),
    ('2024-2025', 'NEW', 2,  300000.0000,   700000.0000,  5.00),
    ('2024-2025', 'NEW', 3,  700000.0000,  1000000.0000, 10.00),
    ('2024-2025', 'NEW', 4, 1000000.0000,  1200000.0000, 15.00),
    ('2024-2025', 'NEW', 5, 1200000.0000,  1500000.0000, 20.00),
    ('2024-2025', 'NEW', 6, 1500000.0000,  NULL,         30.00),

    -- New regime FY 2025-26 — seven bands, nil to 4L, and a 25% band that did not
    -- previously exist. This is the one most likely to be got wrong from memory.
    ('2025-2026', 'NEW', 1,       0.0000,   400000.0000,  0.00),
    ('2025-2026', 'NEW', 2,  400000.0000,   800000.0000,  5.00),
    ('2025-2026', 'NEW', 3,  800000.0000,  1200000.0000, 10.00),
    ('2025-2026', 'NEW', 4, 1200000.0000,  1600000.0000, 15.00),
    ('2025-2026', 'NEW', 5, 1600000.0000,  2000000.0000, 20.00),
    ('2025-2026', 'NEW', 6, 2000000.0000,  2400000.0000, 25.00),
    ('2025-2026', 'NEW', 7, 2400000.0000,  NULL,         30.00)
) AS v(fy, regime, ord, from_amount, to_amount, rate)
  ON m.financial_year = v.fy AND m.regime = v.regime
WHERE m.age_category = 'GENERAL';

-- Version 1 of each header, so the history table has a baseline to version from when a
-- future Finance Act supersedes one of these.
INSERT INTO reference.tax_slab_master_history (master_id, version, change_reason, action_type, changed_by, valid_from)
SELECT m.id, 1, 'Initial statutory seed (W-09)', 'INSERT', 'system', m.effective_from
FROM reference.tax_slab_master m;

-- ─────────────────────────────────────────────────────────────────────────────
-- Standard deduction. 50,000 throughout the old regime; the new regime rises to 75,000
-- from FY 2024-25 (Finance (No. 2) Act 2024).
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.standard_deduction_rule_master
    (financial_year, regime, amount, description, effective_from, effective_to) VALUES
    ('2023-2024', 'OLD', 50000.0000, 'Standard deduction, old regime',                 DATE '2023-04-01', DATE '2024-03-31'),
    ('2023-2024', 'NEW', 50000.0000, 'Standard deduction, new regime',                 DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', 'OLD', 50000.0000, 'Standard deduction, old regime',                 DATE '2024-04-01', DATE '2025-03-31'),
    ('2024-2025', 'NEW', 75000.0000, 'Standard deduction, new regime (raised to 75k)', DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', 'OLD', 50000.0000, 'Standard deduction, old regime',                 DATE '2025-04-01', NULL),
    ('2025-2026', 'NEW', 75000.0000, 'Standard deduction, new regime',                 DATE '2025-04-01', NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- Section 87A rebate. The old regime has not moved: 12,500 up to 5,00,000. The new
-- regime went 25,000 up to 7,00,000, then 60,000 up to 12,00,000 for FY 2025-26.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.section87a_rebate_rule_master
    (financial_year, regime, income_threshold, max_rebate_amount, remarks, effective_from, effective_to) VALUES
    ('2023-2024', 'OLD',  500000.0000, 12500.0000, 'Section 87A, old regime',                      DATE '2023-04-01', DATE '2024-03-31'),
    ('2023-2024', 'NEW',  700000.0000, 25000.0000, 'Section 87A, new regime',                      DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', 'OLD',  500000.0000, 12500.0000, 'Section 87A, old regime',                      DATE '2024-04-01', DATE '2025-03-31'),
    ('2024-2025', 'NEW',  700000.0000, 25000.0000, 'Section 87A, new regime',                      DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', 'OLD',  500000.0000, 12500.0000, 'Section 87A, old regime',                      DATE '2025-04-01', NULL),
    ('2025-2026', 'NEW', 1200000.0000, 60000.0000, 'Section 87A, new regime (Union Budget 2025)',  DATE '2025-04-01', NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- House rent allowance, Section 10(13A). Old regime only — the new regime grants no HRA
-- exemption, so seeding a NEW row would assert a rule that does not exist.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.hra_rule_master
    (financial_year, tax_regime, metro_percent, non_metro_percent, basic_da_percent_threshold,
     pan_mandatory_threshold, is_month_wise_calculation, effective_from, effective_to) VALUES
    ('2023-2024', 'OLD', 50.00, 40.00, 10.00, 100000.0000, TRUE, DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', 'OLD', 50.00, 40.00, 10.00, 100000.0000, TRUE, DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', 'OLD', 50.00, 40.00, 10.00, 100000.0000, TRUE, DATE '2025-04-01', NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- Home loan. 24(b) caps self-occupied interest at 2,00,000 and leaves let-out interest
-- uncapped — the let-out restriction is the set-off limit below, not an interest cap.
-- 80EE and 80EEA are closed sanction windows: no new loan qualifies, but loans sanctioned
-- inside the window keep the deduction for their life, so the rules stay seeded.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.home_loan_rule_master
    (financial_year, section_code, section_name, component, property_type, max_limit,
     loan_sanction_from, loan_sanction_to, is_first_time_buyer, remarks, effective_from, effective_to)
SELECT y.fy, r.section_code, r.section_name, r.component, r.property_type, r.max_limit,
       r.sanction_from, r.sanction_to, r.first_time_buyer, r.remarks, y.effective_from, y.effective_to
FROM (VALUES
    ('24B',   'Interest on borrowed capital', 'INTEREST', 'SELF_OCCUPIED', 200000.0000::NUMERIC(19,4), NULL::DATE,        NULL::DATE,        FALSE, 'Section 24(b) cap for a self-occupied property'),
    ('24B',   'Interest on borrowed capital', 'INTEREST', 'LET_OUT',       NULL,                       NULL,              NULL,              FALSE, 'Interest itself is uncapped for a let-out property; the loss set-off is capped'),
    ('80EE',  'Additional interest, 80EE',    'INTEREST', 'SELF_OCCUPIED',  50000.0000,                DATE '2016-04-01', DATE '2017-03-31', TRUE,  'Closed sanction window; loans inside it keep the deduction'),
    ('80EEA', 'Additional interest, 80EEA',   'INTEREST', 'SELF_OCCUPIED', 150000.0000,                DATE '2019-04-01', DATE '2022-03-31', TRUE,  'Closed sanction window; loans inside it keep the deduction')
) AS r(section_code, section_name, component, property_type, max_limit, sanction_from, sanction_to, first_time_buyer, remarks)
CROSS JOIN (VALUES
    ('2023-2024', DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', DATE '2025-04-01', NULL::DATE)
) AS y(fy, effective_from, effective_to);

-- ─────────────────────────────────────────────────────────────────────────────
-- Let-out property: 30% standard deduction on net annual value under 24(a), and the
-- 2,00,000 cap on house-property loss set off against salary.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.let_out_property_rule_master
    (financial_year, tax_regime, standard_deduction_percent, max_loss_setoff_limit,
     is_home_loan_interest_allowed, is_loss_carry_forward_allowed, effective_from, effective_to) VALUES
    ('2023-2024', 'OLD', 30.00, 200000.0000, TRUE, TRUE, DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', 'OLD', 30.00, 200000.0000, TRUE, TRUE, DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', 'OLD', 30.00, 200000.0000, TRUE, TRUE, DATE '2025-04-01', NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- Savings interest: 80TTA (10,000, under 60) and 80TTB (50,000, senior citizen).
-- Old regime only; neither is available under the new regime.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.other_income_rule_master
    (financial_year, section_code, section_name, rule_type, tax_regime, max_limit,
     is_proof_required, is_conditional, effective_from, effective_to) VALUES
    ('2023-2024', '80TTA', 'Interest on savings account',            'DEDUCTION', 'OLD', 10000.0000, FALSE, FALSE, DATE '2023-04-01', DATE '2024-03-31'),
    ('2023-2024', '80TTB', 'Interest income, senior citizen',        'DEDUCTION', 'OLD', 50000.0000, FALSE, TRUE,  DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', '80TTA', 'Interest on savings account',            'DEDUCTION', 'OLD', 10000.0000, FALSE, FALSE, DATE '2024-04-01', DATE '2025-03-31'),
    ('2024-2025', '80TTB', 'Interest income, senior citizen',        'DEDUCTION', 'OLD', 50000.0000, FALSE, TRUE,  DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', '80TTA', 'Interest on savings account',            'DEDUCTION', 'OLD', 10000.0000, FALSE, FALSE, DATE '2025-04-01', NULL),
    ('2025-2026', '80TTB', 'Interest income, senior citizen',        'DEDUCTION', 'OLD', 50000.0000, FALSE, TRUE,  DATE '2025-04-01', NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- Chapter VI-A heads.
--
-- This table and cess_surcharge_rule_master below are the two the legacy engine reads as
-- a list (OldTaxCalculationServiceImpl.java:826,1318,1399). An empty table there raises
-- nothing: it yields zero deductions and no cess, and the payslip is simply wrong. The
-- other masters are read with orElseThrow and fail loudly. That asymmetry is why these two
-- have dedicated assertions in ReferenceSchemaIT rather than a count check.
--
-- A NULL max_limit means the section has no flat ceiling — 80E has none, 80G and 80GGA
-- depend on the donee and on qualifying-amount arithmetic W-33 owns.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.section6a_item_master
    (section_code, category, name, description, max_limit, category_group_code,
     is_80c, is_80d, is_other_section, is_allowed_in_new_regime, display_order) VALUES
    ('80C',       'INVESTMENT',       'Investments under Section 80C',        'Life insurance, PPF, EPF, ELSS, principal on a housing loan, tuition fees', 150000.0000, '80C_GROUP', TRUE,  FALSE, FALSE, FALSE,  1),
    ('80CCC',     'INVESTMENT',       'Pension fund contribution',            'Contribution to an annuity plan of a life insurer',                          150000.0000, '80C_GROUP', TRUE,  FALSE, FALSE, FALSE,  2),
    ('80CCD(1)',  'INVESTMENT',       'NPS, employee contribution',           'Employee share of the National Pension System',                              150000.0000, '80C_GROUP', TRUE,  FALSE, FALSE, FALSE,  3),
    ('80CCD(1B)', 'INVESTMENT',       'NPS, additional contribution',         'Additional deduction over and above the 80C group ceiling',                   50000.0000, NULL,        FALSE, FALSE, TRUE,  FALSE,  4),
    ('80CCD(2)',  'INVESTMENT',       'NPS, employer contribution',           'Employer share; a percentage of salary rather than a flat cap',                     NULL, NULL,        FALSE, FALSE, TRUE,  TRUE,   5),
    ('80D',       'HEALTH_INSURANCE', 'Health insurance premium',             'Self and family, plus parents; the ceiling rises where a parent is a senior', 100000.0000, NULL,        FALSE, TRUE,  FALSE, FALSE,  6),
    ('80DD',      'DISABILITY',       'Maintenance of a disabled dependant',  'Flat deduction; the higher figure applies to severe disability',             125000.0000, NULL,        FALSE, FALSE, TRUE,  FALSE,  7),
    ('80DDB',     'MEDICAL',          'Treatment of a specified disease',     'Ceiling differs for a senior citizen',                                       100000.0000, NULL,        FALSE, FALSE, TRUE,  FALSE,  8),
    ('80E',       'EDUCATION_LOAN',   'Interest on an education loan',        'No ceiling; available for eight assessment years',                                  NULL, NULL,        FALSE, FALSE, TRUE,  FALSE,  9),
    ('80G',       'DONATION',         'Donations to charitable institutions', 'Qualifying amount and rate depend on the donee',                                    NULL, NULL,        FALSE, FALSE, TRUE,  FALSE, 10),
    ('80GGA',     'DONATION',         'Donation for scientific research',     'Rural development and scientific research',                                         NULL, NULL,        FALSE, FALSE, TRUE,  FALSE, 11),
    ('80GGC',     'DONATION',         'Contribution to a political party',    'No ceiling; non-cash contributions only',                                           NULL, NULL,        FALSE, FALSE, TRUE,  FALSE, 12),
    ('80U',       'DISABILITY',       'Self, person with a disability',       'Flat deduction; the higher figure applies to severe disability',             125000.0000, NULL,        FALSE, FALSE, TRUE,  FALSE, 13);

-- ─────────────────────────────────────────────────────────────────────────────
-- Cess and surcharge.
--
-- Health and education cess is 4% of tax plus surcharge, for both regimes.
-- Surcharge has four income thresholds — 50L, 1Cr, 2Cr, 5Cr. The top band is the one
-- place the regimes differ: 37% under the old regime, capped at 25% under the new.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.cess_surcharge_rule_master
    (financial_year, rule_type, tax_regime, income_from, income_to, rate,
     is_marginal_relief_applicable, remarks, effective_from, effective_to)
SELECT y.fy, r.rule_type, r.tax_regime, r.income_from, r.income_to, r.rate,
       r.marginal_relief, r.remarks, y.effective_from, y.effective_to
FROM (VALUES
    ('CESS',      'BOTH',        NULL::NUMERIC(19,4), NULL::NUMERIC(19,4),  4.00, FALSE, 'Health and education cess on tax plus surcharge'),
    ('SURCHARGE', 'BOTH',   5000000.0000,  10000000.0000, 10.00, TRUE,  'Total income above 50,00,000 and up to 1,00,00,000'),
    ('SURCHARGE', 'BOTH',  10000000.0000,  20000000.0000, 15.00, TRUE,  'Total income above 1,00,00,000 and up to 2,00,00,000'),
    ('SURCHARGE', 'BOTH',  20000000.0000,  50000000.0000, 25.00, TRUE,  'Total income above 2,00,00,000 and up to 5,00,00,000'),
    ('SURCHARGE', 'OLD',   50000000.0000,  NULL,          37.00, TRUE,  'Total income above 5,00,00,000, old regime'),
    ('SURCHARGE', 'NEW',   50000000.0000,  NULL,          25.00, TRUE,  'Total income above 5,00,00,000, new regime — capped at 25%')
) AS r(rule_type, tax_regime, income_from, income_to, rate, marginal_relief, remarks)
CROSS JOIN (VALUES
    ('2023-2024', DATE '2023-04-01', DATE '2024-03-31'),
    ('2024-2025', DATE '2024-04-01', DATE '2025-03-31'),
    ('2025-2026', DATE '2025-04-01', NULL::DATE)
) AS y(fy, effective_from, effective_to);
