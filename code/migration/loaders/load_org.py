"""3b — organisation masters (W-68 Task 3, mapping §3.1).

  department, designation, workLocations -> core.department, core.designation, core.work_location
  paySchedule (+ payScheduleWorkingDays)  -> payroll.pay_schedule, core.lop_policy
  incomeTaxDetails                        -> payroll.tax_deductor (signatory set by load_employees)

Codes are the legacy code where one exists, else an upper-snake slug of the name, made
unique per tenant. A row that already exists under the same (tenant, code) keeps its id;
the id-map records whichever id the target holds.
"""
from __future__ import annotations

import logging
import re
import sys
from collections import defaultdict
from datetime import date

from common import db, staging
from common.ids import det_id, unique_codes
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import iso_weekday, text, to_date, truthy
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_org"
INPUTS = [
    "stg_payroll_department.json", "stg_payroll_designation.json", "stg_payroll_workLocations.json",
    "stg_payroll_paySchedule.json", "stg_payroll_payScheduleWorkingDays.json",
    "stg_payroll_incomeTaxDetails.json",
]
TAN = re.compile(r"^[A-Z]{4}[0-9]{5}[A-Z]$")
PAN = re.compile(r"^[A-Z]{5}[0-9]{4}[A-Z]$")
log = logging.getLogger(STEP)


def _by_org(rows: list[dict]) -> dict[str, list[dict]]:
    out = defaultdict(list)
    for r in rows:
        out[str(r.get("organizationId"))].append(r)
    return out


def _upsert_coded(cur, table: str, tenant_id: str, rows: list[dict]) -> dict[str, str]:
    """Upsert on (tenant_id, code); return {code: id actually stored}."""
    if not rows:
        return {}
    db.upsert(cur, table, rows, conflict=("tenant_id", "code"), keep_on_update=("id",))
    cur.execute(f"SELECT code, id FROM {table} WHERE tenant_id = %s", (tenant_id,))
    return {code: str(i) for code, i in cur.fetchall()}


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    depts = _by_org(staging.load("stg_payroll_department.json"))
    desigs = _by_org(staging.load("stg_payroll_designation.json"))
    locs = _by_org(staging.load("stg_payroll_workLocations.json"))
    scheds = {str(s.get("organizationId")): s for s in staging.load("stg_payroll_paySchedule.json")}
    sched_days = defaultdict(list)
    for d in staging.load("stg_payroll_payScheduleWorkingDays.json"):
        sched_days[str(d.get("payScheduleId"))].append(d.get("workingDays"))
    taxes = {str(t.get("organizationId")): t for t in staging.load("stg_payroll_incomeTaxDetails.json")}

    def work(cur, t: Tenant) -> StepResult:
        tid, org = t.tenant_id, t.legacy_org_id
        done = skipped = 0
        cur.execute("SELECT code, name FROM reference.state")
        states = {name.lower(): code for code, name in cur.fetchall()}
        audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}

        # Departments
        rows = depts.get(org, [])
        named = [r for r in rows if text(r.get("name"))]
        for r in rows:
            if not text(r.get("name")):
                q.add("NO_NAME", "department", r.get("id"), tid); skipped += 1
        codes = unique_codes([text(r.get("departmentCode"), 50) or text(r.get("name"), 50) for r in named])
        payload = [{"id": det_id(tid, "department", r.get("id")), "tenant_id": tid, "code": code,
                    "name": text(r.get("name"), 150), "is_active": truthy(r.get("status"), True), **audit}
                   for r, code in zip(named, codes)]
        stored = _upsert_coded(cur, "core.department", tid, payload)
        for r, p in zip(named, payload):
            idmap.put("payroll.department", r.get("id"), stored[p["code"]])
            if r.get("departmentId"):
                idmap.put("payroll.department.uuid", r.get("departmentId"), stored[p["code"]])
        done += len(payload)

        # Designations
        rows = desigs.get(org, [])
        named = [r for r in rows if text(r.get("name"))]
        for r in rows:
            if not text(r.get("name")):
                q.add("NO_NAME", "designation", r.get("id"), tid); skipped += 1
        codes = unique_codes([text(r.get("name"), 50) for r in named])
        payload = [{"id": det_id(tid, "designation", r.get("id")), "tenant_id": tid,
                    "code": code, "name": text(r.get("name"), 150),
                    "is_active": truthy(r.get("status"), True), **audit} for r, code in zip(named, codes)]
        stored = _upsert_coded(cur, "core.designation", tid, payload)
        for r, p in zip(named, payload):
            idmap.put("payroll.designation", r.get("id"), stored[p["code"]])
            if r.get("designationId"):
                idmap.put("payroll.designation.uuid", r.get("designationId"), stored[p["code"]])
        done += len(payload)

        # Work locations - at most one filing address per tenant (partial unique index)
        rows = locs.get(org, [])
        named = [r for r in rows if text(r.get("workLocationName"))]
        for r in rows:
            if not text(r.get("workLocationName")):
                q.add("NO_NAME", "workLocations", r.get("id"), tid); skipped += 1
        codes = unique_codes([text(r.get("workLocationName"), 50) for r in named])
        filing_taken = False
        payload = []
        for r, code in zip(named, codes):
            filing = bool(truthy(r.get("isFilingAddress"), False))
            if filing and filing_taken:
                q.add("SECOND_FILING_ADDRESS", "workLocations", r.get("id"), tid, note="loaded as not filing")
                filing = False
            filing_taken = filing_taken or filing
            state = text(r.get("state"), 100)
            country = (text(r.get("country")) or "").lower()
            payload.append({
                "id": det_id(tid, "workLocations", r.get("id")), "tenant_id": tid,
                "code": code, "name": text(r.get("workLocationName"), 150),
                "address_line1": text(r.get("streetAddress1"), 255), "address_line2": text(r.get("streetAddress2"), 255),
                "city": text(r.get("city"), 100), "state": state,
                "state_code": states.get((state or "").lower()),
                "zip_code": text(r.get("zipCode"), 20),
                "country_code": "IN" if country in ("", "india", "in") else None,
                "is_filing_address": filing, "is_active": truthy(r.get("status"), True), **audit,
            })
        stored = _upsert_coded(cur, "core.work_location", tid, payload)
        for r, p in zip(named, payload):
            idmap.put("payroll.workLocations", r.get("id"), stored[p["code"]])
        done += len(payload)

        # Pay schedule and LOP policy (one each per tenant)
        s = scheds.get(org)
        if s:
            days = sorted({d for d in (iso_weekday(x) for x in sched_days.get(str(s.get("id")), [])) if d})
            if not days:
                days = [1, 2, 3, 4, 5]
                q.add("NO_WORKING_DAYS", "paySchedule", s.get("id"), tid, note="defaulted to Mon-Fri")
            pay_day = text(s.get("payDay"))
            rule, dom = "LAST_WORKING_DAY", None
            if pay_day and pay_day.isdigit():
                n = int(pay_day)
                if 1 <= n <= 28:
                    rule, dom = "SPECIFIC_DAY", n
                else:
                    rule = "LAST_DAY_OF_PERIOD"
                    q.add("PAY_DAY_OVER_28", "paySchedule", s.get("id"), tid, pay_day=n,
                          note="target allows 1-28; loaded as LAST_DAY_OF_PERIOD")
            start = to_date(s.get("payPeriodStartDate"))
            if start is None:
                q.add("NO_PERIOD_START", "paySchedule", s.get("id"), tid); skipped += 1
            else:
                db.upsert(cur, "payroll.pay_schedule", [{
                    "id": det_id(tid, "paySchedule", s.get("id")), "tenant_id": tid, "frequency": "MONTHLY",
                    "working_days": days, "pay_day_rule": rule, "pay_day_of_month": dom,
                    "first_period_start": start, **audit,
                }], conflict=("tenant_id",), keep_on_update=("id",))
                done += 1
            basis_raw = (text(s.get("workingDaysCalculationType")) or "actual_days").lower()
            basis = "ORG_DAYS" if basis_raw == "org_days" else "ACTUAL_DAYS"
            configured = s.get("noOfWorkingDays") if basis == "ORG_DAYS" else None
            lop = {
                "id": det_id(tid, "lop_policy", "1900-01-01"), "tenant_id": tid,
                "working_day_basis": basis, "configured_days_per_month": configured,
                "weekends_payable": bool(truthy(s.get("includeWeekends"), True)),
                "holidays_payable": bool(truthy(s.get("includeHolidays"), True)),
                "lop_rounding": "HALF_UP_2", "effective_from": date(1900, 1, 1), **audit,
            }
        else:
            # What core.provision_tenant gives a new tenant.
            lop = {
                "id": det_id(tid, "lop_policy", "1900-01-01"), "tenant_id": tid,
                "working_day_basis": "ACTUAL_DAYS", "configured_days_per_month": None,
                "weekends_payable": True, "holidays_payable": True,
                "lop_rounding": "HALF_UP_2", "effective_from": date(1900, 1, 1), **audit,
            }
        db.upsert(cur, "core.lop_policy", [lop], conflict=("tenant_id", "effective_from"), keep_on_update=("id",))
        done += 1

        # Tax deductor (TAN/PAN must be valid; signatory employee filled by load_employees)
        tx = taxes.get(org)
        if tx:
            tan = (text(tx.get("tanNumber")) or "").upper()
            pan = (text(tx.get("panNumber")) or "").upper()
            name = text(tx.get("authorizedPersonName"), 150)
            desig = text(tx.get("authorizedPersonDesignation"), 100)
            problems = [m for m, ok in (("TAN_INVALID", TAN.match(tan)), ("PAN_INVALID", PAN.match(pan)),
                                        ("NO_SIGNATORY_NAME", name), ("NO_SIGNATORY_DESIGNATION", desig)) if not ok]
            if problems:
                q.add(",".join(problems), "incomeTaxDetails", tx.get("id"), tid); skipped += 1
            else:
                db.upsert(cur, "payroll.tax_deductor", [{
                    "id": det_id(tid, "incomeTaxDetails", tx.get("id")), "tenant_id": tid,
                    "tan": tan, "pan": pan, "tds_circle": text(tx.get("tdsCircle"), 100),
                    "signatory_name": name, "signatory_parent_name": text(tx.get("authorizedPersonParent"), 150),
                    "signatory_designation": desig, **audit,
                }], conflict=("tenant_id",), keep_on_update=("id", "signatory_employee_id"))
                done += 1
        return StepResult(processed=done, skipped=skipped)

    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, tenants.all(), staging.checksum(INPUTS), work)
    finally:
        if own:
            conn.close()
    idmap.save()
    q.write()
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
