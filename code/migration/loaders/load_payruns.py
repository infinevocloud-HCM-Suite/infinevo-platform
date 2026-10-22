"""3g - pay runs and pay history (W-68 Task 3, mapping §3.6, decision X-06).

  payruns + employee_payruns        -> payroll.payrun (REGULAR, or OFF_CYCLE for legacy
                                       OFF_CYCLE / ONE_TIME_PAYOUT runs) + payroll.employee_payrun
  employee_payruns five totals      -> synthetic payroll.employee_payrun_line rows
                                       (TDS, LOP, BONUS, CLAIM_DEDUCTION, CLAIM_REIMBURSEMENT)
  employee_payruns of PAID runs     -> payroll.prior_payroll_month per employee per period,
                                       under one prior_payroll_import_log (+ its source
                                       core.document) per tenant per financial year -
                                       ONLY for periods with no loaded REGULAR run: the
                                       target already counts a migrated run's TDS line as
                                       prior tax, so a prior month there would count it twice
  off_cycle_pay_run + _employee + _earnings + _deductions
                                    -> payroll.payrun run_type OFF_CYCLE + employee_payrun totals
  one_time_payout                   -> payroll.scheduled_earning (PAID once its pay date is past)

Legacy keeps totals only (EmployeePayRun.java); no component lines are invented. A run's
header totals are recomputed from the employee rows actually loaded (the branch's rule).

Status: the target's own mapping (code/backend/payroll/.../payrun/PayRunStatus.java:11-13):
DRAFT->DRAFT, SUBMITTED/APPROVAL_PENDING->COMPUTED, APPROVED->APPROVED, COMPLETED->PAID,
REJECTED->LOCKED; READY ("unused") and anything else is quarantined with the status.
"""
from __future__ import annotations

import calendar
import hashlib
import json
import logging
import sys
from collections import defaultdict
from datetime import date, datetime

from common import db, staging
from common.ids import child_id, det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp
from common.money import money_or_zero, to_decimal, to_money
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_payruns"
INPUTS = ["stg_payroll_payruns.json", "stg_payroll_employee_payruns.json",
          "stg_payroll_off_cycle_pay_run.json", "stg_payroll_off_cycle_payrun_employee.json",
          "stg_payroll_off_cycle_payrun_employee_earnings.json",
          "stg_payroll_off_cycle_payrun_employee_deductions.json", "stg_payroll_one_time_payout.json",
          # id-map sources: employees (merge + load_employees), earnings (load_salary)
          "stg_merged_employees.json", "stg_payroll_employee.json", "stg_payroll_earnings.json"]
log = logging.getLogger(STEP)

AUDIT = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}

# Legacy PayRunStatus (enumeration/payruns/PayRunStatus.java) -> target payroll.payrun.status
RUN_STATUS = {"DRAFT": "DRAFT", "SUBMITTED": "COMPUTED", "APPROVAL_PENDING": "COMPUTED",
              "APPROVED": "APPROVED", "COMPLETED": "PAID", "REJECTED": "LOCKED"}
# OffCyclePayRun.status is free lower-case text, "draft" by default (OffCyclePayRunServiceImpl.java:67)
OFF_CYCLE_STATUS = {"DRAFT": "DRAFT", "SUBMITTED": "COMPUTED", "APPROVAL_PENDING": "COMPUTED",
                    "PROCESSED": "COMPUTED", "APPROVED": "APPROVED", "COMPLETED": "PAID", "PAID": "PAID",
                    "REJECTED": "LOCKED", "CANCELLED": "CANCELLED"}
# Legacy PayRunType (enumeration/payruns/PayRunType.java), by name or JSON value
RUN_TYPE = {"REGULAR": "REGULAR", "OFF_CYCLE": "OFF_CYCLE", "ONE_TIME_PAYOUT": "OFF_CYCLE"}
STATUS_RANK = {"PAID": 6, "APPROVED": 5, "COMPUTED": 4, "LOCKED": 3, "DRAFT": 2, "CANCELLED": 1}

# Synthetic lines: (code, legacy columns in preference order, line_kind, source, name, taxable, sort)
# Codes and sources are the target's own: TDS (tds/TaxLineContributor.java:34-35, source TAX),
# LOP (payrun/LopLineContributor.java:45-46, source LOP); the rest are PAY_INPUT lines.
LINES = (
    ("TDS", ("totalTaxes", "monthlyTds"), "DEDUCTION", "TAX", "Tax deducted at source", False, 10),
    ("LOP", ("LOP", "lwp_amount"), "DEDUCTION", "LOP", "Loss of pay", False, 20),
    ("BONUS", ("bonus",), "EARNING", "PAY_INPUT", "Bonus", True, 30),
    ("CLAIM_DEDUCTION", ("claim_deduction",), "DEDUCTION", "PAY_INPUT", "Claim deduction", False, 40),
    ("CLAIM_REIMBURSEMENT", ("claim_reimbursement",), "REIMBURSEMENT", "PAY_INPUT", "Claim reimbursement",
     False, 50),
)

ZERO = money_or_zero(0)


def _norm(v) -> str:
    return (text(v) or "").upper().replace("-", "_").replace(" ", "_")


def _first(row: dict, cols) -> object:
    for c in cols:
        if row.get(c) is not None:
            return row.get(c)
    return None


def _days(v):
    d = to_decimal(v)
    return d if d is not None else to_decimal(0)


def _period_of(d: date) -> str:
    return f"{d.year:04d}-{d.month:02d}"


def _month_bounds(period: str) -> tuple[date, date]:
    y, m = int(period[:4]), int(period[5:7])
    return date(y, m, 1), date(y, m, calendar.monthrange(y, m)[1])


def _parse_period(p: dict) -> str | None:
    """payPeriodStartDate, else processingPeriod ("July 2025"), else payDate (old loader's order)."""
    start = to_date(p.get("payPeriodStartDate"))
    if start:
        return _period_of(start)
    proc = text(p.get("processingPeriod"))
    if proc:
        for fmt in ("%B %Y", "%b %Y", "%Y-%m"):
            try:
                return _period_of(datetime.strptime(proc, fmt).date())
            except ValueError:
                pass
    pay = to_date(p.get("payDate"))
    return _period_of(pay) if pay else None


def financial_year(period: str) -> str:
    y, m = int(period[:4]), int(period[5:7])
    return f"{y}-{y + 1}" if m >= 4 else f"{y - 1}-{y}"


class _Plan:
    """Everything one tenant will write, built before the database is touched."""

    def __init__(self):
        self.payruns: list[dict] = []
        self.emp_payruns: list[dict] = []
        self.lines: list[dict] = []
        self.prior: list[dict] = []  # {employee_id, period, gross_earnings, tds, net_pay}
        self.payouts: list[dict] = []  # one_time_payout rows awaiting the earning lookup


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    plans: dict[str, _Plan] = defaultdict(_Plan)
    today = date.today()

    def employee_of(legacy_emp, tid, table, legacy_id) -> str | None:
        eid = idmap.get("payroll.employee", legacy_emp)
        if eid is None:
            q.add("EMPLOYEE_NOT_MIGRATED", table, legacy_id, tid, employee_ref=legacy_emp)
            return None
        owner = idmap.get("employee.tenant", eid)
        if owner and owner != tid:
            q.add("EMPLOYEE_TENANT_MISMATCH", table, legacy_id, tid, employee_ref=legacy_emp)
            return None
        return eid

    # ---- regular pay runs ------------------------------------------------------------
    lines_by_run: dict[str, list[dict]] = defaultdict(list)
    for line in staging.load("stg_payroll_employee_payruns.json"):
        lines_by_run[str(line.get("payrun_ref_id"))].append(line)

    candidates: dict[tuple[str, str, str], list[dict]] = defaultdict(list)  # (tid, run_type, period)
    known_runs = set()
    for p in staging.load("stg_payroll_payruns.json"):
        lid = p["id"]
        known_runs.add(str(lid))
        t = tenants.for_org(p.get("organizationId"))
        if t is None:
            q.add("ORG_NOT_MIGRATED", "payruns", lid, organization=p.get("organizationId"))
            continue
        tid = t.tenant_id
        status = RUN_STATUS.get(_norm(p.get("status")))
        if status is None:
            q.add("PAYRUN_STATUS_UNMAPPED", "payruns", lid, tid, status=p.get("status"))
            continue
        run_type = RUN_TYPE.get(_norm(p.get("type")) or "REGULAR")
        if run_type is None:
            q.add("PAYRUN_TYPE_UNMAPPED", "payruns", lid, tid, type=p.get("type"))
            continue
        period = _parse_period(p)
        if period is None:
            q.add("PAYRUN_NO_PERIOD", "payruns", lid, tid)
            continue
        pay_date = to_date(p.get("payDate"))
        if pay_date is None:
            q.add("PAYRUN_NO_PAY_DATE", "payruns", lid, tid)
            continue
        m_start, m_end = _month_bounds(period)
        start = to_date(p.get("payPeriodStartDate")) or m_start
        end = to_date(p.get("payPeriodEndDate")) or m_end
        if start > end:
            q.add("PAYRUN_PERIOD_INVERTED", "payruns", lid, tid)
            continue
        candidates[(tid, run_type, period)].append(
            {"legacy": p, "tid": tid, "status": status, "run_type": run_type, "period": period,
             "start": start, "end": end, "pay_date": pay_date})

    loaded_runs: dict[str, dict] = {}  # legacy run id -> candidate
    for (tid, run_type, period), cands in candidates.items():
        if run_type == "REGULAR" and len(cands) > 1:
            # one live REGULAR run per tenant per period (uk_payrun_tenant_period)
            cands.sort(key=lambda c: (STATUS_RANK[c["status"]], int(c["legacy"]["id"])), reverse=True)
            for loser in cands[1:]:
                q.add("DUPLICATE_REGULAR_PERIOD", "payruns", loser["legacy"]["id"], tid, period=period,
                      kept=cands[0]["legacy"]["id"])
            cands = cands[:1]
        for c in cands:
            loaded_runs[str(c["legacy"]["id"])] = c

    for ref, rows in lines_by_run.items():
        if ref in loaded_runs:
            continue
        reason = "PAYRUN_NOT_LOADED" if ref in known_runs else "PAYRUN_NOT_FOUND"
        for line in rows:
            q.add(reason, "employee_payruns", line.get("id"), idmap.get("employee.tenant",
                  idmap.get("payroll.employee", line.get("employee_ref_id"))), payrun_ref=ref)

    for ref, c in loaded_runs.items():
        p, tid = c["legacy"], c["tid"]
        plan = plans[tid]
        run_id = det_id(tid, "payruns", p["id"])
        idmap.put("payroll.payruns", p["id"], run_id)
        rows: list[dict] = []
        seen_emp: dict[str, dict] = {}
        for line in sorted(lines_by_run.get(ref, []), key=lambda r: int(r["id"]), reverse=True):
            eid = employee_of(line.get("employee_ref_id"), tid, "employee_payruns", line.get("id"))
            if eid is None:
                continue
            if eid in seen_emp:  # uk_employee_payrun_tenant_run_employee: newest legacy row wins
                q.add("DUPLICATE_EMPLOYEE_IN_RUN", "employee_payruns", line.get("id"), tid,
                      kept=seen_emp[eid]["legacy_id"])
                continue
            lop_days = _days(line.get("lwp_days") if line.get("lwp_days") is not None else line.get("totalNoOfLeaves"))
            ep_id = det_id(tid, "employee_payruns", line["id"])
            idmap.put("payroll.employee_payruns", line["id"], ep_id)
            row = {"id": ep_id, "tenant_id": tid, "payrun_id": run_id, "employee_id": eid,
                   "inclusion_status": "INCLUDED", "skip_reason": None,
                   "gross_earnings": money_or_zero(line.get("totalEarnings")),
                   "total_reimbursements": money_or_zero(line.get("totalReimbursements")),
                   "total_benefits": money_or_zero(line.get("totalBenefits")),
                   "total_deductions": money_or_zero(line.get("totalDeductions")),
                   "net_pay": money_or_zero(line.get("netPay")),
                   "lop_days": lop_days, "unpaid_days": lop_days, "paid_days": _days(line.get("paidDays")),
                   **AUDIT}
            seen_emp[eid] = {"legacy_id": line["id"]}
            rows.append(row)
            tds = ZERO
            for code, cols, kind, source, name, taxable, sort in LINES:
                amount = to_money(_first(line, cols))
                if amount is None or amount == 0:
                    continue
                if amount < 0:
                    q.add("NEGATIVE_LINE_AMOUNT", "employee_payruns", line["id"], tid, line_code=code)
                    continue
                if code == "TDS":
                    tds = amount
                plan.lines.append({"id": child_id(ep_id, code), "tenant_id": tid, "employee_payrun_id": ep_id,
                                   "payrun_id": run_id, "line_kind": kind, "source": source,
                                   "component_id": None, "component_code": code, "component_name": name,
                                   "amount": amount, "is_taxable": taxable, "sort_order": sort, **AUDIT})
            if c["status"] == "PAID" and c["run_type"] == "REGULAR":
                plan.prior.append({"employee_id": eid, "period": c["period"], "legacy_id": line["id"],
                                   "gross_earnings": row["gross_earnings"], "tds": tds,
                                   "net_pay": row["net_pay"]})
        plan.emp_payruns.extend(rows)
        plan.payruns.append(_header(run_id, tid, c, rows, notes=text(p.get("rejectedReason"), 500),
                                    approved_on=to_date(p.get("approvedDate")),
                                    fallback=(p.get("totalPayrollCost"), p.get("totalDeductions"),
                                              p.get("totalNetPay"), p.get("noOfEmployees")),
                                    skipped=p.get("noOfSkippedEmployees"), created_at=None))

    # ---- off-cycle runs ----------------------------------------------------------------
    earn_sum: dict[str, object] = defaultdict(lambda: ZERO)
    for e in staging.load("stg_payroll_off_cycle_payrun_employee_earnings.json"):
        earn_sum[str(e.get("off_cycle_employee_id"))] += money_or_zero(e.get("amount"))
    ded_sum: dict[str, object] = defaultdict(lambda: ZERO)
    for d in staging.load("stg_payroll_off_cycle_payrun_employee_deductions.json"):
        ded_sum[str(d.get("off_cycle_employee_id"))] += money_or_zero(d.get("amount"))
    oc_emps: dict[str, list[dict]] = defaultdict(list)
    for e in staging.load("stg_payroll_off_cycle_payrun_employee.json"):
        oc_emps[str(e.get("payroll_run_id"))].append(e)

    for r in staging.load("stg_payroll_off_cycle_pay_run.json"):
        lid = r["id"]
        t = tenants.for_org(r.get("organizationId"))
        if t is None:
            q.add("ORG_NOT_MIGRATED", "off_cycle_pay_run", lid, organization=r.get("organizationId"))
            continue
        tid = t.tenant_id
        status = OFF_CYCLE_STATUS.get(_norm(r.get("status")))
        if status is None:
            q.add("OFF_CYCLE_STATUS_UNMAPPED", "off_cycle_pay_run", lid, tid, status=r.get("status"))
            continue
        pay_date = to_date(r.get("payDate"))
        if pay_date is None:
            q.add("PAYRUN_NO_PAY_DATE", "off_cycle_pay_run", lid, tid)
            continue
        period = _period_of(pay_date)
        start, end = _month_bounds(period)
        c = {"tid": tid, "status": status, "run_type": "OFF_CYCLE", "period": period, "start": start,
             "end": end, "pay_date": pay_date}
        run_id = det_id(tid, "off_cycle_pay_run", lid)
        idmap.put("payroll.off_cycle_pay_run", lid, run_id)
        plan = plans[tid]
        rows, seen_emp = [], set()
        for e in sorted(oc_emps.get(str(lid), []), key=lambda x: int(x["id"]), reverse=True):
            eid = employee_of(e.get("employee_id"), tid, "off_cycle_payrun_employee", e.get("id"))
            if eid is None:
                continue
            if eid in seen_emp:
                q.add("DUPLICATE_EMPLOYEE_IN_RUN", "off_cycle_payrun_employee", e.get("id"), tid)
                continue
            seen_emp.add(eid)
            gross, ded = earn_sum[str(e["id"])], ded_sum[str(e["id"])]
            ep_id = det_id(tid, "off_cycle_payrun_employee", e["id"])
            idmap.put("payroll.off_cycle_payrun_employee", e["id"], ep_id)
            rows.append({"id": ep_id, "tenant_id": tid, "payrun_id": run_id, "employee_id": eid,
                         "inclusion_status": "INCLUDED", "skip_reason": None, "gross_earnings": gross,
                         "total_reimbursements": ZERO, "total_benefits": ZERO, "total_deductions": ded,
                         "net_pay": gross - ded, "lop_days": _days(0), "unpaid_days": _days(0),
                         "paid_days": _days(0), **AUDIT})
        plan.emp_payruns.extend(rows)
        plan.payruns.append(_header(run_id, tid, c, rows, notes=text(r.get("notes"), 500), approved_on=None,
                                    fallback=(0, 0, 0, 0), skipped=0, created_at=to_timestamp(r.get("createdAt"))))

    # ---- one-time payouts (earning resolved inside the tenant transaction) ---------------
    for o in staging.load("stg_payroll_one_time_payout.json"):
        lid = o["id"]
        t = tenants.for_org(o.get("organizationId"))
        if t is None:
            q.add("ORG_NOT_MIGRATED", "one_time_payout", lid, organization=o.get("organizationId"))
            continue
        tid = t.tenant_id
        if o.get("employee_id") in (None, ""):
            q.add("NO_EMPLOYEE", "one_time_payout", lid, tid)
            continue
        eid = employee_of(o.get("employee_id"), tid, "one_time_payout", lid)
        if eid is None:
            continue
        amount = to_money(o.get("earningAmount"))
        if amount is None or amount <= 0:
            q.add("NON_POSITIVE_AMOUNT", "one_time_payout", lid, tid)
            continue
        pay_date = to_date(o.get("payDate"))
        if pay_date is None:
            q.add("PAYRUN_NO_PAY_DATE", "one_time_payout", lid, tid)
            continue
        plans[tid].payouts.append({"legacy": o, "employee_id": eid, "amount": amount, "pay_date": pay_date})

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        plan = plans.get(tid) or _Plan()
        _upsert_grouped(cur, "payroll.payrun", plan.payruns)
        db.upsert(cur, "payroll.employee_payrun", plan.emp_payruns)
        db.upsert(cur, "payroll.employee_payrun_line", plan.lines)
        # The target counts a migrated run's TAX lines as prior income itself
        # (EmployeePayRunLineRepository, EmployeeTdsServiceImpl), and its own importer
        # refuses a prior month where a real run exists (PriorPayrollRowValidator
        # REAL_RUN_EXISTS). A prior month is written only for a period with no loaded
        # REGULAR run, so TDS is never counted twice.
        run_periods = {p["period"] for p in plan.payruns if p.get("run_type") == "REGULAR"}
        prior = [p for p in plan.prior if p["period"] not in run_periods]
        prior_count = _load_prior_months(cur, tid, prior, q)

        cur.execute("SELECT id::text FROM payroll.earning WHERE tenant_id = %s", (tid,))
        earnings = {r[0] for r in cur.fetchall()}
        sched = []
        for po in plan.payouts:
            o = po["legacy"]
            earning = idmap.get("payroll.earnings", o.get("earning_id"))
            if earning is None or earning not in earnings:
                q.add("EARNING_NOT_MIGRATED", "one_time_payout", o["id"], tid, earning_ref=o.get("earning_id"))
                continue
            first = po["pay_date"].replace(day=1)
            paid = po["pay_date"] <= today
            sid = det_id(tid, "one_time_payout", o["id"])
            idmap.put("payroll.one_time_payout", o["id"], sid)
            row = {"id": sid, "tenant_id": tid, "employee_id": po["employee_id"], "earning_id": earning,
                   "amount": po["amount"], "first_period": first, "instalments": 1,
                   "paid_instalments": 1 if paid else 0, "last_paid_period": first if paid else None,
                   "status": "PAID" if paid else "SCHEDULED", "reason": "Legacy one-time payout",
                   "status_reason": None, **AUDIT}
            created = to_timestamp(o.get("createdAt"))
            if created:
                row["created_at"] = created
            sched.append(row)
        _upsert_grouped(cur, "payroll.scheduled_earning", sched)
        processed = len(plan.payruns) + len(plan.emp_payruns) + len(plan.lines) + prior_count + len(sched)
        # every quarantined row of this tenant, found before or inside this transaction
        return StepResult(processed=processed, skipped=q.count(tid))

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


def _upsert_grouped(cur, table: str, rows: list[dict]) -> None:
    """Rows with and without a legacy created_at go in separate statements (one key set each)."""
    for cols in sorted({tuple(r.keys()) for r in rows}):
        db.upsert(cur, table, [r for r in rows if tuple(r.keys()) == cols])


def _header(run_id, tid, c, rows, notes, approved_on, fallback, skipped, created_at) -> dict:
    """payroll.payrun; totals are the sum of the employee rows loaded (branch rule), the
    legacy header figures only when no employee row loaded."""
    if rows:
        gross = sum((r["gross_earnings"] for r in rows), ZERO)
        ded = sum((r["total_deductions"] for r in rows), ZERO)
        net = sum((r["net_pay"] for r in rows), ZERO)
        included = len(rows)
        negative = sum(1 for r in rows if r["net_pay"] < 0)
    else:
        gross, ded, net = (money_or_zero(v) for v in fallback[:3])
        included = int(fallback[3] or 0)
        negative = 0
    status = c["status"]
    approved_at = None
    if status in ("APPROVED", "PAID") and approved_on:
        approved_at = to_timestamp(approved_on.isoformat())
    row = {"id": run_id, "tenant_id": tid, "period": c["period"], "period_start": c["start"],
           "period_end": c["end"], "cutoff_date": c["end"], "pay_date": c["pay_date"],
           "run_type": c["run_type"], "status": status, "included_count": included,
           "skipped_count": int(skipped or 0), "total_gross": gross, "total_deductions": ded,
           "total_net_pay": net, "negative_net_count": negative, "notes": notes,
           "approved_at": approved_at, "paid_on": c["pay_date"] if status == "PAID" else None, **AUDIT}
    if created_at:
        row["created_at"] = created_at
    return row


def _load_prior_months(cur, tid: str, prior: list[dict], q: Quarantine) -> int:
    """prior_payroll_month per employee per period, under one import log per financial year.

    The import log needs a source core.document (NOT NULL FK). The migration writes the
    rows it imports as that document's content, into staging, and records its size and
    sha256; the blob itself is uploaded with the other migrated documents (W-69)."""
    by_key: dict[tuple[str, str], dict] = {}
    for p in prior:
        k = (p["employee_id"], p["period"])
        if k in by_key:  # two PAID regular runs cannot share a period; kept for safety
            agg = by_key[k]
            for f in ("gross_earnings", "tds", "net_pay"):
                agg[f] += p[f]
        else:
            by_key[k] = dict(p)
    by_fy: dict[str, list[dict]] = defaultdict(list)
    for (eid, period), p in sorted(by_key.items()):
        if p["gross_earnings"] < 0 or p["net_pay"] < 0 or p["tds"] < 0 or \
                p["net_pay"] > p["gross_earnings"] - p["tds"]:
            # prior_payroll_month_check: net <= gross - epf - esi - pt - tds (those three are 0)
            q.add("PRIOR_MONTH_TOTALS_INCONSISTENT", "employee_payruns", p["legacy_id"], tid, period=period)
            continue
        by_fy[financial_year(period)].append(p)

    docs, logs, months = [], [], []
    for fy, rows in sorted(by_fy.items()):
        content = json.dumps(
            [{"employee_id": r["employee_id"], "period": r["period"], "gross_earnings": str(r["gross_earnings"]),
              "tds": str(r["tds"]), "net_pay": str(r["net_pay"])} for r in rows],
            sort_keys=True, separators=(",", ":")).encode("utf-8")
        doc_id = det_id(tid, "prior_payroll_source_document", fy)
        name = f"legacy-payroll-history-{fy}.json"
        staging.stg_path(f"prior_payroll_source_{tid}_{fy}.json").write_bytes(content)
        docs.append({"id": doc_id, "tenant_id": tid, "employee_id": None, "kind": "EXPORT", "file_name": name,
                     "content_type": "application/json", "size_bytes": len(content),
                     "blob_container": "documents", "blob_path": f"{tid}/migration/prior-payroll/{name}",
                     "checksum_sha256": hashlib.sha256(content).hexdigest(), **AUDIT})
        log_id = det_id(tid, "prior_payroll_import_log", fy)
        logs.append({"id": log_id, "tenant_id": tid, "source_document_id": doc_id, "error_document_id": None,
                     "financial_year": fy, "status": "COMPLETED", "is_dry_run": False,
                     "rows_total": len(rows), "rows_imported": len(rows), "rows_failed": 0, **AUDIT})
        for r in rows:
            months.append({"id": det_id(tid, "prior_payroll_month", f"{r['employee_id']}:{r['period']}"),
                           "tenant_id": tid, "employee_id": r["employee_id"], "period": r["period"],
                           "gross_earnings": r["gross_earnings"], "epf_employee": ZERO, "esi_employee": ZERO,
                           "professional_tax": ZERO, "tds": r["tds"], "net_pay": r["net_pay"],
                           "import_id": log_id, **AUDIT})
    db.upsert(cur, "core.document", docs)
    db.upsert(cur, "payroll.prior_payroll_import_log", logs)
    db.upsert(cur, "payroll.prior_payroll_month", months, conflict=("tenant_id", "employee_id", "period"),
              keep_on_update=("id",))
    return len(docs) + len(logs) + len(months)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
