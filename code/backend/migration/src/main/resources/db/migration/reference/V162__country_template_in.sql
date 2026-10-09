-- Migration: V162__country_template_in.sql
-- Schema: reference
-- Purpose: W-73.9 reference.country_template - the defaults a new tenant of a country starts with,
--          one row per (country, section). India (IN) first; another country is one more migration.
--
-- No tenant_id and no RLS: shared national data, like reference.pt_slab (V064). app_user reads it
-- through the reference schema's default privileges (infra/postgres/03-grants.sql).
--
-- Each section is read by one TenantTemplateContributor bean - core: holidays, leave_types;
-- payroll: salary_components, statutory, pay_schedule - which writes the tenant's own rows only
-- where the tenant has none. A rate or a list that changes is a new migration that updates the
-- payload and bumps version; core.tenant_template_applied records the version each tenant got.
--
-- Money and rates are JSON strings, parsed to BigDecimal, never JSON numbers (CONVENTIONS.md).
--
-- Not here, on purpose:
--   * Professional tax - it has no per-tenant settings row: the slabs come from reference.pt_slab by
--     each work location's state (ProfessionalTaxServiceImpl.java:233-266).
--   * PF, ESI, PT and TDS as catalogue deductions - they are derived pay-run lines
--     (StatutoryLineDeriver, StatutoryComponentCode), so a catalogue row would list them twice.
--   * Tax slabs - per financial year in reference already (W-33).
--   * The rest of 2027's holidays - added by a migration once the central government publishes its
--     2027 list; the five dates known now are here.

CREATE TABLE reference.country_template (
    country_code CHAR(2) NOT NULL REFERENCES reference.country(code),
    section VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (country_code, section),
    CONSTRAINT chk_country_template_section
        CHECK (section IN ('holidays', 'leave_types', 'salary_components', 'statutory', 'pay_schedule')),
    CONSTRAINT chk_country_template_version CHECK (version >= 1)
);

COMMENT ON TABLE reference.country_template IS
    'W-73.9: a country''s default holidays, leave types, salary components, statutory settings and pay schedule, applied to a new tenant of that country';

-- Holidays: the central government's 17 closed holidays for 2026 as gazetted for Delhi (DoPT holiday
-- OM of 3 July 2025: the 14 compulsory and the three chosen), and for 2027 the five dates known now.
-- The contributor writes the current and the next calendar year into one default calendar.
INSERT INTO reference.country_template (country_code, section, version, payload) VALUES
('IN', 'holidays', 1, $json$
{
  "calendar_name": "India national holidays",
  "years": {
    "2026": [
      {"date": "2026-01-26", "name": "Republic Day"},
      {"date": "2026-03-04", "name": "Holi"},
      {"date": "2026-03-21", "name": "Id-ul-Fitr"},
      {"date": "2026-03-26", "name": "Ram Navami"},
      {"date": "2026-03-31", "name": "Mahavir Jayanti"},
      {"date": "2026-04-03", "name": "Good Friday"},
      {"date": "2026-05-01", "name": "Buddha Purnima"},
      {"date": "2026-05-27", "name": "Id-ul-Zuha (Bakrid)"},
      {"date": "2026-06-26", "name": "Muharram"},
      {"date": "2026-08-15", "name": "Independence Day"},
      {"date": "2026-08-26", "name": "Milad-un-Nabi"},
      {"date": "2026-09-04", "name": "Janmashtami"},
      {"date": "2026-10-02", "name": "Gandhi Jayanti"},
      {"date": "2026-10-20", "name": "Dussehra"},
      {"date": "2026-11-08", "name": "Diwali"},
      {"date": "2026-11-24", "name": "Guru Nanak Jayanti"},
      {"date": "2026-12-25", "name": "Christmas"}
    ],
    "2027": [
      {"date": "2027-01-26", "name": "Republic Day"},
      {"date": "2027-03-26", "name": "Good Friday"},
      {"date": "2027-08-15", "name": "Independence Day"},
      {"date": "2027-10-02", "name": "Gandhi Jayanti"},
      {"date": "2027-12-25", "name": "Christmas"}
    ]
  }
}
$json$),

('IN', 'leave_types', 1, $json$
{
  "types": [
    {"code": "EL",  "name": "Earned Leave",    "paid": true,  "half_day": true},
    {"code": "CL",  "name": "Casual Leave",    "paid": true,  "half_day": true},
    {"code": "SL",  "name": "Sick Leave",      "paid": true,  "half_day": true},
    {"code": "ML",  "name": "Maternity Leave", "paid": true,  "half_day": false},
    {"code": "PL",  "name": "Paternity Leave", "paid": true,  "half_day": false},
    {"code": "LOP", "name": "Loss of Pay",     "paid": false, "half_day": true}
  ]
}
$json$),

-- Earning types are the named ones the component drawer offers (D-37, componentFields.js).
('IN', 'salary_components', 1, $json$
{
  "earnings": [
    {"code": "BASIC", "name": "Basic", "earning_type": "Basic", "calculation_type": "PERCENTAGE", "default_value": "50", "percentage_of": "CTC", "taxable": true, "pro_rata": true, "epf_inclusion_type": "ALWAYS", "included_in_esi": true},
    {"code": "HRA", "name": "House Rent Allowance", "earning_type": "House Rent Allowance", "calculation_type": "PERCENTAGE", "default_value": "50", "percentage_of": "BASIC", "taxable": true, "pro_rata": true, "included_in_esi": true},
    {"code": "DA", "name": "Dearness Allowance", "earning_type": "Dearness Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "epf_inclusion_type": "ALWAYS", "included_in_esi": true},
    {"code": "CONVEYANCE", "name": "Conveyance Allowance", "earning_type": "Conveyance Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "included_in_esi": true},
    {"code": "SPECIAL", "name": "Special Allowance", "earning_type": "Custom Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "epf_inclusion_type": "WHEN_PF_WAGE_BELOW_15000", "included_in_esi": true},
    {"code": "MEDICAL", "name": "Medical Allowance", "earning_type": "Fixed Medical Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "included_in_esi": true},
    {"code": "LTA", "name": "Leave Travel Allowance", "earning_type": "Leave Travel Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": false},
    {"code": "CEA", "name": "Children Education Allowance", "earning_type": "Children Education Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true},
    {"code": "TELEPHONE", "name": "Telephone and Internet Allowance", "earning_type": "Telephone And Internet Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true},
    {"code": "FOOD", "name": "Food Allowance", "earning_type": "Food Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "included_in_esi": true},
    {"code": "FUEL", "name": "Fuel Allowance", "earning_type": "Fuel Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true},
    {"code": "CCA", "name": "City Compensatory Allowance", "earning_type": "City Compensatory Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "included_in_esi": true},
    {"code": "UNIFORM", "name": "Uniform Allowance", "earning_type": "Uniform Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true},
    {"code": "SHIFT", "name": "Shift Allowance", "earning_type": "Shift Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": true, "included_in_esi": true},
    {"code": "OVERTIME", "name": "Overtime Allowance", "earning_type": "Overtime Allowance", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": false, "variable": true, "included_in_esi": true},
    {"code": "BONUS", "name": "Bonus", "earning_type": "Bonus", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": false, "variable": true},
    {"code": "COMMISSION", "name": "Commission", "earning_type": "Commission", "calculation_type": "FLAT", "default_value": "0", "taxable": true, "pro_rata": false, "variable": true}
  ],
  "deductions": [
    {"code": "SALARY_ADVANCE", "name": "Salary Advance Recovery", "deduction_type": "POST_TAX", "calculation_type": "FLAT", "default_value": "0", "recurring": false},
    {"code": "LOAN_EMI", "name": "Loan EMI", "deduction_type": "POST_TAX", "calculation_type": "FLAT", "default_value": "0", "recurring": true},
    {"code": "CANTEEN", "name": "Canteen", "deduction_type": "POST_TAX", "calculation_type": "FLAT", "default_value": "0", "recurring": true},
    {"code": "NOTICE_PAY", "name": "Notice Pay Recovery", "deduction_type": "POST_TAX", "calculation_type": "FLAT", "default_value": "0", "recurring": false}
  ]
}
$json$),

-- Statutory rates as the EPF and ESI acts set them today. Both seeded DISABLED: the tenant opts in
-- with its registration number on /payroll/settings/epf and /esi.
('IN', 'statutory', 1, $json$
{
  "epf": {
    "enabled": false,
    "employee_rate": "12",
    "employer_rate": "12",
    "eps_rate": "8.33",
    "edli_rate": "0.5",
    "admin_charge_rate": "0.5",
    "wage_ceiling": "15000",
    "eps_senior_age": 58
  },
  "esi": {
    "enabled": false,
    "employee_rate": "0.75",
    "employer_rate": "3.25",
    "wage_ceiling": "21000"
  }
}
$json$),

-- Monthly, Monday to Friday, paid on the last working day, inputs close on the 25th. The first
-- period is the month the template is applied in.
('IN', 'pay_schedule', 1, $json$
{
  "frequency": "MONTHLY",
  "working_days": [1, 2, 3, 4, 5],
  "pay_day_rule": "LAST_WORKING_DAY",
  "input_cutoff_day": 25
}
$json$);
