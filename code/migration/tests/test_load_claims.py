"""3l load_claims against a real migrated Postgres (W-68 Task 4)."""
import json
import uuid
from decimal import Decimal

from tests.fixtures import CUSTOMER_ORG, CUSTOMER_TENANT, FOUNDER_ORG, FOUNDER_TENANT
from tests.pipeline import BASE, run_base_and, run_step, run_steps

STEP = "loaders.load_claims"
TABLES = ["payroll.employee_reimbursement_request", "payroll.employee_deduction", "core.pay_input"]

# Legacy reimbursement masters (load_salary's input) and the target rows it would have made.
MASTERS = [
    {"id": 901, "reimbursementId": "R-901", "reimbursementName": "Internet Reimbursement",
     "reimbursementType": "Internet Reimbursement", "displayName": "Internet", "isDeleted": False,
     "organizationId": int(FOUNDER_ORG)},
    {"id": 902, "reimbursementId": "R-902", "reimbursementName": "Fuel Reimbursement",
     "reimbursementType": "Fuel Reimbursement", "displayName": "Fuel", "isDeleted": False,
     "organizationId": int(FOUNDER_ORG)},
    {"id": 903, "reimbursementId": "R-903", "reimbursementName": "Internet Reimbursement",
     "reimbursementType": "Internet Reimbursement", "displayName": "Internet", "isDeleted": False,
     "organizationId": int(CUSTOMER_ORG)},
    # matches claim 9 (FOOD) but is never loaded: COMPONENT_NOT_LOADED
    {"id": 904, "reimbursementId": "R-904", "reimbursementName": "Food Reimbursement",
     "reimbursementType": "Food Reimbursement", "displayName": "Food", "isDeleted": False,
     "organizationId": int(FOUNDER_ORG)},
]
LOADED = {901: FOUNDER_TENANT, 902: FOUNDER_TENANT, 903: CUSTOMER_TENANT}


def _setup(staging_dir, query) -> dict:
    """Base steps, then the masters and period locks a real run would already have."""
    run_steps(BASE)
    (staging_dir / "stg_payroll_reimbursements.json").write_text(json.dumps(MASTERS), encoding="utf-8")
    idmap_path = staging_dir / "id_map.json"
    idmap = json.loads(idmap_path.read_text(encoding="utf-8"))
    ids = {}
    for legacy, tid in LOADED.items():
        rid = str(uuid.uuid4())
        ids[legacy] = rid
        query("INSERT INTO payroll.reimbursement (id, tenant_id, code, name, reimbursement_type) "
              "VALUES (%s, %s, %s, %s, %s)", (rid, tid, f"R{legacy}", f"Master {legacy}", "INTERNET"))
        idmap.setdefault("payroll.reimbursements", {})[str(legacy)] = rid
    idmap_path.write_text(json.dumps(idmap), encoding="utf-8")
    # 2024-03 is closed for the founder (deduction 14, already taken, must not move);
    # 2024-07 is closed for the customer (deduction 13, still open, moves to 2024-08).
    query("INSERT INTO core.pay_input_period_lock (tenant_id, period, locked_by) VALUES (%s, '2024-03', 'test')",
          (FOUNDER_TENANT,))
    query("INSERT INTO core.pay_input_period_lock (tenant_id, period, locked_by) VALUES (%s, '2024-07', 'test')",
          (CUSTOMER_TENANT,))
    return ids


def _quarantine(staging_dir):
    return json.loads((staging_dir / "stg_quarantined_load_claims.json").read_text(encoding="utf-8"))


def test_claims_loaded_and_mapped(legacy_fixture, query):
    masters = _setup(legacy_fixture, query)
    run_step(STEP)
    rows = {r[0]: r[1:] for r in query(
        "SELECT e.employee_number, c.tenant_id::text, c.reimbursement_id::text, c.requested_amount, "
        "c.approved_amount, c.status, c.bill_date::text, c.document_id, c.approved_by IS NOT NULL, "
        "c.created_at = '2024-05-11T09:15:00+05:30'::timestamptz, c.created_by "
        "FROM payroll.employee_reimbursement_request c JOIN core.employee e ON e.id = c.employee_id")}
    assert set(rows) == {"E001", "E002", "C001"}
    e1, e2, c1 = rows["E001"], rows["E002"], rows["C001"]
    assert e1[0] == FOUNDER_TENANT and e1[1] == masters[901] and e1[2] == Decimal("1500.5000")
    assert e1[3] is None and e1[4] == "SUBMITTED" and e1[5] == "2024-05-10" and e1[6] is None
    assert e1[8] is True and e1[9] == "migration"     # legacy created_at kept, audit user
    assert e2[1] == masters[902] and e2[3] == Decimal("1999.9900") and e2[4] == "APPROVED" and e2[7] is True
    assert c1[0] == CUSTOMER_TENANT and c1[1] == masters[903] and c1[4] == "APPROVED"
    q = _quarantine(legacy_fixture)
    claims_q = {i["legacy_id"]: i["reason"] for i in q if i["legacy_table"] == "employee_reimbursement_request"}
    assert claims_q == {3: "NO_MATCHING_COMPONENT", 5: "EMPLOYEE_NOT_MIGRATED", 6: "AMOUNT_NOT_POSITIVE",
                        7: "STATUS_UNMAPPED", 8: "TENANT_MISMATCH", 9: "COMPONENT_NOT_LOADED"}
    assert "@" not in json.dumps(q) and "Claim" not in json.dumps(q)   # ids and codes only
    # the id-map carries the claims for load_documents
    idmap = json.loads((legacy_fixture / "id_map.json").read_text(encoding="utf-8"))
    assert set(idmap["payroll.employee_reimbursement_request"]) == {"1", "2", "4"}


def test_deductions_post_to_the_ledger(legacy_fixture, query):
    _setup(legacy_fixture, query)
    run_step(STEP)
    rows = {r[0]: r[1:] for r in query(
        "SELECT e.employee_number, d.deduction_type, d.amount, d.period, d.posted_period, d.status, "
        "p.kind, p.amount, p.period, p.source_module, p.source_ref = 'employee_deduction:' || d.id "
        "FROM payroll.employee_deduction d JOIN core.employee e ON e.id = d.employee_id "
        "JOIN core.pay_input p ON p.id = d.pay_input_id")}
    assert set(rows) == {"E001", "C002"}  # E002's deduction was already taken in legacy
    assert rows["E001"] == ("ADVANCE_RECOVERY", Decimal("2500.7500"), "2024-06", "2024-06", "POSTED",
                            "AD_HOC_DEDUCTION", Decimal("2500.7500"), "2024-06", "payroll", True)
    # open deduction in a locked month moves to the next open one, as the app does
    assert rows["C002"][:5] == ("OTHER", Decimal("499.9900"), "2024-07", "2024-08", "POSTED")
    assert rows["C002"][7] == "2024-08"
    q = {i["legacy_id"]: i["reason"] for i in _quarantine(legacy_fixture) if i["legacy_table"] == "employee_deduction"}
    # already taken in legacy (PROCESSED) is never re-posted, locked month or not
    assert q == {12: "ALREADY_DEDUCTED_IN_LEGACY", 14: "ALREADY_DEDUCTED_IN_LEGACY",
                 15: "AMOUNT_NOT_POSITIVE", 16: "NO_PERIOD"}


def test_tenant_isolation_and_ledger(legacy_fixture, query):
    _setup(legacy_fixture, query)
    run_step(STEP)
    for table, founder, customer in (("payroll.employee_reimbursement_request", 2, 1),
                                     ("payroll.employee_deduction", 1, 1), ("core.pay_input", 1, 1)):
        got = dict(query(f"SELECT tenant_id::text, count(*) FROM {table} GROUP BY 1"))
        assert got == {FOUNDER_TENANT: founder, CUSTOMER_TENANT: customer}, table
    # no row points at an employee of another tenant
    assert query("SELECT count(*) FROM payroll.employee_reimbursement_request c JOIN core.employee e "
                 "ON e.id = c.employee_id WHERE e.tenant_id <> c.tenant_id")[0][0] == 0
    ledger = dict(query("SELECT tenant_id::text, records_skipped FROM core.migration_ledger "
                        "WHERE step_name = 'load_claims' AND status = 'DONE'"))
    # founder: claims 3, 5 (org 148), 6, 7, 8 (employee in founder), 9 and deductions 12, 14, 15, 16
    assert ledger == {FOUNDER_TENANT: 10, CUSTOMER_TENANT: 0}


def test_without_masters_every_claim_is_quarantined(legacy_fixture, query):
    run_base_and(STEP)
    assert query("SELECT count(*) FROM payroll.employee_reimbursement_request")[0][0] == 0
    # 11 and 13 load; 12 and 14 were already taken in legacy and are never re-posted
    assert query("SELECT count(*) FROM payroll.employee_deduction")[0][0] == 2
    reasons = {i["reason"] for i in _quarantine(legacy_fixture) if i["legacy_table"] == "employee_reimbursement_request"}
    assert "NO_MATCHING_COMPONENT" in reasons


def test_second_run_is_a_no_op(legacy_fixture, query):
    _setup(legacy_fixture, query)
    run_step(STEP)
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    run_step(STEP)                     # unchanged inputs: the ledger skips both tenants
    assert {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES} == before
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_claims'")
    run_step(STEP)                     # forced: every upsert finds nothing to change
    assert {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES} == before
    assert dict(query("SELECT tenant_id::text, count(*) FROM core.migration_ledger "
                      "WHERE step_name = 'load_claims' AND status = 'DONE' GROUP BY 1")) ==         {FOUNDER_TENANT: 1, CUSTOMER_TENANT: 1}


def test_rerun_keeps_the_document_w69_attached(legacy_fixture, query):
    _setup(legacy_fixture, query)
    run_step(STEP)
    doc = str(uuid.uuid4())
    query("INSERT INTO core.document (id, tenant_id, kind, file_name, content_type, size_bytes, blob_container, "
          "blob_path, checksum_sha256) VALUES (%s, %s, 'REIMBURSEMENT_RECEIPT', 'r.pdf', 'application/pdf', 1, "
          "'documents', %s, %s)", (doc, FOUNDER_TENANT, f"{FOUNDER_TENANT}/x/REIMBURSEMENT_RECEIPT/{doc}", "0" * 64))
    query("UPDATE payroll.employee_reimbursement_request SET document_id = %s WHERE description = 'Claim 1'", (doc,))
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_claims'")
    run_step(STEP)
    assert query("SELECT document_id::text FROM payroll.employee_reimbursement_request "
                 "WHERE description = 'Claim 1'") == [(doc,)]
