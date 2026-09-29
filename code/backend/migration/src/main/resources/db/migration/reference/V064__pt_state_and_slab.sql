-- Migration: V064__pt_state_and_slab.sql
-- Description: W-31.2 reference.pt_state and reference.pt_slab — shared national PT base tables and statutory seed.
-- No tenant_id and no RLS: shared national data in reference schema.

CREATE TABLE reference.pt_state (
    state_code VARCHAR(10) PRIMARY KEY REFERENCES reference.state(code),
    levies_pt BOOLEAN NOT NULL,
    annual_ceiling NUMERIC(19,4),
    notes VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reference.pt_slab (
    id BIGSERIAL PRIMARY KEY,
    state_code VARCHAR(10) NOT NULL REFERENCES reference.pt_state(state_code),
    effective_from DATE NOT NULL,
    effective_to DATE,
    from_amount NUMERIC(19,4) NOT NULL,
    to_amount NUMERIC(19,4),
    amount NUMERIC(19,4) NOT NULL,
    is_female_exempt BOOLEAN NOT NULL DEFAULT false,
    deduction_months VARCHAR(32),
    sort_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pt_slab_state_effective ON reference.pt_slab(state_code, effective_from, sort_order);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed reference.pt_state for all rows in reference.state
-- 21 states levy professional tax (ProfessionalTaxUtil.java:9-14); 15 do not.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.pt_state (state_code, levies_pt, annual_ceiling, notes)
SELECT
    s.code,
    CASE WHEN s.code IN ('AP', 'AS', 'BR', 'CG', 'GJ', 'JH', 'KA', 'KL', 'MP', 'MH', 'MN', 'MZ', 'NL', 'OD', 'PB', 'PY', 'SK', 'TN', 'TS', 'TR', 'WB')
         THEN true ELSE false END,
    CASE WHEN s.code IN ('AP', 'AS', 'BR', 'CG', 'GJ', 'JH', 'KA', 'KL', 'MP', 'MH', 'MN', 'MZ', 'NL', 'OD', 'PB', 'PY', 'SK', 'TN', 'TS', 'TR', 'WB')
         THEN 2500.0000 ELSE NULL END,
    CASE WHEN s.code IN ('AP', 'AS', 'BR', 'CG', 'GJ', 'JH', 'KA', 'KL', 'MP', 'MH', 'MN', 'MZ', 'NL', 'OD', 'PB', 'PY', 'SK', 'TN', 'TS', 'TR', 'WB')
         THEN 'Levies professional tax under state statute'
         ELSE 'No professional tax levied' END
FROM reference.state s;

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed statutory slabs for the 21 states, effective 2024-04-01
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.pt_slab
    (state_code, effective_from, effective_to, from_amount, to_amount, amount, is_female_exempt, deduction_months, sort_order)
VALUES
    -- Andhra Pradesh (AP) — Andhra Pradesh Tax on Professions, Trades, Callings and Employments Act, 1987
    ('AP', DATE '2024-04-01', NULL, 0.0000, 15000.0000, 0.0000, false, NULL, 1),
    ('AP', DATE '2024-04-01', NULL, 15000.0000, 20000.0000, 150.0000, false, NULL, 2),
    ('AP', DATE '2024-04-01', NULL, 20000.0000, NULL, 200.0000, false, NULL, 3),

    -- Assam (AS) — Assam Professions, Trades, Callings and Employments Taxation Act, 1947
    ('AS', DATE '2024-04-01', NULL, 0.0000, 10000.0000, 0.0000, false, NULL, 1),
    ('AS', DATE '2024-04-01', NULL, 10000.0000, 15000.0000, 150.0000, false, NULL, 2),
    ('AS', DATE '2024-04-01', NULL, 15000.0000, 25000.0000, 180.0000, false, NULL, 3),
    ('AS', DATE '2024-04-01', NULL, 25000.0000, NULL, 208.0000, false, NULL, 4),

    -- Bihar (BR) — Bihar State Tax on Professions, Trades, Callings and Employments Act, 2011
    ('BR', DATE '2024-04-01', NULL, 0.0000, 25000.0000, 0.0000, false, NULL, 1),
    ('BR', DATE '2024-04-01', NULL, 25000.0000, 41666.0000, 83.3300, false, NULL, 2),
    ('BR', DATE '2024-04-01', NULL, 41666.0000, 83333.0000, 166.6600, false, NULL, 3),
    ('BR', DATE '2024-04-01', NULL, 83333.0000, NULL, 208.3300, false, NULL, 4),

    -- Chhattisgarh (CG) — Chhattisgarh State Tax on Professions, Trades, Callings and Employments Act, 1995
    ('CG', DATE '2024-04-01', NULL, 0.0000, 25000.0000, 0.0000, false, NULL, 1),
    ('CG', DATE '2024-04-01', NULL, 25000.0000, 33333.0000, 150.0000, false, NULL, 2),
    ('CG', DATE '2024-04-01', NULL, 33333.0000, NULL, 200.0000, false, NULL, 3),

    -- Gujarat (GJ) — Gujarat Panchayats, Municipalities and State Tax on Professions Act, 1976
    ('GJ', DATE '2024-04-01', NULL, 0.0000, 12000.0000, 0.0000, false, NULL, 1),
    ('GJ', DATE '2024-04-01', NULL, 12000.0000, NULL, 200.0000, false, NULL, 2),

    -- Jharkhand (JH) — Jharkhand Tax on Professions, Trades, Callings and Employments Act, 2011
    ('JH', DATE '2024-04-01', NULL, 0.0000, 25000.0000, 0.0000, false, NULL, 1),
    ('JH', DATE '2024-04-01', NULL, 25000.0000, 41666.0000, 100.0000, false, NULL, 2),
    ('JH', DATE '2024-04-01', NULL, 41666.0000, 66666.0000, 150.0000, false, NULL, 3),
    ('JH', DATE '2024-04-01', NULL, 66666.0000, 83333.0000, 175.0000, false, NULL, 4),
    ('JH', DATE '2024-04-01', NULL, 83333.0000, NULL, 208.3300, false, NULL, 5),

    -- Karnataka (KA) — Karnataka Tax on Professions, Trades, Callings and Employments Act, 1976 (amended w.e.f. 2023)
    ('KA', DATE '2024-04-01', NULL, 0.0000, 24999.0000, 0.0000, false, NULL, 1),
    ('KA', DATE '2024-04-01', NULL, 24999.0000, NULL, 200.0000, false, NULL, 2),

    -- Kerala (KL) — Kerala Municipality Act, 1994, Section 245 (Half-yearly, Months 3 and 9; monthly wage equivalent)
    ('KL', DATE '2024-04-01', NULL, 0.0000, 2000.0000, 0.0000, false, '3,9', 1),
    ('KL', DATE '2024-04-01', NULL, 2000.0000, 3000.0000, 120.0000, false, '3,9', 2),
    ('KL', DATE '2024-04-01', NULL, 3000.0000, 5000.0000, 180.0000, false, '3,9', 3),
    ('KL', DATE '2024-04-01', NULL, 5000.0000, 7500.0000, 300.0000, false, '3,9', 4),
    ('KL', DATE '2024-04-01', NULL, 7500.0000, 10000.0000, 450.0000, false, '3,9', 5),
    ('KL', DATE '2024-04-01', NULL, 10000.0000, 12500.0000, 600.0000, false, '3,9', 6),
    ('KL', DATE '2024-04-01', NULL, 12500.0000, 16667.0000, 750.0000, false, '3,9', 7),
    ('KL', DATE '2024-04-01', NULL, 16667.0000, 20833.0000, 1000.0000, false, '3,9', 8),
    ('KL', DATE '2024-04-01', NULL, 20833.0000, NULL, 1250.0000, false, '3,9', 9),

    -- Madhya Pradesh (MP) — Madhya Pradesh Vritti Kar Adhiniyam, 1995
    ('MP', DATE '2024-04-01', NULL, 0.0000, 18750.0000, 0.0000, false, NULL, 1),
    ('MP', DATE '2024-04-01', NULL, 18750.0000, 25000.0000, 125.0000, false, NULL, 2),
    ('MP', DATE '2024-04-01', NULL, 25000.0000, 33333.0000, 167.0000, false, NULL, 3),
    ('MP', DATE '2024-04-01', NULL, 33333.0000, NULL, 208.0000, false, '1,3,4,5,6,7,8,9,10,11,12', 4),
    ('MP', DATE '2024-04-01', NULL, 33333.0000, NULL, 212.0000, false, '2', 5),

    -- Maharashtra (MH) — Maharashtra State Tax on Professions, Trades, Callings and Employments Act, 1975
    ('MH', DATE '2024-04-01', NULL, 0.0000, 7500.0000, 0.0000, false, NULL, 1),
    ('MH', DATE '2024-04-01', NULL, 7500.0000, 10000.0000, 175.0000, true, NULL, 2),
    ('MH', DATE '2024-04-01', NULL, 10000.0000, 25000.0000, 200.0000, true, '1,3,4,5,6,7,8,9,10,11,12', 3),
    ('MH', DATE '2024-04-01', NULL, 10000.0000, 25000.0000, 300.0000, true, '2', 4),
    ('MH', DATE '2024-04-01', NULL, 25000.0000, NULL, 200.0000, false, '1,3,4,5,6,7,8,9,10,11,12', 5),
    ('MH', DATE '2024-04-01', NULL, 25000.0000, NULL, 300.0000, false, '2', 6),

    -- Manipur (MN) — Manipur Professions, Trades, Callings and Employments Taxation Act, 1981
    ('MN', DATE '2024-04-01', NULL, 0.0000, 4166.0000, 0.0000, false, NULL, 1),
    ('MN', DATE '2024-04-01', NULL, 4166.0000, 6250.0000, 100.0000, false, NULL, 2),
    ('MN', DATE '2024-04-01', NULL, 6250.0000, 8333.0000, 166.0000, false, NULL, 3),
    ('MN', DATE '2024-04-01', NULL, 8333.0000, 10416.0000, 200.0000, false, NULL, 4),
    ('MN', DATE '2024-04-01', NULL, 10416.0000, NULL, 208.0000, false, NULL, 5),

    -- Mizoram (MZ) — Mizoram Professions, Trades, Callings and Employments Taxation Act, 1995
    ('MZ', DATE '2024-04-01', NULL, 0.0000, 5000.0000, 0.0000, false, NULL, 1),
    ('MZ', DATE '2024-04-01', NULL, 5000.0000, 8000.0000, 75.0000, false, NULL, 2),
    ('MZ', DATE '2024-04-01', NULL, 8000.0000, 10000.0000, 120.0000, false, NULL, 3),
    ('MZ', DATE '2024-04-01', NULL, 10000.0000, 12000.0000, 150.0000, false, NULL, 4),
    ('MZ', DATE '2024-04-01', NULL, 12000.0000, 15000.0000, 180.0000, false, NULL, 5),
    ('MZ', DATE '2024-04-01', NULL, 15000.0000, 20000.0000, 195.0000, false, NULL, 6),
    ('MZ', DATE '2024-04-01', NULL, 20000.0000, NULL, 208.0000, false, NULL, 7),

    -- Nagaland (NL) — Nagaland Professions, Trades, Callings and Employments Taxation Act, 1968
    ('NL', DATE '2024-04-01', NULL, 0.0000, 4000.0000, 0.0000, false, NULL, 1),
    ('NL', DATE '2024-04-01', NULL, 4000.0000, 7000.0000, 35.0000, false, NULL, 2),
    ('NL', DATE '2024-04-01', NULL, 7000.0000, 9000.0000, 75.0000, false, NULL, 3),
    ('NL', DATE '2024-04-01', NULL, 9000.0000, 12000.0000, 110.0000, false, NULL, 4),
    ('NL', DATE '2024-04-01', NULL, 12000.0000, NULL, 208.0000, false, NULL, 5),

    -- Odisha (OD) — Odisha State Tax on Professions, Trades, Callings and Employments Act, 2000
    ('OD', DATE '2024-04-01', NULL, 0.0000, 13333.0000, 0.0000, false, NULL, 1),
    ('OD', DATE '2024-04-01', NULL, 13333.0000, 25000.0000, 125.0000, false, NULL, 2),
    ('OD', DATE '2024-04-01', NULL, 25000.0000, NULL, 200.0000, false, '1,2,3,4,5,6,7,8,9,10,11', 3),
    ('OD', DATE '2024-04-01', NULL, 25000.0000, NULL, 300.0000, false, '12', 4),

    -- Punjab (PB) — Punjab State Development Tax Act, 2018
    ('PB', DATE '2024-04-01', NULL, 0.0000, 20833.0000, 0.0000, false, NULL, 1),
    ('PB', DATE '2024-04-01', NULL, 20833.0000, NULL, 200.0000, false, NULL, 2),

    -- Puducherry (PY) — Puducherry Municipalities Act, 1973 (Half-yearly, Months 3 and 9; monthly wage equivalent)
    ('PY', DATE '2024-04-01', NULL, 0.0000, 16667.0000, 0.0000, false, '3,9', 1),
    ('PY', DATE '2024-04-01', NULL, 16667.0000, 33333.0000, 250.0000, false, '3,9', 2),
    ('PY', DATE '2024-04-01', NULL, 33333.0000, 50000.0000, 500.0000, false, '3,9', 3),
    ('PY', DATE '2024-04-01', NULL, 50000.0000, 66667.0000, 750.0000, false, '3,9', 4),
    ('PY', DATE '2024-04-01', NULL, 66667.0000, 83333.0000, 1000.0000, false, '3,9', 5),
    ('PY', DATE '2024-04-01', NULL, 83333.0000, NULL, 1250.0000, false, '3,9', 6),

    -- Sikkim (SK) — Sikkim Tax on Professions, Trades, Callings and Employments Act, 2006
    ('SK', DATE '2024-04-01', NULL, 0.0000, 20000.0000, 0.0000, false, NULL, 1),
    ('SK', DATE '2024-04-01', NULL, 20000.0000, 30000.0000, 125.0000, false, NULL, 2),
    ('SK', DATE '2024-04-01', NULL, 30000.0000, 40000.0000, 150.0000, false, NULL, 3),
    ('SK', DATE '2024-04-01', NULL, 40000.0000, NULL, 200.0000, false, NULL, 4),

    -- Tamil Nadu (TN) — Tamil Nadu Municipal Laws Act, 1998 (Half-yearly, Months 3 and 9; monthly wage equivalent)
    ('TN', DATE '2024-04-01', NULL, 0.0000, 3500.0000, 0.0000, false, '3,9', 1),
    ('TN', DATE '2024-04-01', NULL, 3500.0000, 5000.0000, 180.0000, false, '3,9', 2),
    ('TN', DATE '2024-04-01', NULL, 5000.0000, 7500.0000, 425.0000, false, '3,9', 3),
    ('TN', DATE '2024-04-01', NULL, 7500.0000, 10000.0000, 930.0000, false, '3,9', 4),
    ('TN', DATE '2024-04-01', NULL, 10000.0000, 12500.0000, 1025.0000, false, '3,9', 5),
    ('TN', DATE '2024-04-01', NULL, 12500.0000, NULL, 1250.0000, false, '3,9', 6),

    -- Telangana (TS) — Telangana Tax on Professions, Trades, Callings and Employments Act, 1987
    ('TS', DATE '2024-04-01', NULL, 0.0000, 15000.0000, 0.0000, false, NULL, 1),
    ('TS', DATE '2024-04-01', NULL, 15000.0000, 20000.0000, 150.0000, false, NULL, 2),
    ('TS', DATE '2024-04-01', NULL, 20000.0000, NULL, 200.0000, false, NULL, 3),

    -- Tripura (TR) — Tripura Professions, Trades, Callings and Employments Taxation Act, 1997
    ('TR', DATE '2024-04-01', NULL, 0.0000, 7500.0000, 0.0000, false, NULL, 1),
    ('TR', DATE '2024-04-01', NULL, 7500.0000, 15000.0000, 150.0000, false, NULL, 2),
    ('TR', DATE '2024-04-01', NULL, 15000.0000, NULL, 208.0000, false, NULL, 3),

    -- West Bengal (WB) — West Bengal State Tax on Professions, Trades, Callings and Employments Act, 1979
    ('WB', DATE '2024-04-01', NULL, 0.0000, 10000.0000, 0.0000, false, NULL, 1),
    ('WB', DATE '2024-04-01', NULL, 10000.0000, 15000.0000, 110.0000, false, NULL, 2),
    ('WB', DATE '2024-04-01', NULL, 15000.0000, 25000.0000, 130.0000, false, NULL, 3),
    ('WB', DATE '2024-04-01', NULL, 25000.0000, 40000.0000, 150.0000, false, NULL, 4),
    ('WB', DATE '2024-04-01', NULL, 40000.0000, NULL, 200.0000, false, NULL, 5);
