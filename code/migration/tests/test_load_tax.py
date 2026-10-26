"""3h load_tax against a real migrated Postgres (fixture: tests/fixtures/fx_tax.py)."""
import json
from datetime import date
from decimal import Decimal

import pytest

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and, run_step

TABLES = ["payroll.income_tax_declaration", "payroll.employee_investment_declaration",
          "payroll.employee_inv_house_rent", "payroll.employee_inv_home_loan", "payroll.employee_inv_let_out_property",
          "payroll.employee_inv_let_out_property_line", "payroll.employee_inv_other_income",
          "payroll.employee_inv_pre_tax_deduction", "payroll.employee_inv_prev_employment",
          "payroll.employee_inv_section6a", "payroll.employee_inv_tax_summary", "payroll.employee_tds",
          "payroll.employee_proof_of_investment", "payroll.employee_proof_item", "payroll.employee_proof_item_comment"]


@pytest.fixture(autouse=True)
def _fy(monkeypatch):
    monkeypatch.setenv("MIGRATION_FY", "2025-26")


def test_financial_year_parameter(monkeypatch):
    from loaders.load_tax import current_fy

    assert current_fy().label == "2025-2026"
    monkeypatch.setenv("MIGRATION_FY", "2024-2025")
    assert current_fy().label == "2024-2025"
    for bad in ("2025", "2025-27", "25-26"):
        monkeypatch.setenv("MIGRATION_FY", bad)
        with pytest.raises(ValueError):
            current_fy()
    monkeypatch.delenv("MIGRATION_FY")
    assert current_fy(date(2026, 3, 31)).label == "2025-2026"
    assert current_fy(date(2026, 4, 1)).label == "2026-2027"


def test_every_table_loaded(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    counts = {t: query(f"SELECT count(*) FROM {t}")[0][0] for t in TABLES}
    assert counts == {
        "payroll.income_tax_declaration": 2, "payroll.employee_investment_declaration": 3,
        "payroll.employee_inv_house_rent": 2, "payroll.employee_inv_home_loan": 1,
        "payroll.employee_inv_let_out_property": 1, "payroll.employee_inv_let_out_property_line": 3,
        "payroll.employee_inv_other_income": 1, "payroll.employee_inv_pre_tax_deduction": 2,
        "payroll.employee_inv_prev_employment": 2, "payroll.employee_inv_section6a": 3,
        "payroll.employee_inv_tax_summary": 2, "payroll.employee_tds": 3,
        "payroll.employee_proof_of_investment": 2, "payroll.employee_proof_item": 6,
        "payroll.employee_proof_item_comment": 3}


def test_values_mapped(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    itd = dict((r[0], r[1:]) for r in query(
        "SELECT tenant_id::text, financial_year, window_opens_on::text, window_closes_on::text, is_locked, "
        "default_tax_regime, notify_on_lock, poi_opens_on::text, poi_due_date::text, poi_comment_mandatory "
        "FROM payroll.income_tax_declaration"))
    assert itd[FOUNDER_TENANT] == ("2025-2026", "2025-04-01", "2026-01-31", False, "NEW", True, "2026-01-01",
                                   "2026-02-15", True)
    # a lock date from an earlier year leaves the window open to the FY end
    assert itd[CUSTOMER_TENANT][:5] == ("2025-2026", "2025-04-01", "2026-03-31", True, "OLD")
    decl = {r[0]: r[1:] for r in query(
        "SELECT e.employee_number, d.tax_regime, d.status, d.is_locked, d.is_staying_in_rented_house, "
        "d.submitted_at IS NOT NULL FROM payroll.employee_investment_declaration d "
        "JOIN core.employee e ON e.id = d.employee_id")}
    assert decl["E001"] == ("OLD", "SUBMITTED", False, True, True)
    assert decl["E002"] == ("NEW", "DRAFT", True, False, False)      # org default regime; can_allow_edit false
    assert decl["C001"][:2] == ("OLD", "SUBMITTED")                  # legacy APPROVED
    assert query("SELECT from_month::text, to_month::text, amount_per_month, landlord_pan, is_metro "
                 "FROM payroll.employee_inv_house_rent WHERE tenant_id = %s", (FOUNDER_TENANT,)) == [
        ("2025-04-01", "2026-03-01", Decimal("25000.50"), "ABCDE1234F", True)]
    assert query("SELECT landlord_pan FROM payroll.employee_inv_house_rent WHERE tenant_id = %s",
                 (CUSTOMER_TENANT,)) == [(None,)]
    lines = dict(query("SELECT line_type, amount FROM payroll.employee_inv_let_out_property_line"))
    assert lines == {"ANNUAL_RENT": Decimal("240000.00"), "MUNICIPAL_TAX": Decimal("12000.00"),
                     "LOAN_INTEREST": Decimal("50000.00")}
    assert dict(query("SELECT kind, amount FROM payroll.employee_inv_pre_tax_deduction")) == {
        "EMPLOYEE_PF": Decimal("21600.00"), "PROFESSIONAL_TAX": Decimal("2400.00")}
    assert dict(query("SELECT kind, entered_by FROM payroll.employee_inv_prev_employment")) == {
        "INCOME": "EMPLOYEE", "INCOME_TAX_DEDUCTED": "EMPLOYEE"}
    assert query("SELECT kind, amount FROM payroll.employee_inv_other_income") == [
        ("SAVINGS_INTEREST", Decimal("5000.55"))]
    sec = dict(query("SELECT m.section_code, sum(s.amount) FROM payroll.employee_inv_section6a s "
                     "JOIN reference.section6a_item_master m ON m.id = s.section6a_item_id GROUP BY 1"))
    assert sec == {"80C": Decimal("200000.00"), "80D": Decimal("25000.00")}
    summ = dict((r[0], r[1:]) for r in query(
        "SELECT regime, taxable_income, remaining_months FROM payroll.employee_inv_tax_summary"))
    assert summ["OLD"] == (Decimal("1200000.56"), 9) and summ["NEW"][0] == Decimal("1200000.00")


def test_tds_one_active_per_employee(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    rows = query("SELECT e.employee_number, t.regime, t.source, t.annual_tax, t.effective_from_period, t.is_active, "
                 "t.superseded_at::text, t.declaration_id IS NOT NULL FROM payroll.employee_tds t "
                 "JOIN core.employee e ON e.id = t.employee_id ORDER BY 1, t.is_active")
    assert rows == [
        ("C001", "OLD", "DECLARATION", Decimal("12500.00"), "2025-09", True, None, True),
        ("E001", "NEW", "DECLARATION", Decimal("70000.00"), "2025-04", False, "2026-01-05 04:30:00+00", True),
        ("E001", "OLD", "DECLARATION", Decimal("92500.45"), "2026-01", True, None, True),
    ]


def test_proofs(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    poi = dict(query("SELECT status, decided_at IS NOT NULL FROM payroll.employee_proof_of_investment"))
    assert poi == {"APPROVED": True, "DRAFT": False}
    items = sorted(query("SELECT source_kind, status, declared_amount, claimed_amount, approved_amount "
                         "FROM payroll.employee_proof_item"), key=lambda r: (r[0], r[2]))
    assert items == [
        ("HOUSE_RENT", "APPROVED", Decimal("300006.00"), Decimal("300000.00"), Decimal("290000.00")),
        ("LET_OUT_PROPERTY", "PENDING", Decimal("12000.00"), None, None),
        ("LET_OUT_PROPERTY", "PENDING", Decimal("50000.00"), None, None),
        ("LET_OUT_PROPERTY", "PENDING", Decimal("240000.00"), None, None),
        ("PREV_EMPLOYMENT", "DISALLOWED", Decimal("300000.00"), Decimal("300000.00"), Decimal("0.00")),
        ("SECTION_6A", "APPROVED", Decimal("150000.00"), Decimal("150000.00"), Decimal("150000.00")),
    ]
    # every item points at a line of its own proof's declaration
    assert query("""
        SELECT count(*) FROM payroll.employee_proof_item i
        JOIN payroll.employee_proof_of_investment p ON p.id = i.proof_id
        WHERE NOT EXISTS (
            SELECT 1 FROM payroll.employee_inv_house_rent x WHERE x.id = i.source_line_id AND x.declaration_id = p.declaration_id
            UNION ALL SELECT 1 FROM payroll.employee_inv_let_out_property_line x
                WHERE x.id = i.source_line_id AND x.declaration_id = p.declaration_id
            UNION ALL SELECT 1 FROM payroll.employee_inv_section6a x
                WHERE x.id = i.source_line_id AND x.declaration_id = p.declaration_id
            UNION ALL SELECT 1 FROM payroll.employee_inv_prev_employment x
                WHERE x.id = i.source_line_id AND x.declaration_id = p.declaration_id)""")[0][0] == 0
    comments = sorted(query("SELECT c.author_role, e.employee_number, c.body FROM payroll.employee_proof_item_comment c "
                            "JOIN core.employee e ON e.id = c.author_employee_id"))
    assert comments == [("EMPLOYEE", "E001", "Lease attached"), ("EMPLOYEE", "E001", "Uploaded the receipts"),
                        ("REVIEWER", "E002", "Verified")]
    idmap = json.loads((legacy_fixture / "id_map.json").read_text())
    assert set(idmap["payroll.employee_poi_item"]) == {"1", "2", "3", "5"}


def test_quarantine_reasons(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    q = json.loads((legacy_fixture / "stg_quarantined_load_tax.json").read_text())
    got = {(i["reason"], i["legacy_table"], str(i["legacy_id"])) for i in q}
    assert got == {
        ("NOT_CURRENT_FY", "employee_investment_declaration", "3"),
        ("EMPLOYEE_NOT_MIGRATED", "employee_investment_declaration", "5"),
        ("DECLARATION_STATUS_UNMAPPED", "employee_investment_declaration", "6"),
        ("HOUSE_RENT_BAD_MONTHS", "employee_inv_house_rent", "2"),
        ("PAN_DROPPED_NOT_10_CHARS", "employee_inv_house_rent", "4"),
        ("HOME_LOAN_NO_LENDER", "employee_inv_home_loan", "2"),
        ("DUPLICATE_PROPERTY_LINE", "employee_inv_let_out_property_detail", "3"),
        ("KIND_UNMAPPED", "employee_inv_other_income", "2"),
        ("SECTION6A_CODE_UNMAPPED", "employee_inv_section6a", "3"),
        ("NOT_CURRENT_FY", "employee_tds", "3"),
        ("TDS_UNMAPPED", "employee_tds", "5"),
        ("POI_STATUS_UNMAPPED", "employee_proof_of_investment", "2"),
        ("PROOF_ITEM_NOT_LOADED", "employee_poi_property_detail", "3"),
        ("PROOF_KIND_NOT_IN_TARGET", "employee_poi_item", "4"),
        ("PROOF_SOURCE_NOT_FOUND", "employee_poi_item", "6"),
        ("PROOF_SOURCE_NOT_FOUND", "employee_poi_item", "7"),
        ("COMMENT_AUTHOR_NOT_EMPLOYEE", "employee_poi_item_comment", "3"),
    }
    dump = json.dumps(q)
    assert "ABCDE" not in dump and "TOOLONGPAN" not in dump and "Landlord" not in dump


def test_customer_tenant_isolated(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    for t in ("payroll.employee_investment_declaration", "payroll.employee_tds",
              "payroll.employee_proof_of_investment"):
        assert query(f"SELECT count(*) FROM {t} x JOIN core.employee e ON e.id = x.employee_id "
                     f"WHERE e.tenant_id <> x.tenant_id")[0][0] == 0, t
    for t in TABLES[2:11]:
        assert query(f"SELECT count(*) FROM {t} x JOIN payroll.employee_investment_declaration d "
                     f"ON d.id = x.declaration_id WHERE d.tenant_id <> x.tenant_id")[0][0] == 0, t
    assert query("SELECT count(*) FROM payroll.employee_investment_declaration WHERE tenant_id = %s",
                 (CUSTOMER_TENANT,))[0][0] == 1


def test_other_year_loads_nothing_current(legacy_fixture, query, monkeypatch):
    monkeypatch.setenv("MIGRATION_FY", "2024-25")
    run_base_and("loaders.load_tax")
    assert query("SELECT financial_year, count(*) FROM payroll.employee_investment_declaration GROUP BY 1") == [
        ("2024-2025", 1)]


def test_second_run_is_a_no_op(legacy_fixture, query):
    run_base_and("loaders.load_tax")
    def snap():
        return {t: query(f"SELECT count(*), max({'created_at' if t.endswith('_comment') else 'updated_at'}) "
                         f"FROM {t}")[0] for t in TABLES}

    before = snap()
    run_step("loaders.load_tax")  # unchanged inputs: skipped by the ledger
    # a changed checksum (an unrelated input file) re-runs every upsert: still nothing changes
    (legacy_fixture / "stg_payroll_employee.json").write_text(
        (legacy_fixture / "stg_payroll_employee.json").read_text() + "\n")
    run_step("loaders.load_tax")
    after = snap()
    assert before == after
    assert sorted(query("SELECT tenant_id::text, status FROM core.migration_ledger WHERE step_name = 'load_tax'")) == \
        sorted([(FOUNDER_TENANT, "DONE"), (CUSTOMER_TENANT, "DONE")])


def test_tds_keyed_by_organisation_code(legacy_fixture, query):
    """Prod employee_tds.organization_id holds Organization.organizationId (e.g. '414068'), not
    the numeric id the org map is keyed by; every TDS row of org 8 was dropped silently before."""
    from tests.fixtures.fx_org import org_code

    p = legacy_fixture / "stg_payroll_employee_tds.json"
    rows = json.loads(p.read_text(encoding="utf-8"))
    for r in rows:
        r["organization_id"] = org_code(r["organization_id"])
    p.write_text(json.dumps(rows), encoding="utf-8")
    run_base_and("loaders.load_tax")
    by_tenant = dict(query("SELECT tenant_id::text, count(*) FROM payroll.employee_tds GROUP BY 1"))
    assert by_tenant[FOUNDER_TENANT] == 2 and by_tenant[CUSTOMER_TENANT] == 1
