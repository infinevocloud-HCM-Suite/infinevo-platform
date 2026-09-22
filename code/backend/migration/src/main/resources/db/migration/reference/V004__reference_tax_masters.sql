-- W-09: the eleven statutory tax master tables.
--
-- These hold Indian central tax law — the Income Tax Act 1961 as amended by each year's
-- Finance Act. The law is the same for every customer, so none of these tables carries a
-- tenant_id and none has row-level security (D-08, CONVENTIONS.md Rule 7). The legacy
-- system got this wrong: TaxSlabDetailHistory.java:25 carried an organizationId, so a
-- Union Budget slab change meant a manual multi-row update per organisation, with nothing
-- to stop the copies diverging. Genuine per-tenant variation — professional tax, company
-- allowance rules — belongs in the payroll schema, which is tenant-scoped; W-33 reads the
-- baseline from here and applies those overrides on top.
--
-- Money is NUMERIC(19,4) and rates are NUMERIC(5,2) throughout (CONVENTIONS.md §2). No
-- float or double appears in this file, and the CI gate refuses one.

-- ─────────────────────────────────────────────────────────────────────────────
-- Income tax slabs
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE reference.tax_slab_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regime VARCHAR(10) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    financial_year VARCHAR(10) NOT NULL,
    assessment_year VARCHAR(10) NOT NULL,
    age_category VARCHAR(20) NOT NULL DEFAULT 'GENERAL' CHECK (age_category IN ('GENERAL', 'SENIOR', 'SUPER_SENIOR')),
    effective_from DATE NOT NULL,
    effective_to DATE,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tax_slab_master UNIQUE (regime, financial_year, age_category)
);

CREATE INDEX idx_tax_slab_master_fy ON reference.tax_slab_master(financial_year, regime);

CREATE TABLE reference.tax_slab_master_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    master_id UUID NOT NULL REFERENCES reference.tax_slab_master(id) ON DELETE CASCADE,
    version INT NOT NULL,
    change_reason VARCHAR(255),
    action_type VARCHAR(20) NOT NULL DEFAULT 'INSERT',
    changed_by VARCHAR(100) NOT NULL DEFAULT 'system',
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tax_slab_master_hist_master ON reference.tax_slab_master_history(master_id);

-- The rate brackets themselves. to_amount NULL is the open-ended top band; the legacy
-- code carried a sentinel amount instead, which the calculator had to know about.
CREATE TABLE reference.tax_slab_detail_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slab_master_id UUID NOT NULL REFERENCES reference.tax_slab_master(id) ON DELETE CASCADE,
    from_amount NUMERIC(19,4) NOT NULL,
    to_amount NUMERIC(19,4),
    tax_rate_percent NUMERIC(5,2) NOT NULL CHECK (tax_rate_percent >= 0 AND tax_rate_percent <= 100),
    slab_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tax_slab_detail_order UNIQUE (slab_master_id, slab_order)
);

CREATE INDEX idx_tax_slab_detail_master ON reference.tax_slab_detail_history(slab_master_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Exemption and deduction rules
-- ─────────────────────────────────────────────────────────────────────────────

-- Section 10(13A) house rent allowance.
CREATE TABLE reference.hra_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    tax_regime VARCHAR(10) NOT NULL DEFAULT 'OLD' CHECK (tax_regime IN ('OLD', 'NEW')),
    metro_percent NUMERIC(5,2) NOT NULL DEFAULT 50.00,
    non_metro_percent NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    basic_da_percent_threshold NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    pan_mandatory_threshold NUMERIC(19,4) NOT NULL DEFAULT 100000.0000,
    is_month_wise_calculation BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hra_rule_fy UNIQUE (financial_year, tax_regime)
);

-- Section 24(b), 80EE and 80EEA home loan interest and principal.
CREATE TABLE reference.home_loan_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    section_code VARCHAR(20) NOT NULL,
    section_name VARCHAR(100) NOT NULL,
    component VARCHAR(20) NOT NULL CHECK (component IN ('PRINCIPAL', 'INTEREST')),
    property_type VARCHAR(20) NOT NULL DEFAULT 'SELF_OCCUPIED' CHECK (property_type IN ('SELF_OCCUPIED', 'LET_OUT', 'BOTH')),
    max_limit NUMERIC(19,4),
    loan_sanction_from DATE,
    loan_sanction_to DATE,
    is_first_time_buyer BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(255),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_home_loan_rule_sec_fy UNIQUE (financial_year, section_code, component, property_type)
);

-- Section 24(a) standard deduction on let-out property, and the set-off cap.
CREATE TABLE reference.let_out_property_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    tax_regime VARCHAR(10) NOT NULL DEFAULT 'OLD' CHECK (tax_regime IN ('OLD', 'NEW')),
    standard_deduction_percent NUMERIC(5,2) NOT NULL DEFAULT 30.00,
    max_loss_setoff_limit NUMERIC(19,4) NOT NULL DEFAULT 200000.0000,
    is_home_loan_interest_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    is_loss_carry_forward_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_let_out_prop_fy UNIQUE (financial_year, tax_regime)
);

-- Sections 80TTA and 80TTB savings interest.
CREATE TABLE reference.other_income_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    section_code VARCHAR(20) NOT NULL,
    section_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL CHECK (rule_type IN ('DEDUCTION', 'INCOME')),
    tax_regime VARCHAR(10) NOT NULL DEFAULT 'BOTH' CHECK (tax_regime IN ('OLD', 'NEW', 'BOTH')),
    max_limit NUMERIC(19,4),
    deduction_percent NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    is_proof_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_conditional BOOLEAN NOT NULL DEFAULT FALSE,
    is_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_other_income_rule_sec_fy UNIQUE (financial_year, section_code, rule_type)
);

CREATE TABLE reference.standard_deduction_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    regime VARCHAR(10) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    amount NUMERIC(19,4) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_std_deduction_fy_regime UNIQUE (financial_year, regime)
);

-- Chapter VI-A heads. Not scoped by financial year: the section codes and their ceilings
-- have been stable across the three years seeded, and a year that changes one gets a new
-- row rather than a rewrite. category_group_code carries the shared umbrella limit —
-- 80C, 80CCC and 80CCD(1) share one 1,50,000 ceiling between them.
CREATE TABLE reference.section6a_item_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_code VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    max_limit NUMERIC(19,4),
    category_group_code VARCHAR(20),
    is_80c BOOLEAN NOT NULL DEFAULT FALSE,
    is_80d BOOLEAN NOT NULL DEFAULT FALSE,
    is_other_section BOOLEAN NOT NULL DEFAULT FALSE,
    is_allowed_in_new_regime BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_section6a_code UNIQUE (section_code)
);

CREATE INDEX idx_section6a_group ON reference.section6a_item_master(category_group_code);

CREATE TABLE reference.section87a_rebate_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    regime VARCHAR(10) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    income_threshold NUMERIC(19,4) NOT NULL,
    max_rebate_amount NUMERIC(19,4) NOT NULL,
    is_full_rebate BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(255),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sec87a_fy_regime UNIQUE (financial_year, regime)
);

-- Health and education cess, and the high-income surcharge bands.
CREATE TABLE reference.cess_surcharge_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    rule_type VARCHAR(20) NOT NULL DEFAULT 'CESS' CHECK (rule_type IN ('CESS', 'SURCHARGE')),
    tax_regime VARCHAR(10) NOT NULL DEFAULT 'BOTH' CHECK (tax_regime IN ('OLD', 'NEW', 'BOTH')),
    income_from NUMERIC(19,4),
    income_to NUMERIC(19,4),
    rate NUMERIC(5,2) NOT NULL,
    is_marginal_relief_applicable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(255),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cess_surcharge_fy ON reference.cess_surcharge_rule_master(financial_year, rule_type, tax_regime);
