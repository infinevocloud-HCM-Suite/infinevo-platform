"""W-68.2 merge and 3c load_employees against a real migrated Postgres."""
import json
import uuid

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_until

NS = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")


def test_cohorts_and_quarantine(legacy_fixture):
    from transformers import merge_employees

    merged = merge_employees.run()
    cohorts = {}
    for m in merged:
        cohorts[m["cohort"]] = cohorts.get(m["cohort"], 0) + 1
    assert cohorts == {"COHORT_1_BOTH": 10, "COHORT_2_HRMS_ONLY": 10, "COHORT_3_PAYROLL_ONLY": 4}
    q = json.loads((legacy_fixture / "stg_quarantined_duplicates.json").read_text())
    assert sorted(i["reason"] for i in q) == ["DUPLICATE_SOURCE_RECORD_IN_ORG", "NO_DOJ"]
    # stable identity: uuid5(tenant:work_email)
    e1 = next(m for m in merged if m["source_legacy_hrms_id"] == 1)
    assert e1["id"] == str(uuid.uuid5(NS, f"{FOUNDER_TENANT}:e1@founder.test"))
    # PAN match and personal-email match both found their HRMS person
    assert next(m for m in merged if m["source_legacy_payroll_id"] == 1010)["source_legacy_hrms_id"] == 10
    assert next(m for m in merged if m["source_legacy_payroll_id"] == 1009)["source_legacy_hrms_id"] == 9


def test_employees_loaded_with_details(legacy_fixture, query):
    run_until("loaders.load_employees")
    assert query("SELECT count(*) FROM core.employee")[0][0] == 24
    by_num = {r[0]: r[1:] for r in query(
        "SELECT employee_number, date_of_joining::text, gender, status, designation_id IS NOT NULL, "
        "work_location_id IS NOT NULL, termination_date::text FROM core.employee WHERE tenant_id = %s", (FOUNDER_TENANT,))}
    # HRMS wins the joining date over Payroll (E005), gender comes from HRMS
    assert by_num["E005"][0] == "2022-06-01" and by_num["E005"][1] == "FEMALE"
    assert by_num["E008"][2] == "TERMINATED"          # Payroll INACTIVE
    assert by_num["E019"][2] == "TERMINATED" and by_num["E019"][5] == "2024-01-31"
    assert by_num["E001"][3] and by_num["E001"][4]   # Payroll-sourced row has designation and location
    assert by_num["E015"][3] and not by_num["E015"][4]  # HRMS-only: designation from job title, no location
    # HRMS-only department text created a department in the tenant
    assert query("SELECT count(*) FROM core.department WHERE tenant_id = %s AND name = 'Design Studio'",
                 (FOUNDER_TENANT,))[0][0] == 1
    for t in ("core.employee_personal", "core.employee_contact", "core.employee_identification",
              "core.employee_employment"):
        assert query(f"SELECT count(*) FROM {t}")[0][0] == 24, t
    assert query("SELECT count(*) FROM payroll.employee_statutory_profile")[0][0] == 14
    banks = dict(query("SELECT payment_mode, count(*) FROM core.employee_bank GROUP BY 1"))
    assert banks == {"BANK_TRANSFER": 10, "CASH": 4}
    # Payroll fallbacks: father's name and a Payroll-only person's PAN come from employee_personal_detail
    assert query("SELECT father_name FROM core.employee_personal p JOIN core.employee e ON e.id = p.employee_id "
                 "WHERE e.employee_number = 'E003'") == [("Father3",)]
    kinds = dict(query("SELECT kind, count(*) FROM core.reporting_line GROUP BY 1"))
    assert kinds == {"PRIMARY": 19, "INDIRECT": 10, "APPROVER_L1": 10}
    assert query("SELECT e.employee_number FROM payroll.tax_deductor t JOIN core.employee e "
                 "ON e.id = t.signatory_employee_id") == [("E001",)]
    q = json.loads((legacy_fixture / "stg_quarantined_load_employees.json").read_text())
    reasons = {i["reason"] for i in q}
    assert "NO_TARGET_KIND_APPROVER_3" in reasons and "EMPLOYMENT_TYPE_UNMAPPED" in reasons
    # quarantine never carries personal data
    assert "PAN" not in json.dumps(q) and "@" not in json.dumps(q)


def test_customer_tenant_isolated(legacy_fixture, query):
    run_until("loaders.load_employees")
    assert query("SELECT count(*) FROM core.employee WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 4
    assert query("SELECT count(*) FROM core.reporting_line WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 0


def test_second_run_is_a_no_op(legacy_fixture, query):
    run_until("loaders.load_employees")
    tables = ["core.employee", "core.employee_personal", "core.employee_contact", "core.employee_identification",
              "core.employee_employment", "core.employee_bank", "payroll.employee_statutory_profile",
              "core.reporting_line", "core.department", "core.designation"]
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in tables}
    run_until("loaders.load_employees")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in tables}
    assert before == after
    assert query("SELECT count(*) FROM core.migration_ledger WHERE step_name = 'load_employees'")[0][0] == 2


def test_full_time_permanent_is_permanent():
    """Prod HRMS personal.employment_status is 'Full-Time Permanent' for 62 of 91 employees."""
    from transformers.merge_employees import norm_employment

    assert norm_employment("Full-Time Permanent") == "PERMANENT"
    assert norm_employment("Freelancer") is None
