"""W-70 Task 2 — loss-of-pay mismatch log for finance (W-67 R4).

Per employee per pay period, compares:
  HRMS    employee_monthly_lop.lop_days       - the unpaid days HRMS sent to payroll
  Payroll employee_payruns.totalNoOfLeaves    - the leave days the pay run deducted for,
          employee_payruns.LOP                - the amount it deducted (perDayPay x days)
Only periods where the two day counts differ go to core.lop_mismatch_log; matching
periods are counted. A variance is a finding for finance, never a correction (R4).

Idempotent: id = uuid5(namespace, "tenant_id:employee_number:pay_period"); a re-run updates
the day and deduction columns and never touches status, so a row finance approved stays
approved. Writes staging/lop_mismatch_<tenant>.csv for finance (no email - the CSV is
uploaded with the rehearsal results) and staging/lop_summary_<tenant>.json for W-70 Task 1.
"""
from __future__ import annotations

import csv
import json
import logging
import sys
import uuid
from collections import defaultdict
from decimal import Decimal

from common import db, staging
from common.ids import NAMESPACE
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date
from common.money import to_days, to_money
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "lop_mismatch"
INPUTS = ["stg_hrms_employee_monthly_lop.json", "stg_payroll_employee_payruns.json",
          "stg_payroll_payruns.json", "stg_merged_employees.json"]
ZERO_DAYS = Decimal("0.0")
ZERO_MONEY = Decimal("0.00")
log = logging.getLogger(STEP)


def row_id(tenant_id: str, employee_number: str, period: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{tenant_id}:{employee_number}:{period}"))


def collect(idmap: IdMap, q: Quarantine):
    """{tenant: {(employee_id, period): {"hrms": days, "payroll": days, "deduction": money}}}"""
    data = defaultdict(lambda: defaultdict(lambda: {"hrms": ZERO_DAYS, "payroll": ZERO_DAYS, "deduction": ZERO_MONEY}))

    for r in staging.load("stg_hrms_employee_monthly_lop.json"):
        ref = text(r.get("employee_id"))
        emp = idmap.get("hrms.emp_code", ref) or idmap.get("hrms.employee", ref)
        year, month = r.get("year"), r.get("month")
        if emp is None:
            q.add("NO_EMPLOYEE", "employee_monthly_lop", r.get("id"))
            continue
        if not year or not month:
            q.add("NO_PERIOD", "employee_monthly_lop", r.get("id"))
            continue
        tenant = idmap.get("employee.tenant", emp)
        data[tenant][(emp, f"{int(year):04d}-{int(month):02d}")]["hrms"] += to_days(r.get("lop_days")) or ZERO_DAYS

    payruns = {}
    for p in staging.load("stg_payroll_payruns.json"):
        start = to_date(p.get("payPeriodStartDate"))
        if start is None:
            continue
        period = f"{start.year:04d}-{start.month:02d}"
        payruns[("id", str(p.get("id")))] = period
        if p.get("payrunId"):
            payruns[("uuid", str(p.get("payrunId")))] = period

    for e in staging.load("stg_payroll_employee_payruns.json"):
        period = payruns.get(("id", str(e.get("payrun_ref_id")))) or payruns.get(("uuid", str(e.get("payrunId"))))
        emp = idmap.get("payroll.employee.uuid", e.get("employeeId")) or idmap.get("payroll.employee", e.get("employee_ref_id"))
        if period is None:
            q.add("NO_PAYRUN", "employee_payruns", e.get("id"))
            continue
        if emp is None:
            q.add("NO_EMPLOYEE", "employee_payruns", e.get("id"))
            continue
        tenant = idmap.get("employee.tenant", emp)
        cell = data[tenant][(emp, period)]
        cell["payroll"] += to_days(e.get("totalNoOfLeaves")) or ZERO_DAYS
        cell["deduction"] += to_money(e.get("LOP")) or ZERO_MONEY
    return data


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    data = collect(idmap, q)

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        cells = data.get(tid, {})
        cur.execute("SELECT id::text, employee_number, coalesce(work_email, '') FROM core.employee WHERE tenant_id = %s",
                    (tid,))
        emps = {i: (num, mail) for i, num, mail in cur.fetchall()}
        rows, matching, skipped = [], 0, 0
        for (emp, period), v in sorted(cells.items()):
            if emp not in emps:
                q.add("EMPLOYEE_NOT_LOADED", "lop", None, tid, period=period)
                skipped += 1
                continue
            variance = abs(v["hrms"] - v["payroll"])
            if variance == ZERO_DAYS:
                matching += 1
                continue
            num, mail = emps[emp]
            rows.append({"id": row_id(tid, num, period), "tenant_id": tid, "employee_number": num,
                         "work_email": mail, "pay_period": period, "hrms_unpaid_days": v["hrms"],
                         "payroll_lop_days": v["payroll"], "variance_days": variance,
                         "payroll_processed_deduction": v["deduction"]})
        db.upsert(cur, "core.lop_mismatch_log", rows)  # status is not sent, so never overwritten

        cur.execute("SELECT employee_number, pay_period, hrms_unpaid_days, payroll_lop_days, variance_days, "
                    "payroll_processed_deduction, status FROM core.lop_mismatch_log WHERE tenant_id = %s "
                    "ORDER BY pay_period, employee_number", (tid,))
        logged = cur.fetchall()
        with open(staging.stg_path(f"lop_mismatch_{tid}.csv"), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["employee_number", "pay_period", "hrms_unpaid_days", "payroll_lop_days", "variance_days",
                        "payroll_processed_deduction", "status"])
            w.writerows([[str(c) for c in r] for r in logged])
        staging.write(f"lop_summary_{tid}.json", {"mismatches": len(rows), "matching": matching,
                                                   "logged_total": len(logged)})
        return StepResult(processed=len(rows), skipped=skipped)

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
