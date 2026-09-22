-- Test fixture, not a shipped migration. Lives under src/test/resources and is applied
-- only by ReferenceSchemaIT#annualUpdateSimulation_oneMigrationAppliesCleanly.
--
-- It is the annual update runbook (W-09 spec §4) carried out rather than described: when a
-- Union Budget changes the slabs, one new versioned script in db/migration/reference/
-- inserts the new financial year, and every tenant reads the new law the moment it is
-- applied — no per-tenant batch, no downtime, nothing else touched.
--
-- The rates are deliberately not real. A future Finance Act has not been written, and
-- inventing plausible-looking numbers in a file that sits beside the genuine seed is how
-- a fixture ends up cited as law. Three round bands, obviously synthetic.
--
-- V099 sits far above the shipped sequence so that a real migration added later cannot
-- collide with it.

INSERT INTO reference.tax_slab_master
    (regime, financial_year, assessment_year, age_category, effective_from, description)
VALUES ('NEW', '2026-2027', '2027-2028', 'GENERAL', DATE '2026-04-01', 'Synthetic fixture — not statutory');

INSERT INTO reference.tax_slab_detail_history (slab_master_id, from_amount, to_amount, tax_rate_percent, slab_order)
SELECT m.id, v.from_amount, v.to_amount, v.rate, v.ord
FROM reference.tax_slab_master m
JOIN (VALUES
    (1,       0.0000,  500000.0000,  0.00),
    (2,  500000.0000, 1000000.0000, 10.00),
    (3, 1000000.0000, NULL,         30.00)
) AS v(ord, from_amount, to_amount, rate) ON TRUE
WHERE m.financial_year = '2026-2027' AND m.regime = 'NEW';
