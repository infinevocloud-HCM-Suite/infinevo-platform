-- ─────────────────────────────────────────────────────────────────────────────
-- Senior and Super-Senior Tax Slabs (W-09.1)
-- ─────────────────────────────────────────────────────────────────────────────
-- Under the old regime:
-- - Senior citizens (60-79) have a basic exemption limit of 3,00,000.
-- - Super senior citizens (80+) have a basic exemption limit of 5,00,000.
-- Unchanged across FY 2023-24, FY 2024-25, and FY 2025-26.
-- Section 115BAC (new regime) has one slab table for all ages; no age variant exists.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.tax_slab_master
    (regime, financial_year, assessment_year, age_category, effective_from, effective_to, description) VALUES
    ('OLD', '2023-2024', '2024-2025', 'SENIOR',       DATE '2023-04-01', DATE '2024-03-31', 'Old regime, FY 2023-24, resident 60-79'),
    ('OLD', '2023-2024', '2024-2025', 'SUPER_SENIOR', DATE '2023-04-01', DATE '2024-03-31', 'Old regime, FY 2023-24, resident 80+'),
    ('OLD', '2024-2025', '2025-2026', 'SENIOR',       DATE '2024-04-01', DATE '2025-03-31', 'Old regime, FY 2024-25, resident 60-79'),
    ('OLD', '2024-2025', '2025-2026', 'SUPER_SENIOR', DATE '2024-04-01', DATE '2025-03-31', 'Old regime, FY 2024-25, resident 80+'),
    ('OLD', '2025-2026', '2026-2027', 'SENIOR',       DATE '2025-04-01', NULL,              'Old regime, FY 2025-26, resident 60-79'),
    ('OLD', '2025-2026', '2026-2027', 'SUPER_SENIOR', DATE '2025-04-01', NULL,              'Old regime, FY 2025-26, resident 80+');

INSERT INTO reference.tax_slab_detail_history (slab_master_id, from_amount, to_amount, tax_rate_percent, slab_order)
SELECT m.id, v.from_amount, v.to_amount, v.rate, v.ord
FROM reference.tax_slab_master m
JOIN (VALUES
    -- SENIOR: nil to 3,00,000 — per year, 4 bands
    ('SENIOR',       1,       0.0000,   300000.0000,  0.00),
    ('SENIOR',       2,  300000.0000,   500000.0000,  5.00),
    ('SENIOR',       3,  500000.0000,  1000000.0000, 20.00),
    ('SENIOR',       4, 1000000.0000,  NULL,         30.00),
    -- SUPER_SENIOR: nil to 5,00,000 — per year, 3 bands
    ('SUPER_SENIOR', 1,       0.0000,   500000.0000,  0.00),
    ('SUPER_SENIOR', 2,  500000.0000,  1000000.0000, 20.00),
    ('SUPER_SENIOR', 3, 1000000.0000,  NULL,         30.00)
) AS v(age, ord, from_amount, to_amount, rate) ON m.age_category = v.age
WHERE m.regime = 'OLD' AND m.financial_year IN ('2023-2024', '2024-2025', '2025-2026');

INSERT INTO reference.tax_slab_master_history (master_id, version, change_reason, action_type, changed_by, valid_from)
SELECT m.id, 1, 'Senior and super-senior old-regime slabs (W-09.1)', 'INSERT', 'system', m.effective_from
FROM reference.tax_slab_master m
WHERE m.age_category IN ('SENIOR', 'SUPER_SENIOR');
