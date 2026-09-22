-- W-09: generic lookup tables — country, state, currency, bank.
--
-- No tenant_id and no row-level security anywhere in this file, deliberately (D-08,
-- CONVENTIONS.md Rule 7). ISO country codes and RBI bank identifiers do not vary by
-- customer, so a tenant column here would duplicate national data per tenant and let the
-- copies drift. The one schema in the platform that is exempt is this one; if a future
-- table in `reference` looks like it needs a tenant column, it belongs in core, hrms or
-- payroll instead. app_user holds SELECT and nothing else here (infra/postgres/03-grants.sql).

-- ─────────────────────────────────────────────────────────────────────────────
-- Country — ISO 3166-1
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE reference.country (
    code CHAR(2) PRIMARY KEY,
    alpha3 CHAR(3) NOT NULL UNIQUE,
    numeric_code CHAR(3) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    dial_code VARCHAR(10) NOT NULL,
    default_currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_country_name ON reference.country(name);

-- ─────────────────────────────────────────────────────────────────────────────
-- State — Indian states and union territories, with GST state codes
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE reference.state (
    code VARCHAR(10) PRIMARY KEY,
    gst_state_code CHAR(2) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    country_code CHAR(2) NOT NULL REFERENCES reference.country(code),
    is_union_territory BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_state_country ON reference.state(country_code);
CREATE INDEX idx_state_name ON reference.state(name);

-- ─────────────────────────────────────────────────────────────────────────────
-- Currency — ISO 4217
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE reference.currency (
    code CHAR(3) PRIMARY KEY,
    numeric_code CHAR(3) NOT NULL UNIQUE,
    symbol VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    decimal_places INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Bank — scheduled commercial banks and their IFSC prefixes
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE reference.bank (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    ifsc_prefix VARCHAR(10) NOT NULL,
    rbi_code VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bank_ifsc_prefix ON reference.bank(ifsc_prefix);
CREATE INDEX idx_bank_name ON reference.bank(name);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed — currency first, then country, then state: each references the one above.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.currency (code, numeric_code, symbol, name, decimal_places) VALUES
    ('INR', '356', '₹',   'Indian Rupee',        2),
    ('USD', '840', '$',   'US Dollar',           2),
    ('EUR', '978', '€',   'Euro',                2),
    ('GBP', '826', '£',   'Pound Sterling',      2),
    ('AED', '784', 'د.إ', 'UAE Dirham',          2),
    ('SGD', '702', 'S$',  'Singapore Dollar',    2);

INSERT INTO reference.country (code, alpha3, numeric_code, name, dial_code, default_currency) VALUES
    ('IN', 'IND', '356', 'India',                    '+91',  'INR'),
    ('US', 'USA', '840', 'United States of America', '+1',   'USD'),
    ('GB', 'GBR', '826', 'United Kingdom',           '+44',  'GBP'),
    ('AE', 'ARE', '784', 'United Arab Emirates',     '+971', 'AED'),
    ('SG', 'SGP', '702', 'Singapore',                '+65',  'SGD');

-- 28 states and 8 union territories. gst_state_code is the two-digit code that opens
-- every GSTIN; it is the identifier statutory filings use, so it is unique here and the
-- abbreviation is only the primary key.
INSERT INTO reference.state (code, gst_state_code, name, country_code, is_union_territory) VALUES
    ('JK',    '01', 'Jammu and Kashmir',                          'IN', TRUE),
    ('HP',    '02', 'Himachal Pradesh',                           'IN', FALSE),
    ('PB',    '03', 'Punjab',                                     'IN', FALSE),
    ('CH',    '04', 'Chandigarh',                                 'IN', TRUE),
    ('UK',    '05', 'Uttarakhand',                                'IN', FALSE),
    ('HR',    '06', 'Haryana',                                    'IN', FALSE),
    ('DL',    '07', 'Delhi',                                      'IN', TRUE),
    ('RJ',    '08', 'Rajasthan',                                  'IN', FALSE),
    ('UP',    '09', 'Uttar Pradesh',                              'IN', FALSE),
    ('BR',    '10', 'Bihar',                                      'IN', FALSE),
    ('SK',    '11', 'Sikkim',                                     'IN', FALSE),
    ('AR',    '12', 'Arunachal Pradesh',                          'IN', FALSE),
    ('NL',    '13', 'Nagaland',                                   'IN', FALSE),
    ('MN',    '14', 'Manipur',                                    'IN', FALSE),
    ('MZ',    '15', 'Mizoram',                                    'IN', FALSE),
    ('TR',    '16', 'Tripura',                                    'IN', FALSE),
    ('ML',    '17', 'Meghalaya',                                  'IN', FALSE),
    ('AS',    '18', 'Assam',                                      'IN', FALSE),
    ('WB',    '19', 'West Bengal',                                'IN', FALSE),
    ('JH',    '20', 'Jharkhand',                                  'IN', FALSE),
    ('OD',    '21', 'Odisha',                                     'IN', FALSE),
    ('CG',    '22', 'Chhattisgarh',                               'IN', FALSE),
    ('MP',    '23', 'Madhya Pradesh',                             'IN', FALSE),
    ('GJ',    '24', 'Gujarat',                                    'IN', FALSE),
    ('DNHDD', '26', 'Dadra and Nagar Haveli and Daman and Diu',   'IN', TRUE),
    ('MH',    '27', 'Maharashtra',                                'IN', FALSE),
    ('KA',    '29', 'Karnataka',                                  'IN', FALSE),
    ('GA',    '30', 'Goa',                                        'IN', FALSE),
    ('LD',    '31', 'Lakshadweep',                                'IN', TRUE),
    ('KL',    '32', 'Kerala',                                     'IN', FALSE),
    ('TN',    '33', 'Tamil Nadu',                                 'IN', FALSE),
    ('PY',    '34', 'Puducherry',                                 'IN', TRUE),
    ('AN',    '35', 'Andaman and Nicobar Islands',                'IN', TRUE),
    ('TS',    '36', 'Telangana',                                  'IN', FALSE),
    ('AP',    '37', 'Andhra Pradesh',                             'IN', FALSE),
    ('LA',    '38', 'Ladakh',                                     'IN', TRUE);

INSERT INTO reference.bank (code, name, ifsc_prefix, rbi_code) VALUES
    ('SBI',      'State Bank of India',      'SBIN', 'SBI'),
    ('HDFC',     'HDFC Bank',                'HDFC', 'HDFC'),
    ('ICICI',    'ICICI Bank',               'ICIC', 'ICICI'),
    ('AXIS',     'Axis Bank',                'UTIB', 'AXIS'),
    ('PNB',      'Punjab National Bank',     'PUNB', 'PNB'),
    ('BOB',      'Bank of Baroda',           'BARB', 'BOB'),
    ('KOTAK',    'Kotak Mahindra Bank',      'KKBK', 'KOTAK'),
    ('CANARA',   'Canara Bank',              'CNRB', 'CANARA'),
    ('UNION',    'Union Bank of India',      'UBIN', 'UNION'),
    ('IDBI',     'IDBI Bank',                'IBKL', 'IDBI'),
    ('INDUSIND', 'IndusInd Bank',            'INDB', 'INDUSIND'),
    ('YES',      'YES Bank',                 'YESB', 'YES');
