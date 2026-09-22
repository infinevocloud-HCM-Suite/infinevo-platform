# W-09 — Reference schema and seed

| Field | Value |
|---|---|
| **Work item** | `W-09` · issue [#10](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/10) |
| **Kind** | Port — Reference data, tax master tables, seed scripts & annual update process |
| **Stream / track** | Stream B — Data foundation · Track P |
| **Wave** | 2 — Data platform |
| **Size / skill** | M · DATA |
| **Owner** | unassigned |
| **Blocked by** | `W-06` #7 (merged 2026-09-18) |
| **Blocks** | `W-31` Statutory components · `W-32` Income tax declaration · `W-33` Tax calculator · `W-36` TDS & payslips |
| **Capabilities** | `CORE-15` Reference data · `PAY-10` Tax calculator |
| **Decisions** | `D-08` reference schema (shared base, per-tenant override) · `D-09` Postgres with Flyway · `D-38` Java 21 · `D-45` migration schema · `D-46` ddl-auto absent · `CONVENTIONS.md` Rule 7 (reference schema exemption) · `02-data-model.md:357-364` |
| **Gaps addressed** | `DEBT-002` (ddl-auto elimination), `DEBT-018` (proper indexing), elimination of multi-tenant statutory data duplication |
| **Status** | **Approved** |
| **Approved by** | Sanjib (founder) |
| **Approved on** | 2026-09-20 |

> Hard rule 1: No code is written until this spec is approved by the founder.

---

## 1. Problem

In the legacy monolithic architecture (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/EmployeeITDeclaration/` and `statutorycomponents/`), statutory tax rules, income tax slabs, standard deduction amounts, HRA exemption criteria, Section 80C/Chapter VI-A deduction ceilings, Section 87A rebate thresholds, cess and surcharge rates, and generic lookups (countries, states, currencies, banks) suffered from fundamental architectural defects:
1. **Multi-Tenant Duplication of National Law**: Statutory tax parameters are governed by Indian central tax legislation (Income Tax Act, 1961 and annual Finance Acts) and are universally identical for every organization. In the legacy codebase, tables like `TaxSlabDetailHistory` carried `organizationId` (`TaxSlabDetailHistory.java:25`), duplicating copies of national tax rules per tenant and requiring risky multi-row manual updates whenever the Union Budget revised tax slabs.
2. **Inconsistent Generic Lookups**: Countries, Indian states (and GST state codes), banking institutions (with RBI/IFSC codes), and ISO currencies lacked a single, standardized, read-only system authority, causing validation errors and typos across HRMS and Payroll entities.
3. **No Governed Annual Tax Update Process**: Changing tax slabs required touching operational tables or hardcoding values in application business logic, rather than executing a single, versioned, auditable database migration.
4. **Current Repository State**: `W-05` created the four platform schemas (`core`, `hrms`, `payroll`, `reference`) owned by `migration_user`. `W-06` established the Flyway migration framework, and `W-07`/`W-08` established tenant isolation for tenant-scoped schemas. However, the `reference` schema currently contains **zero production tables and zero seed data**.

---

## 2. Scope

### In scope

1. **Generic Lookup Tables (`CORE-15` - 4 Tables in `reference` Schema)**:
   - `reference.country`: ISO 3166-1 alpha-2, alpha-3, numeric code, country name, international dialing code, default currency code.
   - `reference.state`: Indian States and Union Territories with official state name, 2-digit GST state code, state code abbreviation, and country reference.
   - `reference.bank`: Standard banking institutions in India, bank code, RBI bank identifier, and canonical bank name.
   - `reference.currency`: ISO 4217 standard currency codes (code, numeric code, symbol, minor unit decimal places, name).

2. **Tax Master Tables (`PAY-10` - 11 Tables in `reference` Schema)**:
   - `reference.tax_slab_master`: Income tax slab header defining financial year (e.g. `2023-2024`, `2024-2025`, `2025-2026`), assessment year, tax regime (`OLD`, `NEW`), age category (`GENERAL`, `SENIOR`, `SUPER_SENIOR`), and effective date ranges.
   - `reference.tax_slab_master_history`: Historical version tracking and audit metadata for tax slab master configurations.
   - `reference.tax_slab_detail_history`: Detailed income tax brackets linked to slab master: `from_amount`, `to_amount`, and `tax_rate_percent` using financial precision `NUMERIC(19,4)` and `NUMERIC(5,2)`.
   - `reference.hra_rule_master`: Statutory House Rent Allowance (HRA) exemption rules (Section 10(13A)) defining metro allowance percentage (50%), non-metro percentage (40%), basic salary DA threshold (10%), PAN mandatory threshold (₹1,00,000), month-wise calculation flag, and active status per financial year.
   - `reference.home_loan_rule_master`: Section 24(b) interest deduction caps for self-occupied properties (e.g., ₹2,00,000), Section 80EE/80EEA additional interest deduction rules per financial year.
   - `reference.let_out_property_rule_master`: Statutory municipal taxes deduction, standard deduction rate (30% under Section 24(a)) on net annual value, home loan interest allowance flag, and maximum loss set-off cap (₹2,00,000) for let-out properties.
   - `reference.other_income_rule_master`: Section 80TTA (₹10,000 limit) and Section 80TTB (₹50,000 limit for senior citizens) savings interest deduction rules.
   - `reference.standard_deduction_rule_master`: Standard deduction limits per regime and financial year.
   - `reference.section6a_item_master`: Statutory Chapter VI-A investment deduction heads (80C, 80CCC, 80CCD(1), 80CCD(1B), 80CCD(2), 80D, 80DD, 80DDB, 80E, 80G, 80GGA, 80GGC, 80U) with individual deduction caps, category group codes, section discriminator flags (`is_80c`, `is_80d`, `is_other_section`), and eligible regime mappings.
   - `reference.section87a_rebate_rule_master`: Section 87A tax rebate limits and maximum taxable income thresholds per regime and financial year.
   - `reference.cess_surcharge_rule_master`: Health & Education Cess rate (4.00%), high-income surcharge slab thresholds (₹50L, ₹1Cr, ₹2Cr, ₹5Cr), marginal relief computation flags, rule types (`CESS`, `SURCHARGE`), and maximum surcharge caps under the New Tax Regime (25%).

3. **Multi-Year Statutory Values (FY 2023-24, FY 2024-25, FY 2025-26)**:
   - **FY 2023-2024 (AY 2024-2025)**:
     * *Old Regime Slabs*: 0 to ₹2.5L: 0%; ₹2.5L to ₹5L: 5%; ₹5L to ₹10L: 20%; >₹10L: 30%.
     * *New Regime Slabs*: 0 to ₹3L: 0%; ₹3L to ₹6L: 5%; ₹6L to ₹9L: 10%; ₹9L to ₹12L: 15%; ₹12L to ₹15L: 20%; >₹15L: 30%.
     * *Standard Deduction*: ₹50,000 for both Old and New Regimes.
     * *Section 87A Rebate*: Old Regime: ₹12,500 up to ₹5,00,000 taxable income; New Regime: ₹25,000 up to ₹7,00,000 taxable income.
   - **FY 2024-2025 (AY 2025-2026 - Finance (No. 2) Act 2024)**:
     * *Old Regime Slabs*: 0 to ₹2.5L: 0%; ₹2.5L to ₹5L: 5%; ₹5L to ₹10L: 20%; >₹10L: 30%.
     * *New Regime Slabs*: 0 to ₹3L: 0%; ₹3L to ₹7L: 5%; ₹7L to ₹10L: 10%; ₹10L to ₹12L: 15%; ₹12L to ₹15L: 20%; >₹15L: 30%.
     * *Standard Deduction*: ₹50,000 (Old Regime); ₹75,000 (New Regime).
     * *Section 87A Rebate*: Old Regime: ₹12,500 up to ₹5,00,000 taxable income; New Regime: ₹25,000 up to ₹7,00,000 taxable income.
   - **FY 2025-2026 (AY 2026-2027 - Union Budget February 2025)**:
     * *Old Regime Slabs*: 0 to ₹2.5L: 0%; ₹2.5L to ₹5L: 5%; ₹5L to ₹10L: 20%; >₹10L: 30%.
     * *New Regime Slabs*:
       - ₹0 to ₹4,00,000: NIL (0%)
       - ₹4,00,001 to ₹8,00,000: 5%
       - ₹8,00,001 to ₹12,00,000: 10%
       - ₹12,00,001 to ₹16,00,000: 15%
       - ₹16,00,001 to ₹20,00,000: 20%
       - ₹20,00,001 to ₹24,00,000: 25%
       - Above ₹24,00,000: 30%
     * *Standard Deduction*: ₹50,000 (Old Regime); ₹75,000 (New Regime).
     * *Section 87A Rebate*: Old Regime: ₹12,500 up to ₹5,00,000 taxable income; **New Regime: ₹60,000 up to ₹12,00,000 taxable income**.

4. **Flyway Migration Versioning Sequence Across All Schemas (`D-09`, `W-06`)**:
   - Version numbers in Flyway are globally unified across all location folders (`db/migration/{reference,core,hrms,payroll}`).
   - Existing/prior versions:
     * `core/V001__tenant.sql` (`W-07`)
     * `core/V002__user_tenant.sql` (`W-08`)
   - W-09 uses the next available global version numbers:
     * `reference/V003__reference_lookups.sql`
     * `reference/V004__reference_tax_masters.sql`
     * `reference/V005__reference_tax_seed.sql`

5. **Exemption from `tenant_id` and RLS (`D-08`, `CONVENTIONS.md` Rule 7)**:
   - Explicit architectural and security justification for why all 15 tables in `reference` schema **deliberately carry no `tenant_id` column** and **have no Row Level Security policies**.

### Out of scope

- Employee-facing investment declaration entry, proof upload, and approval workflows (`W-32` Income tax declaration).
- Tax calculation execution engine and payrun tax computation algorithms (`W-33` Tax calculator).
- Per-tenant statutory overrides (such as tenant-specific professional tax rates, provident fund configurations, or custom allowances) (`W-31` Statutory components).
- UI CRUD administration screens for reference tables (all reference tables are immutable to application users and maintained exclusively via Flyway migrations).

---

## 3. Flow & Architecture

```
[Union Budget / Statutory Tax Changes]
                │
                ▼
[New Flyway Versioned Migration Script]
  (e.g., V00X__reference_tax_fy2026_2027.sql in db/migration/reference/)
                │
                ▼
[Migration Service (MigrationApplication / CI-CD)]
  (Runs as migration_user with full DDL/DML ownership on reference schema)
                │
                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        reference Schema (PostgreSQL)                   │
│                                                                        │
│  [Generic Lookups]                [Tax Masters (11 Tables)]            │
│  ├── country                      ├── tax_slab_master & history        │
│  ├── state                        ├── tax_slab_detail_history          │
│  ├── bank                         ├── hra_rule_master                  │
│  └── currency                     ├── home_loan_rule_master            │
│                                   ├── let_out_property_rule_master     │
│  * NO tenant_id                   ├── other_income_rule_master         │
│  * NO RLS                         ├── standard_deduction_rule_master   │
│  * Read-only to app_user          ├── section6a_item_master            │
│                                   ├── section87a_rebate_rule_master    │
│                                   └── cess_surcharge_rule_master       │
└────────────────────────────────────────────────────────────────────────┘
                │
                │ app_user (SELECT only)
                ▼
[Payroll Tax Engine (W-33)] ────────► Merges with per-tenant overrides
                                      (e.g., payroll.org_pt_override in W-31)
                                      ▼
                             [Calculates Accurate Payrun TDS & Payslips]
```

### Architectural Justification: The `reference` Schema Tenant Exemption (`D-08`)

1. **National Law Uniformity**: Central income tax regulations, ISO country definitions, and Reserve Bank of India IFSC mappings do not vary by tenant. Storing them per tenant wastes storage, complicates audits, and invites cross-tenant data drift.
2. **Seamless Zero-Downtime Annual Updates**: When statutory slabs change, a single migration script updates the shared `reference` tables. All tenants immediately calculate taxes against updated legislation for new financial years without requiring per-tenant batch jobs or downtime.
3. **Clean Override Model**: Genuine tenant variations (e.g. professional tax slab overrides, specific company deduction rules) are stored in tenant-isolated tables in the `payroll` schema (carrying `tenant_id` and enforced with RLS under `D-56`). The calculation engine (`W-33`) reads the shared statutory baseline from `reference` and applies tenant-specific overrides from `payroll`.
4. **Security & Immutability**: The runtime application role `app_user` is granted **`SELECT` only** on the `reference` schema. `INSERT`, `UPDATE`, `DELETE`, and `TRUNCATE` operations are strictly blocked by PostgreSQL permissions, and `DROP TABLE` is blocked by table ownership enforcement (`W-05`).

---

## 4. Backend Changes & Legacy Porting Analysis

### Legacy Entity Mapping and Field Disposition

Every target table in the `reference` schema maps to legacy Java entities in `legacy/Payroll-Bend-SBoot`. The table below provides full citations and explicit dispositions for ported, transformed, and dropped columns:

| Target Table | Legacy Entity & Citation | Ported / Transformed Fields | Dropped Legacy Fields & Rationale |
|---|---|---|---|
| `reference.tax_slab_master` | `TaxSlabMaster.java:7-74` (`EmployeeITDeclaration/taxCalculator`) | `financialYear`, `taxRegime`, `isActive`. Added `assessment_year`, `age_category`, `effective_from`, `effective_to`, `description`. | `slabJson` (JSON blob) dropped in favor of structured relational brackets in `tax_slab_detail_history`. |
| `reference.tax_slab_master_history` | `TaxSlabMasterHistory.java:18-75` (`EmployeeITDeclaration/taxCalculator`) | `taxSlabMasterId` -> `master_id` FK, `financialYear`, `taxRegime`, `actionType`, `changedBy`, `changedAt`. Added `version`, `valid_from`, `valid_to`, `change_reason`. | `oldSlabJson` & `newSlabJson` dropped in favor of structured historical slab rows. |
| `reference.tax_slab_detail_history` | `TaxSlabDetailHistory.java:10-111` (`statutorycomponents`) | `startAmount` -> `from_amount NUMERIC(19,4)`, `endAmount` -> `to_amount NUMERIC(19,4)`, `payAmount` -> `tax_rate_percent NUMERIC(5,2)`, `slab_order`. | `organizationId` dropped (`D-08` national law is identical for all tenants). `oldStartAmount`, `oldEndAmount`, `oldPayAmount` (in-row floating point diffs) dropped in favor of structured versioned rows. |
| `reference.hra_rule_master` | `HraRuleMaster.java:7-109` (`EmployeeITDeclaration/taxCalculator`) | `financialYear`, `taxRegime`, `metroPercentageOfBasic` -> `metro_percent`, `nonMetroPercentageOfBasic` -> `non_metro_percent`, `rentMinusBasicPercentage` -> `basic_da_percent_threshold` (10%), `panMandatoryThreshold` -> `pan_mandatory_threshold NUMERIC(19,4)` (₹1,00,000), `isMonthWiseCalculation` -> `is_month_wise_calculation`, `isActive`. | None dropped. All statutory rules ported with explicit `NUMERIC(19,4)` / `NUMERIC(5,2)` types. |
| `reference.home_loan_rule_master` | `HomeLoanRuleMaster.java:9-159` (`EmployeeITDeclaration/taxCalculator`) | `sectionCode`, `sectionName`, `component` (`PRINCIPAL`/`INTEREST`), `propertyType` (`SELF_OCCUPIED`/`LET_OUT`/`BOTH`), `maxLimit` -> `max_limit NUMERIC(19,4)`, `loanSanctionFrom`, `loanSanctionTo`, `isFirstTimeBuyer`, `isActive`, `remarks`. | `maxLimitFormatted` dropped (string presentation format belongs on frontend). |
| `reference.let_out_property_rule_master` | `LetOutPropertyRuleMaster.java:7-97` (`EmployeeITDeclaration/taxCalculator`) | `financialYear`, `taxRegime`, `standardDeductionPercentage` -> `standard_deduction_percent NUMERIC(5,2)` (30%), `maxLossSetOffAgainstSalary` -> `max_loss_setoff_limit NUMERIC(19,4)` (₹2,00,000), `isHomeLoanInterestAllowed`, `isLossCarryForwardAllowed`, `isActive`. | None dropped. |
| `reference.other_income_rule_master` | `OtherIncomeRuleMaster.java:7-143` (`EmployeeITDeclaration/taxCalculator`) | `sectionCode`, `sectionName`, `ruleType`, `taxRegime`, `financialYear`, `maxLimit` -> `max_limit NUMERIC(19,4)`, `deductionPercentage` -> `deduction_percent NUMERIC(5,2)`, `isProofRequired`, `isConditional`, `isAllowed`, `isActive`. | None dropped. |
| `reference.standard_deduction_rule_master` | `StandardDeductionRuleMaster.java:8-80` (`EmployeeITDeclaration/taxCalculator`) | `financialYear`, `taxRegime`, `amount` -> `amount NUMERIC(19,4)`, `description`, `isActive`. | None dropped. |
| `reference.section6a_item_master` | `Section6AItemMaster.java:12-154` (`EmployeeITDeclaration`) | `category`, `type` -> `section_code`, `maxLimit` -> `max_limit NUMERIC(19,4)`, `is80c` -> `is_80c`, `is80d` -> `is_80d`, `isOtherSection` -> `is_other_section`, `isActive`. Added `category_group_code` (e.g. `80C_GROUP`), `is_allowed_in_new_regime`, `display_order`. | `categoryFormatted`, `typeFormatted`, `maxLimitFormatted` dropped (string-formatted display text belongs on frontend). |
| `reference.section87a_rebate_rule_master` | `Section87ARebateRuleMaster.java:9-121` (`EmployeeITDeclaration/taxCalculator`) | `taxRegime`, `incomeThreshold` -> `income_threshold NUMERIC(19,4)`, `maxRebateAmount` -> `max_rebate_amount NUMERIC(19,4)`, `isFullRebate` -> `is_full_rebate`, `effectiveFrom`, `effectiveTo`, `isActive`, `remarks`. Added `financial_year`. | None dropped. |
| `reference.cess_surcharge_rule_master` | `CessSurchargeRuleMaster.java:8-129` (`EmployeeITDeclaration/taxCalculator`) | `ruleType` -> `rule_type`, `taxRegime` -> `tax_regime`, `incomeFrom` -> `income_from NUMERIC(19,4)`, `incomeTo` -> `income_to NUMERIC(19,4)`, `rate` -> `rate NUMERIC(5,2)`, `effectiveFrom`, `effectiveTo`, `isActive`, `remarks`. Added `financial_year`, `is_marginal_relief_applicable`. | None dropped. |

### Seed data has no legacy source script

The legacy application ships **no seed data for any of the eleven tax master tables**. `legacy/Payroll-Bend-SBoot` contains no `.sql`, `data.sql` or `import.sql` file, and its only `CommandLineRunner`, `MasterDataInitializer.java:96-98`, seeds `MasterConfig` rows for EPF and ESI alone. The tax rule rows exist only in the running MySQL database, populated by hand under `ddl-auto=update` (`DEBT-002`). `V005__reference_tax_seed.sql` therefore writes the statutory values fresh from the Finance Acts rather than porting a script, which is why every seeded value is asserted in §7 rather than trusted.

Two of the six remaining masters fail silently when empty, which is what tests 9 and 10 exist for. `section6a_item_master` and `cess_surcharge_rule_master` are read as lists (`OldTaxCalculationServiceImpl.java:826,1318,1399`), so an empty table yields zero Chapter VI-A deductions and no 4% cess with no error raised. The other four are read with `orElseThrow` (`OldTaxCalculationServiceImpl.java:248,526,986,1175`) and fail loudly.

### Migration Files

Flyway migration scripts are located under `code/backend/migration/src/main/resources/db/migration/reference/`:

| Migration File | Purpose |
|---|---|
| `V003__reference_lookups.sql` | DDL and seed data for the 4 generic lookup tables: `country`, `state`, `bank`, `currency`. |
| `V004__reference_tax_masters.sql` | DDL for the 11 statutory tax master tables, foreign keys, and indexes. |
| `V005__reference_tax_seed.sql` | Comprehensive statutory seed data for Indian Income Tax (Old & New regimes for FY 2023-24, FY 2024-25, FY 2025-26). |

### Annual Update Runbook (`D-08`)

When Indian tax legislation updates in a future Union Budget:
1. Create a new versioned Flyway migration script in `code/backend/migration/src/main/resources/db/migration/reference/` with the next global version number (e.g. `V00X__reference_tax_fy2026_2027.sql`).
2. Insert new financial year master records into `reference.tax_slab_master`, `reference.tax_slab_detail_history`, `reference.standard_deduction_rule_master`, `reference.section87a_rebate_rule_master`, etc.
3. Execute standard automated test suite (`ReferenceSchemaIT`).
4. Merge and deploy through standard CI/CD pipeline (`MigrationApplication`).

---

## 5. Frontend Changes

**None.** Reference tables provide backend statutory master data and system-level lookups. Frontend forms consume lookup endpoints exposed by domain services in later tickets (`W-13`, `W-32`, `W-47`).

---

## 6. Database Changes

### Summary Table

| Table Name | Schema | Tenant Column? | RLS Enabled? | Purpose |
|---|---|---|---|---|
| `country` | `reference` | **No (`D-08`)** | **No** | ISO 3166-1 country lookup |
| `state` | `reference` | **No (`D-08`)** | **No** | Indian States & Union Territories with GST codes |
| `bank` | `reference` | **No (`D-08`)** | **No** | Recognized banking institutions & IFSC prefixes |
| `currency` | `reference` | **No (`D-08`)** | **No** | ISO 4217 standard currency codes |
| `tax_slab_master` | `reference` | **No (`D-08`)** | **No** | Regime & financial year slab header |
| `tax_slab_master_history`| `reference` | **No (`D-08`)** | **No** | Audit version history for tax slab masters |
| `tax_slab_detail_history`| `reference` | **No (`D-08`)** | **No** | Income brackets, thresholds, and tax rates |
| `hra_rule_master` | `reference` | **No (`D-08`)** | **No** | Metro / Non-metro statutory HRA percentages & PAN threshold |
| `home_loan_rule_master` | `reference` | **No (`D-08`)** | **No** | Section 24(b) / 80EE / 80EEA home loan interest caps |
| `let_out_property_rule_master` | `reference` | **No (`D-08`)** | **No** | Statutory let-out property standard deduction rates & loss cap |
| `other_income_rule_master` | `reference` | **No (`D-08`)** | **No** | Section 80TTA / 80TTB interest exemption limits |
| `standard_deduction_rule_master` | `reference` | **No (`D-08`)** | **No** | Old/New regime standard deduction amounts per FY |
| `section6a_item_master` | `reference` | **No (`D-08`)** | **No** | Chapter VI-A investment deduction sections & limits |
| `section87a_rebate_rule_master` | `reference` | **No (`D-08`)** | **No** | Section 87A taxable income thresholds & rebates per FY |
| `cess_surcharge_rule_master` | `reference` | **No (`D-08`)** | **No** | Health & Education cess and high-income surcharge rates |

### DDL Specification

#### 1. Generic Lookups (`V003__reference_lookups.sql`)

```sql
-- 1. Country lookup
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

-- 2. State lookup
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

-- 3. Currency lookup
CREATE TABLE reference.currency (
    code CHAR(3) PRIMARY KEY,
    numeric_code CHAR(3) NOT NULL UNIQUE,
    symbol VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    decimal_places INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bank lookup
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
```

#### 2. Tax Masters (`V004__reference_tax_masters.sql`)

```sql
-- 5. Tax Slab Master
CREATE TABLE reference.tax_slab_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regime VARCHAR(10) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    financial_year VARCHAR(10) NOT NULL, -- e.g. '2023-2024', '2024-2025', '2025-2026'
    assessment_year VARCHAR(10) NOT NULL, -- e.g. '2024-2025', '2025-2026', '2026-2027'
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

-- 6. Tax Slab Master History
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

-- 7. Tax Slab Detail History (Rate Brackets)
CREATE TABLE reference.tax_slab_detail_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slab_master_id UUID NOT NULL REFERENCES reference.tax_slab_master(id) ON DELETE CASCADE,
    from_amount NUMERIC(19,4) NOT NULL,
    to_amount NUMERIC(19,4), -- NULL represents 'and above' / infinite ceiling
    tax_rate_percent NUMERIC(5,2) NOT NULL CHECK (tax_rate_percent >= 0 AND tax_rate_percent <= 100),
    slab_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tax_slab_detail_order UNIQUE (slab_master_id, slab_order)
);

CREATE INDEX idx_tax_slab_detail_master ON reference.tax_slab_detail_history(slab_master_id);

-- 8. HRA Rule Master
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

-- 9. Home Loan Rule Master (Section 24b, 80EE, 80EEA)
CREATE TABLE reference.home_loan_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    section_code VARCHAR(20) NOT NULL, -- '24B', '80EE', '80EEA'
    section_name VARCHAR(100) NOT NULL,
    component VARCHAR(20) NOT NULL CHECK (component IN ('PRINCIPAL', 'INTEREST')),
    property_type VARCHAR(20) NOT NULL DEFAULT 'SELF_OCCUPIED' CHECK (property_type IN ('SELF_OCCUPIED', 'LET_OUT', 'BOTH')),
    max_limit NUMERIC(19,4), -- e.g. 200000.0000, 50000.0000, 150000.0000
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

-- 10. Let Out Property Rule Master
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

-- 11. Other Income Rule Master (80TTA, 80TTB)
CREATE TABLE reference.other_income_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    section_code VARCHAR(20) NOT NULL, -- '80TTA', '80TTB', 'OTHER_INCOME'
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

-- 12. Standard Deduction Rule Master
CREATE TABLE reference.standard_deduction_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    regime VARCHAR(10) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    amount NUMERIC(19,4) NOT NULL, -- ₹50,000 or ₹75,000
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_std_deduction_fy_regime UNIQUE (financial_year, regime)
);

-- 13. Section 6A Item Master (Chapter VI-A Heads)
CREATE TABLE reference.section6a_item_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_code VARCHAR(20) NOT NULL, -- e.g. '80C', '80CCC', '80CCD(1)', '80CCD(1B)', '80D'
    category VARCHAR(50) NOT NULL, -- e.g. 'INVESTMENT', 'HEALTH_INSURANCE', 'DONATION'
    name VARCHAR(150) NOT NULL,
    description TEXT,
    max_limit NUMERIC(19,4),
    category_group_code VARCHAR(20), -- e.g. '80C_GROUP' for ₹1.5L umbrella limit
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

-- 14. Section 87A Rebate Rule Master
CREATE TABLE reference.section87a_rebate_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    regime VARCHAR(10) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    income_threshold NUMERIC(19,4) NOT NULL, -- Old: ₹5L; New: ₹7L (FY23-25), ₹12L (FY25-26)
    max_rebate_amount NUMERIC(19,4) NOT NULL, -- Old: ₹12,500; New: ₹25,000 (FY23-25), ₹60,000 (FY25-26)
    is_full_rebate BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(255),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sec87a_fy_regime UNIQUE (financial_year, regime)
);

-- 15. Cess & Surcharge Rule Master
CREATE TABLE reference.cess_surcharge_rule_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_year VARCHAR(10) NOT NULL,
    rule_type VARCHAR(20) NOT NULL DEFAULT 'CESS' CHECK (rule_type IN ('CESS', 'SURCHARGE')),
    tax_regime VARCHAR(10) NOT NULL DEFAULT 'BOTH' CHECK (tax_regime IN ('OLD', 'NEW', 'BOTH')),
    income_from NUMERIC(19,4),
    income_to NUMERIC(19,4),
    rate NUMERIC(5,2) NOT NULL, -- 4.00 for Cess; 10.00, 15.00, 25.00, 37.00 for Surcharge
    is_marginal_relief_applicable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(255),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cess_surcharge_fy ON reference.cess_surcharge_rule_master(financial_year, rule_type, tax_regime);
```

---

## 7. Tests

### Automated Integration Test (`ReferenceSchemaIT.java`)

A dedicated Testcontainers integration test placed under `code/backend/migration/src/test/java/com/infinevo/migration/ReferenceSchemaIT.java`:

| # | Test Method | Assertion / Probes |
|---|---|---|
| 1 | `referenceTablesExist_andOwnedByMigrationUser` | Queries PostgreSQL `pg_tables` in `reference` schema: verifies all 15 tables exist and are owned by `migration_user`. |
| 2 | `appUser_hasSelectPermission_onAllReferenceTables` | Connects as `app_user` and executes `SELECT count(*) FROM reference.<table>` for all 15 tables, ensuring 0 errors. |
| 3 | `appUser_refusedWriteAndDropPermissions_onReferenceSchema` | Connects as `app_user` and asserts `INSERT`, `UPDATE`, and `DELETE` throw `PSQLException` with SQL state `42501` (`permission denied for table`), and `DROP TABLE` throws `PSQLException` with `42501` (`must be owner of table`). |
| 4 | `referenceTables_haveNoTenantIdColumn_andNoRLS` | Queries `information_schema.columns` where `table_schema = 'reference'` and `column_name = 'tenant_id'`: asserts 0 matching columns. Queries `pg_class` where `relrowsecurity = true`: asserts 0 RLS policies in `reference` schema (`D-08`, `CONVENTIONS.md` Rule 7). |
| 5 | `genericLookups_seededCorrectly` | Asserts baseline countries (`IN`, `US`, `GB`, `AE`), Indian states (28 states + 8 UTs with GST codes), currencies (`INR`, `USD`, `EUR`, `GBP`), and scheduled commercial banks exist with valid data. |
| 6 | `taxSlabMasterAndDetails_seededPerFinancialYear` | **Discrete verification per Financial Year**: <br>• **FY 2023-24**: Old Regime (0-2.5L 0%, 2.5-5L 5%, 5-10L 20%, >10L 30%); New Regime (0-3L 0%, 3-6L 5%, 6-9L 10%, 9-12L 15%, 12-15L 20%, >15L 30%). <br>• **FY 2024-25**: New Regime (0-3L 0%, 3-7L 5%, 7-10L 10%, 10-12L 15%, 12-15L 20%, >15L 30%). <br>• **FY 2025-26**: New Regime (0-4L 0%, 4-8L 5%, 8-12L 10%, 12-16L 15%, 16-20L 20%, 20-24L 25%, >24L 30%). |
| 7 | `standardDeductionAndRebate_seededPerFinancialYear` | **Discrete verification per Financial Year**: <br>• **FY 2023-24**: SD Old ₹50k / New ₹50k; Sec 87A Old ₹12.5k (up to ₹5L) / New ₹25k (up to ₹7L). <br>• **FY 2024-25**: SD Old ₹50k / New ₹75k; Sec 87A Old ₹12.5k (up to ₹5L) / New ₹25k (up to ₹7L). <br>• **FY 2025-26**: SD Old ₹50k / New ₹75k; Sec 87A Old ₹12.5k (up to ₹5L) / **New ₹60,000 (up to ₹12,00,000)**. |
| 8 | `annualUpdateSimulation_oneMigrationAppliesCleanly` | Simulates the annual update process by executing a mock future FY migration script (`V099__test_annual_update.sql` placed in isolated test location avoiding clashes with fixtures `V001`-`V005`), verifying that new financial year slabs are immediately readable with zero tenant downtime. |
| 9 | `silentMasters_seededWithStatutoryRows` | **The two masters the legacy engine reads as a list, so an empty table returns no error.** `reference.section6a_item_master`: asserts all 13 Chapter VI-A heads present (`80C`, `80CCC`, `80CCD(1)`, `80CCD(1B)`, `80CCD(2)`, `80D`, `80DD`, `80DDB`, `80E`, `80G`, `80GGA`, `80GGC`, `80U`), `80C` capped at `150000.0000` with `is_80c = true`, `80D` with `is_80d = true`. `reference.cess_surcharge_rule_master`: asserts a `CESS` row at `rate = 4.00`, and four `SURCHARGE` rows at income thresholds ₹50L, ₹1Cr, ₹2Cr, ₹5Cr, with the New Regime maximum surcharge capped at `25.00`. |
| 10 | `remainingRuleMasters_seededPerFinancialYear` | Asserts at least one active row per financial year in `hra_rule_master` (metro `50.00`, non-metro `40.00`, basic-DA `10.00`, PAN threshold `100000.0000`), `home_loan_rule_master` (`24B` interest cap `200000.0000`), `let_out_property_rule_master` (standard deduction `30.00`, loss set-off cap `200000.0000`), and `other_income_rule_master` (`80TTA` `10000.0000`, `80TTB` `50000.0000`). |

---

## 8. Verification

```bash
# 1. Run full build and test suite
(cd code/backend && ./mvnw clean verify)

# 2. Run ReferenceSchemaIT specifically.
#    Failsafe reads it.test, not test: -Dtest is surefire's selector, and surefire
#    excludes **/*IT.java, so -Dtest=ReferenceSchemaIT runs every IT and then fails the
#    build for having matched no unit test.
(cd code/backend && ./mvnw verify -Dit.test=ReferenceSchemaIT -Dfailsafe.failIfNoSpecifiedTests=false)

# 3. No tenant_id column anywhere in the reference schema (D-08). Must return 0 lines.
#    Comment lines are excluded: V003 and V004 both explain in their headers why there is
#    no tenant_id here, and a bare grep matches that prose and reads as a failure.
git grep -nE "tenant_id" -- code/backend/migration/src/main/resources/db/migration/reference/ \
  | grep -v ':[0-9]*:[[:space:]]*--'
```

The authoritative version of check 3 is `ReferenceSchemaIT#referenceTables_haveNoTenantIdColumn_andNoRLS`,
which queries `information_schema.columns` on the migrated database. The grep reads the
script text; the test reads what Postgres actually built from it.

### Verification Acceptance Criteria

| Check | Target / Command | Expected Output | Result |
|---|---|---|---|
| Migration Execution | Flyway migration runner | Applies `V003`, `V004`, `V005` in `reference` schema cleanly | Pending |
| Schema Ownership | `ReferenceSchemaIT#referenceTablesExist_andOwnedByMigrationUser` | 15 tables present, owned by `migration_user` | Pending |
| Read Access | `ReferenceSchemaIT#appUser_hasSelectPermission_onAllReferenceTables` | `app_user` reads all 15 reference tables | Pending |
| Write & DDL Restriction | `ReferenceSchemaIT#appUser_refusedWriteAndDropPermissions_onReferenceSchema` | Writes blocked with `permission denied` (42501); `DROP TABLE` blocked with `must be owner of table` (42501) | Pending |
| Tenancy Exemption | `ReferenceSchemaIT#referenceTables_haveNoTenantIdColumn_andNoRLS` | 0 `tenant_id` columns, 0 RLS policies in `reference` | Pending |
| FY 2023-24 Seed Verification | `ReferenceSchemaIT#taxSlabMasterAndDetails_seededPerFinancialYear` | Verified: Old (0-2.5L-5L-10L), New (0-3L-6L-9L-12L-15L) | Pending |
| FY 2024-25 Seed Verification | `ReferenceSchemaIT#taxSlabMasterAndDetails_seededPerFinancialYear` | Verified: New (0-3L-7L-10L-12L-15L), SD ₹75,000 | Pending |
| FY 2025-26 Seed Verification | `ReferenceSchemaIT#taxSlabMasterAndDetails_seededPerFinancialYear` | Verified: New (0-4L-8L-12L-16L-20L-24L), 25% slab present, Sec 87A rebate ₹60,000 up to ₹12L | Pending |
| Section VI-A & Cess Seed | `ReferenceSchemaIT#silentMasters_seededWithStatutoryRows` | 13 Chapter VI-A heads present, `80C` cap ₹1,50,000; cess row at 4.00%, four surcharge rows, New Regime cap 25.00 | Pending |
| Remaining Rule Master Seed | `ReferenceSchemaIT#remainingRuleMasters_seededPerFinancialYear` | HRA 50/40/10 + PAN ₹1,00,000; 24(b) ₹2,00,000; let-out 30% + ₹2,00,000; 80TTA ₹10,000 / 80TTB ₹50,000 | Pending |

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Accidental inclusion of `tenant_id` on `reference` tables | Low | Hardcoded assertion in `ReferenceSchemaIT` and `tenant-audit` check preventing `tenant_id` in `reference` schema (`D-08`). |
| Application attempting writes on shared reference tables | Low | PostgreSQL role permissions (`W-05`): `app_user` has `SELECT` only on `reference` schema; DDL and write privileges are restricted exclusively to `migration_user`. |
| Statutory rate ambiguity across Union Budget amendments | Low | Reference tables include `financial_year`, `effective_from`, `effective_to`, and history tables (`tax_slab_master_history`, `tax_slab_detail_history`) to support retroactive and prospective adjustments. |
| Scale and precision truncation in monetary tax brackets | Low | All monetary limits enforce `NUMERIC(19,4)` and percentage rates enforce `NUMERIC(5,2)` in strict accordance with `CONVENTIONS.md` §2. |
| Flyway version number collision across schema folders | Low | Adheres to unified global version sequencing (`V001`-`V002` core, `V003`-`V005` reference, `V099` test simulation). |

---

## 10. Rollback

If a reference schema migration needs to be rolled back during deployment:
1. Since migrations run in forward-only Flyway sequence (`D-09`), write a corrective forward Flyway migration script (e.g. `V00X__revert_or_fix_tax_master.sql`) adjusting the affected rows or constraints.
2. In local/test environments, container destruction resets the database to the baseline state.
