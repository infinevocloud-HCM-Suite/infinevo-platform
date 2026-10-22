"""3c — merged employees -> core.employee and its one-to-one detail tables
(W-68 Task 3, mapping §3.3).

  core.employee (+ department_id, designation_id, work_location_id, termination_date)
  core.employee_personal, employee_contact, employee_identification, employee_employment,
  core.employee_bank, payroll.employee_statutory_profile
  HRMS report -> core.reporting_line (PRIMARY, INDIRECT, APPROVER_L1, APPROVER_L2)
  payroll.tax_deductor.signatory_employee_id (incomeTaxDetails.employeeId), which load_org
  could not set before the employees existed.

Department: the Payroll department by id; for an HRMS-only person, HRMS work.department
text matched by name (case-insensitive) within the tenant, created if the tenant has no
such department. Designation likewise from HRMS work.job_title.
"""
from __future__ import annotations

import logging
import sys
from collections import defaultdict

from common import db, staging
from common.ids import child_id, det_id, slug_code
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_employees"
# id_map.json is not an input: later loaders append to it, so its checksum would change
# between runs without this step's real inputs changing. The files its entries come from are.
INPUTS = ["stg_merged_employees.json", "stg_hrms_report.json", "stg_hrms_personal.json",
          "stg_payroll_incomeTaxDetails.json", "stg_payroll_department.json",
          "stg_payroll_designation.json", "stg_payroll_workLocations.json"]
KINDS = (("reporting_manager_id", "PRIMARY"), ("indirect_manager", "INDIRECT"),
         ("first_level_approver", "APPROVER_L1"), ("second_level_approver", "APPROVER_L2"))
log = logging.getLogger(STEP)

_PAYMENT = {"bank transfer": "BANK_TRANSFER", "bank_transfer": "BANK_TRANSFER", "bank": "BANK_TRANSFER",
            "neft": "BANK_TRANSFER", "online": "BANK_TRANSFER", "cash": "CASH", "cheque": "CASH"}
_ACCOUNT = {"savings": "SAVINGS", "saving": "SAVINGS", "current": "CURRENT"}


def _time(v):
    s = text(v)
    if not s:
        return None
    return s[:8] if len(s) >= 5 and s[2] == ":" else None


def _by_name_or_create(cur, table: str, tenant_id: str, name: str | None, cache: dict) -> str | None:
    """Find a department / designation by name in the tenant, or create it."""
    if not name:
        return None
    key = name.strip().lower()
    if key in cache:
        return cache[key]
    cur.execute(db.on_table("SELECT id FROM {} WHERE tenant_id = %s AND lower(name) = %s ORDER BY code LIMIT 1",
                            table),
                (tenant_id, key))
    row = cur.fetchone()
    if row:
        cache[key] = str(row[0])
        return cache[key]
    base = slug_code(name)
    code, n = base, 2
    while True:
        cur.execute(db.on_table("SELECT 1 FROM {} WHERE tenant_id = %s AND code = %s", table), (tenant_id, code))
        if not cur.fetchone():
            break
        code = f"{base[:46]}_{n}"
        n += 1
    new_id = det_id(tenant_id, f"hrms.{table}", key)
    db.upsert(cur, table, [{"id": new_id, "tenant_id": tenant_id, "code": code, "name": name.strip()[:150],
                            "is_active": True, "created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}])
    cache[key] = new_id
    return new_id


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    by_tenant: dict[str, list[dict]] = defaultdict(list)
    for m in staging.load("stg_merged_employees.json"):
        by_tenant[m["tenant_id"]].append(m)
    reports = {r["id"]: r for r in staging.load("stg_hrms_report.json")}
    taxes = {str(t.get("organizationId")): t for t in staging.load("stg_payroll_incomeTaxDetails.json")}

    def resolve_manager(ref) -> str | None:
        """HRMS stores managers as text: the manager's emp_id code, or the HRMS employee id."""
        ref = text(ref)
        if not ref:
            return None
        return idmap.get("hrms.emp_code", ref) or idmap.get("hrms.employee", ref)

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
        dept_cache, desig_cache = {}, {}
        emps, personal, contact, ident, employment, bank, statutory = [], [], [], [], [], [], []
        skipped = 0
        for m in by_tenant.get(tid, []):
            eid = m["id"]
            if not m.get("first_name"):
                q.add("NO_FIRST_NAME", "employee", m.get("source_legacy_payroll_id") or m.get("source_legacy_hrms_id"), tid)
                skipped += 1
                continue
            dept = idmap.get("payroll.department", m.get("department_legacy_id")) or \
                _by_name_or_create(cur, "core.department", tid, m.get("department_name"), dept_cache)
            desig = idmap.get("payroll.designation", m.get("designation_legacy_id")) or \
                _by_name_or_create(cur, "core.designation", tid, m.get("job_title"), desig_cache)
            emps.append({
                "id": eid, "tenant_id": tid, "employee_number": m["employee_number"][:50],
                "first_name": m["first_name"][:100], "middle_name": (m.get("middle_name") or None),
                "last_name": m.get("last_name"), "gender": m.get("gender"),
                "date_of_joining": to_date(m["date_of_joining"]), "termination_date": to_date(m.get("termination_date")),
                "status": m["status"], "work_email": m.get("work_email"), "mobile": m.get("mobile"),
                "is_portal_enabled": bool(m.get("is_portal_enabled", True)), "is_deleted": bool(m.get("is_deleted")),
                "department_id": dept, "designation_id": desig,
                "work_location_id": idmap.get("payroll.workLocations", m.get("work_location_legacy_id")),
                **audit,
            })
            p = m.get("personal") or {}
            personal.append({"id": child_id(eid, "personal"), "tenant_id": tid, "employee_id": eid,
                             "date_of_birth": to_date(m.get("date_of_birth")),
                             "marital_status": p.get("marital_status"), "nationality": p.get("nationality"),
                             "ethnicity": p.get("ethnicity"), "father_name": p.get("father_name"),
                             "differently_abled_type": p.get("differently_abled_type"),
                             "is_eligible_for_full_tax_exemption": bool(p.get("is_eligible_for_full_tax_exemption")),
                             **audit})
            c = dict(m.get("contact") or {})
            contact.append({"id": child_id(eid, "contact"), "tenant_id": tid, "employee_id": eid, **c, **audit})
            ident.append({"id": child_id(eid, "identification"), "tenant_id": tid, "employee_id": eid,
                          **(m.get("identification") or {}), **audit})
            e = dict(m.get("employment") or {})
            raw_type = e.pop("employment_type_raw", None)
            if raw_type and not e.get("employment_type"):
                q.add("EMPLOYMENT_TYPE_UNMAPPED", "personal", m.get("source_legacy_hrms_id"), tid, value=raw_type)
            e["shift_start_time"] = _time(e.get("shift_start_time"))
            e["shift_end_time"] = _time(e.get("shift_end_time"))
            employment.append({"id": child_id(eid, "employment"), "tenant_id": tid, "employee_id": eid, **e, **audit})
            if m.get("statutory"):
                statutory.append({"id": child_id(eid, "statutory"), "tenant_id": tid, "employee_id": eid,
                                  **m["statutory"], **audit})
            b = m.get("bank")
            if b:
                mode = _PAYMENT.get((b.get("payment_mode") or "").strip().lower())
                if mode is None:
                    q.add("PAYMENT_MODE_UNMAPPED", "employee_bank_detail", m.get("source_legacy_payroll_id"), tid,
                          value=b.get("payment_mode"))
                    mode = "BANK_TRANSFER" if b.get("bank_account_number") else "CASH"
                bank.append({"id": child_id(eid, "bank"), "tenant_id": tid, "employee_id": eid,
                             "payment_mode": mode, "account_holder_name": b.get("account_holder_name"),
                             "bank_name": b.get("bank_name"), "ifsc_code": (b.get("ifsc_code") or None),
                             "bank_account_number": b.get("bank_account_number"),
                             "bank_account_type": _ACCOUNT.get((b.get("bank_account_type") or "").strip().lower()),
                             **audit})

        db.upsert(cur, "core.employee", emps, keep_on_update=("user_account_id",))
        for table, rows in (("core.employee_personal", personal), ("core.employee_contact", contact),
                            ("core.employee_identification", ident), ("core.employee_employment", employment),
                            ("core.employee_bank", bank), ("payroll.employee_statutory_profile", statutory)):
            db.upsert(cur, table, rows, conflict=("tenant_id", "employee_id"), keep_on_update=("id",))

        # Reporting lines from HRMS report
        loaded = {e["id"]: e for e in emps}
        lines = []
        for m in by_tenant.get(tid, []):
            if m["id"] not in loaded or not m.get("report_id"):
                continue
            r = reports.get(m["report_id"], {})
            for col, kind in KINDS:
                ref = r.get(col)
                if not text(ref):
                    continue
                mgr = resolve_manager(ref)
                if mgr is None or mgr not in loaded:
                    q.add("MANAGER_NOT_FOUND", "report", m["report_id"], tid, kind=kind)
                    skipped += 1
                    continue
                if mgr == m["id"]:
                    q.add("SELF_MANAGER", "report", m["report_id"], tid, kind=kind)
                    skipped += 1
                    continue
                lines.append({"id": det_id(tid, f"report.{kind}", m["id"]), "tenant_id": tid,
                              "employee_id": m["id"], "manager_id": mgr, "kind": kind,
                              "effective_from": loaded[m["id"]]["date_of_joining"], **audit})
            if text(r.get("third_level_approver")):
                q.add("NO_TARGET_KIND_APPROVER_3", "report", m["report_id"], tid,
                      note="target has APPROVER_L1 and APPROVER_L2 only")
        db.upsert(cur, "core.reporting_line", lines)

        # Tax deductor signatory (load_org loaded the row without it)
        tx = taxes.get(t.legacy_org_id)
        if tx and text(tx.get("employeeId")):
            sig = idmap.get("payroll.employee.uuid", tx.get("employeeId"))
            if sig in loaded:
                cur.execute("UPDATE payroll.tax_deductor SET signatory_employee_id = %s, updated_by = %s "
                            "WHERE tenant_id = %s AND signatory_employee_id IS DISTINCT FROM %s",
                            (sig, db.AUDIT_USER, tid, sig))
            else:
                q.add("SIGNATORY_NOT_FOUND", "incomeTaxDetails", tx.get("id"), tid)
        return StepResult(processed=len(emps) + len(lines), skipped=skipped)

    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, tenants.all(), staging.checksum(INPUTS), work)
    finally:
        if own:
            conn.close()
    q.write()
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
