"""W-70 Task 1 — reconciliation: are the migrated numbers the legacy numbers?

Per tenant, after every W-68 loader has run:

  counts            row counts of the 1:1 tables (COUNTED_TABLES): legacy staging rows of the
                    tenant minus the rows the loader quarantined or dropped by rule, against
                    the target rows the migration wrote (created_by = 'migration', so V175
                    reimbursements, trigger-seeded roles and app rows never count)
  pay               every legacy employee_payruns / off-cycle line against its
                    payroll.employee_payrun row (id-map keys of load_payruns): gross_earnings,
                    total_deductions, net_pay to the paisa; run headers against their lines;
                    payroll.prior_payroll_month against the loader's rule
  CTC               every migrated payroll.ctc_structure: load_salary's CTC identity (enabled,
                    non-variable, monthly earning lines + EPF_EMPLOYER + ESI_EMPLOYER) recomputed
                    from the TARGET rows against the same identity over the legacy lines, and the
                    header against legacy annualCtc / monthlySalary
  leave             every core.leave_allocation: entitlement + accrued + carried forward - consumed
                    (the app's balance, LeaveBalanceServiceImpl.java:109-110) against the legacy
                    remaining days
  tax               employee_inv_tax_summary.tax_to_be_paid against the legacy row; active
                    employee_tds rows per employee against legacy (current financial year)

A difference is a finding, never a correction (W-67 R4). A legacy row the loader quarantined
or dropped by its own rule is an expected miss, listed with expected=true, and never makes a
tenant unclean. Comparisons that only restate a legacy inconsistency (the target holds no copy
of the compared value) go to legacy_findings and do not make a tenant unclean either.

Output, per tenant: staging/reconciliation_<tenant>_<timestamp>.json (ids and employee numbers
only - no names, emails, PAN or bank numbers; it is uploaded with the rehearsal results) and a
human table in the log. The ledger gets one step_name 'reconcile' row per tenant, DONE when the
reconciliation ran (a variance is reported in the JSON, not as a ledger failure). run() returns
True only when every tenant is clean; the process exits 0 / 1.
"""
from __future__ import annotations

import json
import logging
import sys
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal

from common import db, staging
from common.ids import det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, truthy
from common.money import money_or_zero, to_decimal, to_money
from common.tenants import Tenant, Tenants
from loaders.load_leave import SEEDED
from loaders.load_payruns import _parse_period
from loaders.load_salary import _EPF_CODE, _ESI_CODE, _IN_CTC, _frequency, _per_month
from loaders.load_tax import current_fy

STEP = "reconcile"
log = logging.getLogger(STEP)
ZERO = Decimal("0.00")
ONE_PAISA = Decimal("0.01")

# The 1:1 tables whose row counts are reconciled (W-70 Task 1 "Row counts"; the list W-68 Task 4
# shares). target table -> (loader step, legacy tables the loader quarantines rows of).
COUNTED_TABLES = {
    "core.employee": ("load_employees", ("employee",)),
    "core.department": ("load_org", ("department",)),
    "core.designation": ("load_org", ("designation",)),
    "core.work_location": ("load_org", ("workLocations",)),
    "payroll.payrun": ("load_payruns", ("payruns", "off_cycle_pay_run")),
    "payroll.employee_payrun": ("load_payruns", ("employee_payruns", "off_cycle_payrun_employee")),
    "payroll.prior_payroll_month": ("load_payruns", ()),
    "payroll.ctc_structure": ("load_salary", ("ctc_structure",)),
    "payroll.employee_investment_declaration": ("load_tax", ("employee_investment_declaration",)),
    "payroll.employee_tds": ("load_tax", ("employee_tds",)),
    "core.leave_allocation": ("load_leave", ("leave_balances", "employee_leave_allocation")),
    "core.leave_request": ("load_leave", ("leave_requests",)),
    "core.attendance": ("load_attendance", ("attendance",)),
    "hrms.timesheet": ("load_timesheets", ("timesheets",)),
    "payroll.employee_reimbursement_request": ("load_claims", ("employee_reimbursement_request",)),
    "payroll.employee_deduction": ("load_claims", ("employee_deduction",)),
    "core.notification": ("load_notifications", ("notifications", "timesheet_notifications")),
}

# Quarantine reasons that describe a row the loader still LOADED (a note, a defaulted field, a
# child line not written). Every other reason means the row was not loaded. An unknown new
# reason is therefore read as "dropped": if the loader in fact loaded the row, the count shows it.
INFORMATIONAL = {
    "NO_APPROVAL_DEFINITION",            # load_leave / load_attendance: loaded without approval history
    "NEGATIVE_LINE_AMOUNT",              # load_payruns: the synthetic line is skipped, the row loads
    "PRIOR_MONTH_TOTALS_INCONSISTENT",   # load_payruns: only the prior month is skipped
    "SECOND_FILING_ADDRESS",             # load_org: loaded as not filing
    "PAYMENT_MODE_UNMAPPED", "EMPLOYMENT_TYPE_UNMAPPED", "NO_TARGET_KIND_APPROVER_3", "SIGNATORY_NOT_FOUND",
    "MANAGER_NOT_FOUND", "ASSIGNEE_NOT_FOUND", "PAN_DROPPED_NOT_10_CHARS", "FBP_DATE_UNREADABLE",
    "ACCRUAL_FREQUENCY_UNMAPPED", "EXCEED_MODE_UNMAPPED", "LOP_POLICY_KEPT_FROM_PAY_SCHEDULE",
    "NO_WORKING_DAYS", "PAY_DAY_OVER_28", "POI_DUE_BEFORE_OPEN",
}

INPUTS = [
    "stg_merged_employees.json", "stg_payroll_department.json", "stg_payroll_designation.json",
    "stg_payroll_workLocations.json", "stg_payroll_payruns.json", "stg_payroll_employee_payruns.json",
    "stg_payroll_off_cycle_pay_run.json", "stg_payroll_off_cycle_payrun_employee.json",
    "stg_payroll_off_cycle_payrun_employee_earnings.json",
    "stg_payroll_off_cycle_payrun_employee_deductions.json", "stg_payroll_ctc_structure.json",
    "stg_payroll_earnings.json", "stg_payroll_employee_earnings.json",
    "stg_payroll_employee_variable_earnings.json", "stg_payroll_ctc_epf_components.json",
    "stg_payroll_ctc_esi_components.json", "stg_payroll_employee_investment_declaration.json",
    "stg_payroll_employee_inv_tax_summary.json", "stg_payroll_employee_tds.json",
    "stg_hrms_leave_balances.json", "stg_hrms_leave_requests.json",
    "stg_payroll_employee_leave_allocation.json", "stg_hrms_attendance.json", "stg_hrms_timesheets.json",
    "stg_payroll_employee_reimbursement_request.json", "stg_payroll_employee_deduction.json",
    "stg_hrms_notifications.json", "stg_hrms_timesheet_notifications.json",
]


def _s(v) -> str:
    return "" if v is None else str(v)


def _fmt(v) -> str:
    """Money / days as text: two places when that is exact, else every digit (a sub-paisa
    variance is never rounded away)."""
    q = v.quantize(ONE_PAISA)
    return str(q) if q == v else str(v)


def _days(v) -> Decimal:
    d = to_decimal(v)
    return d if d is not None else ZERO


class Dropped:
    """Every stg_quarantined_<step>.json: (step, legacy_table, legacy_id) -> reason, for the
    reasons that mean "not loaded"."""

    def __init__(self):
        self.reasons: dict[tuple[str, str, str], str] = {}
        for path in sorted(staging.staging_dir().glob("stg_quarantined_*.json")):
            step = path.stem[len("stg_quarantined_"):]
            with open(path, encoding="utf-8") as f:
                items = json.load(f)
            for i in items if isinstance(items, list) else []:
                if i.get("legacy_id") is None or i.get("reason") in INFORMATIONAL:
                    continue
                self.reasons.setdefault((step, _s(i.get("legacy_table")), _s(i.get("legacy_id"))), i.get("reason"))

    def reason(self, step: str, table: str, legacy_id) -> str | None:
        return self.reasons.get((step, table, _s(legacy_id)))


class Legacy:
    """The staging files, read once, with the tenant of each legacy row resolved the way the
    loader resolves it."""

    def __init__(self, tenants: Tenants, idmap: IdMap):
        self.tenants, self.idmap = tenants, idmap
        self.founder = tenants.founder.tenant_id if tenants.founder else None
        ld = staging.load
        self.org_by_code = {str(o.get("organizationId")): o.get("id")
                            for o in ld("stg_payroll_organization.json") if o.get("organizationId")}
        self.merged = ld("stg_merged_employees.json")
        self.departments = ld("stg_payroll_department.json")
        self.designations = ld("stg_payroll_designation.json")
        self.locations = ld("stg_payroll_workLocations.json")
        self.payruns = {_s(p.get("id")): p for p in ld("stg_payroll_payruns.json")}
        self.lines = ld("stg_payroll_employee_payruns.json")
        self.oc_runs = {_s(r.get("id")): r for r in ld("stg_payroll_off_cycle_pay_run.json")}
        self.oc_emps = ld("stg_payroll_off_cycle_payrun_employee.json")
        self.oc_earn, self.oc_ded = defaultdict(lambda: ZERO), defaultdict(lambda: ZERO)
        for e in ld("stg_payroll_off_cycle_payrun_employee_earnings.json"):
            self.oc_earn[_s(e.get("off_cycle_employee_id"))] += money_or_zero(e.get("amount"))
        for d in ld("stg_payroll_off_cycle_payrun_employee_deductions.json"):
            self.oc_ded[_s(d.get("off_cycle_employee_id"))] += money_or_zero(d.get("amount"))
        self.ctc = ld("stg_payroll_ctc_structure.json")
        self.earning_variable = {_s(e.get("id")): bool(truthy(e.get("isVariable"), False))
                                 for e in ld("stg_payroll_earnings.json")}
        self.ctc_lines = defaultdict(list)
        for e in ld("stg_payroll_employee_earnings.json"):
            self.ctc_lines[_s(e.get("ctc_structure_id"))].append(e)
        self.variable_lines = ld("stg_payroll_employee_variable_earnings.json")
        self.epf = defaultdict(list)
        for s in ld("stg_payroll_ctc_epf_components.json"):
            self.epf[_s(s.get("ctc_structure_id"))].append(s)
        self.esi = defaultdict(list)
        for s in ld("stg_payroll_ctc_esi_components.json"):
            self.esi[_s(s.get("ctc_structure_id"))].append(s)
        self.decls = {_s(d.get("id")): d for d in ld("stg_payroll_employee_investment_declaration.json")}
        self.summaries = ld("stg_payroll_employee_inv_tax_summary.json")
        self.tds = ld("stg_payroll_employee_tds.json")
        self.h_balances = ld("stg_hrms_leave_balances.json")
        self.h_requests = ld("stg_hrms_leave_requests.json")
        self.p_allocations = ld("stg_payroll_employee_leave_allocation.json")
        self.attendance = ld("stg_hrms_attendance.json")
        self.timesheets = ld("stg_hrms_timesheets.json")
        self.claims = ld("stg_payroll_employee_reimbursement_request.json")
        self.deductions = ld("stg_payroll_employee_deduction.json")
        self.notifications = {"notifications": ld("stg_hrms_notifications.json"),
                              "timesheet_notifications": ld("stg_hrms_timesheet_notifications.json")}

    # ---- tenant of a legacy row, as each loader decides it
    def org_tenant(self, org) -> str | None:
        t = self.tenants.for_org(org)
        return t.tenant_id if t else None

    def tds_tenant(self, r) -> str | None:
        """load_tax._tds: organization_id holds the org code, or on older rows the id itself."""
        ref = r.get("organization_id")
        return self.org_tenant(self.org_by_code.get(str(ref), ref))

    def employee_tenant_by_uuid(self, ref) -> str | None:
        """load_claims.place / load_leave.pay_emp: the Payroll employee UUID's tenant."""
        eid = self.idmap.get("payroll.employee.uuid", ref)
        return self.idmap.get("employee.tenant", eid) if eid else None

    def line_tenant(self, line) -> str | None:
        run = self.payruns.get(_s(line.get("payrun_ref_id")))
        return self.org_tenant(run.get("organizationId")) if run else None

    def oc_tenant(self, e) -> str | None:
        run = self.oc_runs.get(_s(e.get("payroll_run_id")))
        return self.org_tenant(run.get("organizationId")) if run else None

    def payroll_employee(self, ref) -> str | None:
        return self.idmap.get("payroll.employee", ref)


# ---------------------------------------------------------------- per-tenant checks

class Check:
    """One tenant's reconciliation: counts, totals, variances, legacy findings."""

    def __init__(self, cur, t: Tenant, lg: Legacy, dropped: Dropped):
        self.cur, self.t, self.tid, self.lg, self.dropped = cur, t, t.tenant_id, lg, dropped
        self.idmap = lg.idmap
        self.counts: list[dict] = []
        self.variances: list[dict] = []
        self.findings: list[dict] = []
        self.totals: dict = {}
        cur.execute("SELECT id::text, employee_number FROM core.employee WHERE tenant_id = %s", (self.tid,))
        self.emp_number = dict(cur.fetchall())

    # ---- helpers
    def number(self, employee_id, fallback=None) -> str | None:
        return self.emp_number.get(employee_id) or fallback

    def variance(self, reason: str, expected: bool = False, **kw) -> None:
        self.variances.append({"reason": reason, "expected": expected,
                               **{k: (_fmt(v) if isinstance(v, Decimal) else v) for k, v in kw.items()}})

    def kept(self, step: str, table: str, rows, tenant_of, rule=lambda r: True) -> list[dict]:
        """Legacy rows of this tenant the loader should have loaded: not quarantined, not dropped
        by its rule."""
        return [r for r in rows if tenant_of(r) == self.tid and rule(r)
                and self.dropped.reason(step, table, r.get("id")) is None]

    def actual(self, table: str) -> int:
        self.cur.execute(db.on_table("SELECT count(*) FROM {} WHERE tenant_id = %s AND created_by = %s", table),
                         (self.tid, db.AUDIT_USER))
        return self.cur.fetchone()[0]

    def count(self, table: str, expected: int) -> None:
        actual = self.actual(table)
        self.counts.append({"table": table, "expected": expected, "actual": actual, "delta": actual - expected})

    # ---- 1. row counts
    def row_counts(self) -> None:
        lg, tid, founder = self.lg, self.tid, self.tid == self.lg.founder
        org = lambda r: lg.org_tenant(r.get("organizationId"))  # noqa: E731
        hrms = lambda r: tid if founder else None  # noqa: E731  HRMS rows: founder tenant (W-67 R5)

        employees = [m for m in lg.merged if m.get("tenant_id") == tid and self.dropped.reason(
            "load_employees", "employee", m.get("source_legacy_payroll_id") or m.get("source_legacy_hrms_id")) is None]
        self.count("core.employee", len(employees))
        for table, legacy, rows, name_key, id_key, hrms_name in (
                ("core.department", "department", lg.departments, "name", "department_legacy_id", "department_name"),
                ("core.designation", "designation", lg.designations, "name", "designation_legacy_id", "job_title")):
            kept = self.kept("load_org", legacy, rows, org, lambda r: text(r.get(name_key)))
            self.count(table, len(kept) + self._created_by_name(rows, kept, employees, id_key, hrms_name))
        self.count("core.work_location", len(self.kept("load_org", "workLocations", lg.locations, org,
                                                        lambda r: text(r.get("workLocationName")))))
        self.count("payroll.payrun",
                   len(self.kept("load_payruns", "payruns", lg.payruns.values(), org))
                   + len(self.kept("load_payruns", "off_cycle_pay_run", lg.oc_runs.values(), org)))
        self.count("payroll.employee_payrun",
                   len(self.kept("load_payruns", "employee_payruns", lg.lines, lg.line_tenant))
                   + len(self.kept("load_payruns", "off_cycle_payrun_employee", lg.oc_emps, lg.oc_tenant)))
        # A prior month is written only for a period with no migrated REGULAR run, and every prior
        # month comes from a migrated REGULAR run (load_payruns.py:259, 359-360): by the loader's rule none.
        self.count("payroll.prior_payroll_month", 0)
        # load_salary carries only the active, not deleted structure (load_salary.py:311-312).
        self.count("payroll.ctc_structure", len(self.kept(
            "load_salary", "ctc_structure", lg.ctc, org,
            lambda c: truthy(c.get("is_active"), False) and not truthy(c.get("is_deleted"), False))))
        self.count("payroll.employee_investment_declaration",
                   len(self.kept("load_tax", "employee_investment_declaration", lg.decls.values(), org)))
        self.count("payroll.employee_tds", len(self.kept(
            "load_tax", "employee_tds", lg.tds, lg.tds_tenant)))
        self.count("core.leave_allocation", len(self.expected_allocations()))
        self.count("core.leave_request", len(self.kept("load_leave", "leave_requests", lg.h_requests, hrms)))
        self.count("core.attendance", len(self.kept("load_attendance", "attendance", lg.attendance, hrms)))
        self.count("hrms.timesheet", len(self.kept("load_timesheets", "timesheets", lg.timesheets, hrms)))
        by_uuid = lambda r: lg.employee_tenant_by_uuid(r.get("employee_id"))  # noqa: E731
        self.count("payroll.employee_reimbursement_request",
                   len(self.kept("load_claims", "employee_reimbursement_request", lg.claims, by_uuid)))
        self.count("payroll.employee_deduction",
                   len(self.kept("load_claims", "employee_deduction", lg.deductions, by_uuid)))
        self.count("core.notification", sum(len(self.kept("load_notifications", table, rows, hrms))
                                            for table, rows in lg.notifications.items()))
        # the shared list and the counts taken stay in step
        assert [c["table"] for c in self.counts] == list(COUNTED_TABLES), "COUNTED_TABLES out of step"

    def _created_by_name(self, rows, kept, employees, id_key, name_key) -> int:
        """Departments / designations load_employees creates from an HRMS-only person's text
        (load_employees.py:51-76): a name no Payroll row of the tenant carries."""
        named_ids = {_s(r.get("id")) for r in rows if text(r.get("name")) and self.lg.org_tenant(r.get("organizationId"))}
        names = {text(r.get("name")).lower() for r in kept}
        created = set()
        for m in employees:
            if not m.get("first_name") or _s(m.get(id_key)) in named_ids:
                continue
            name = text(m.get(name_key))
            if name and name.lower() not in names:
                created.add(name.lower())
        return len(created)

    # ---- 2. pay
    def pay(self) -> None:
        lg, cur, tid = self.lg, self.cur, self.tid
        cur.execute("SELECT ep.id::text, ep.employee_id::text, p.period, ep.gross_earnings, ep.total_deductions, "
                    "ep.net_pay FROM payroll.employee_payrun ep JOIN payroll.payrun p ON p.id = ep.payrun_id "
                    "WHERE ep.tenant_id = %s AND ep.created_by = %s", (tid, db.AUDIT_USER))
        target = {r[0]: r[1:] for r in cur.fetchall()}
        legacy_tot = {"gross": ZERO, "deductions": ZERO, "net": ZERO}
        seen = set()

        def compare(table, key, legacy_id, emp_ref, gross, ded, net):
            reason = self.dropped.reason("load_payruns", table, legacy_id)
            if reason is not None:
                if reason in ("EMPLOYEE_NOT_MIGRATED", "EMPLOYEE_TENANT_MISMATCH"):
                    self.variance("NO_EMPLOYEE", expected=True, legacy_table=table, legacy_id=legacy_id,
                                  legacy_employee_id=emp_ref, quarantine_reason=reason)
                return
            for k, v in (("gross", gross), ("deductions", ded), ("net", net)):
                legacy_tot[k] += v
            eid = lg.payroll_employee(emp_ref)
            tgt_id = self.idmap.get(key, legacy_id)
            if eid is None:
                self.variance("NO_EMPLOYEE", legacy_table=table, legacy_id=legacy_id, legacy_employee_id=emp_ref)
                return
            if tgt_id is None or tgt_id not in target:
                self.variance("PAY_MISSING", employee_number=self.number(eid), legacy_table=table, legacy_id=legacy_id)
                return
            seen.add(tgt_id)
            _emp, period, t_gross, t_ded, t_net = target[tgt_id]
            for field, lv, tv in (("gross_earnings", gross, t_gross), ("total_deductions", ded, t_ded),
                                  ("net_pay", net, t_net)):
                if lv != tv:
                    self.variance("PAY_MISMATCH", employee_number=self.number(eid), period=period, field=field,
                                  legacy=lv, target=tv, legacy_table=table, legacy_id=legacy_id)

        # Same legacy -> target formula as load_payruns.py:236-240 and :312-318.
        for line in lg.lines:
            if lg.line_tenant(line) == tid:
                compare("employee_payruns", "payroll.employee_payruns", line.get("id"), line.get("employee_ref_id"),
                        money_or_zero(line.get("totalEarnings")), money_or_zero(line.get("totalDeductions")),
                        money_or_zero(line.get("netPay")))
        for e in lg.oc_emps:
            if lg.oc_tenant(e) == tid:
                g, d = lg.oc_earn[_s(e.get("id"))], lg.oc_ded[_s(e.get("id"))]
                compare("off_cycle_payrun_employee", "payroll.off_cycle_payrun_employee", e.get("id"),
                        e.get("employee_id"), g, d, g - d)
        # a migrated row no expected legacy line reaches
        for tgt_id in sorted(t for t in target if t not in seen):
            emp, period = target[tgt_id][0], target[tgt_id][1]
            self.variance("PAY_UNEXPECTED_ROW", employee_number=self.number(emp), period=period)

        tgt_tot = {"gross": sum((r[2] for r in target.values()), ZERO),
                   "deductions": sum((r[3] for r in target.values()), ZERO),
                   "net": sum((r[4] for r in target.values()), ZERO)}
        self.totals = {k: {"legacy": _fmt(legacy_tot[k]), "target": _fmt(tgt_tot[k]),
                           "delta": _fmt(tgt_tot[k] - legacy_tot[k])} for k in legacy_tot}

        # Headers are the sum of their loaded lines (load_payruns.py:411-414).
        cur.execute("SELECT p.id::text, p.period, p.run_type, p.total_gross, p.total_deductions, p.total_net_pay, "
                    "p.included_count, count(ep.id), coalesce(sum(ep.gross_earnings), 0), "
                    "coalesce(sum(ep.total_deductions), 0), coalesce(sum(ep.net_pay), 0) "
                    "FROM payroll.payrun p LEFT JOIN payroll.employee_payrun ep ON ep.payrun_id = p.id "
                    "WHERE p.tenant_id = %s AND p.created_by = %s GROUP BY p.id ORDER BY p.period, p.id",
                    (tid, db.AUDIT_USER))
        headers = []
        for pid, period, rtype, hg, hd, hn, inc, n, lg_, ld_, ln_ in cur.fetchall():
            if n == 0:
                continue  # no line loaded: the header keeps the legacy figures (load_payruns.py:417-418)
            if (hg, hd, hn, inc) != (lg_, ld_, ln_, n):
                headers.append({"payrun_id": pid, "period": period, "run_type": rtype,
                                "header": [_fmt(hg), _fmt(hd), _fmt(hn), inc],
                                "lines": [_fmt(lg_), _fmt(ld_), _fmt(ln_), n]})
        self.totals["header_vs_lines"] = headers

    # ---- 3. CTC
    def ctc(self) -> None:
        lg, cur, tid = self.lg, self.cur, self.tid
        variable_ids = {det_id(tid, "employee_variable_earnings", v.get("id")) for v in lg.variable_lines}
        line_variable = {}
        for lines in lg.ctc_lines.values():
            for e in lines:
                line_variable[det_id(tid, "employee_earnings", e.get("id"))] = bool(truthy(e.get("isVariable"), False))
        last_pay = self._last_payrun_salary()
        by_target = {}
        for c in lg.ctc:
            sid = self.idmap.get("payroll.ctc_structure", c.get("id"))
            if sid:
                by_target[sid] = c

        cur.execute("SELECT id::text, employee_id::text, annual_ctc, monthly_ctc FROM payroll.ctc_structure "
                    "WHERE tenant_id = %s AND created_by = %s", (tid, db.AUDIT_USER))
        for sid, emp, annual, monthly in sorted(cur.fetchall()):
            c = by_target.get(sid)
            num = self.number(emp)
            if c is None:
                self.variance("CTC_MISMATCH", employee_number=num, field="legacy_structure", legacy=None, target=sid)
                continue
            cur.execute("SELECT ee.id::text, ee.monthly_amount, ee.is_enabled, ee.earning_frequency, e.is_variable "
                        "FROM payroll.employee_earning ee JOIN payroll.earning e ON e.id = ee.component_id "
                        "WHERE ee.tenant_id = %s AND ee.ctc_structure_id = %s", (tid, sid))
            t_sum = ZERO
            for lid, amount, enabled, freq, variable in cur.fetchall():
                if enabled and not variable and freq in (None, "MONTHLY") and lid not in variable_ids \
                        and not line_variable.get(lid, False):
                    t_sum += amount
            for table, code in (("payroll.ctc_epf_component", "EPF_EMPLOYER"),
                                ("payroll.ctc_esi_component", "ESI_EMPLOYER")):
                cur.execute(f"SELECT coalesce(sum(monthly_amount), 0) FROM {table} WHERE tenant_id = %s "
                            "AND ctc_structure_id = %s AND component_code = %s", (tid, sid, code))
                t_sum += cur.fetchone()[0]
            l_sum = self._legacy_ctc_sum(c)
            l_annual = to_money(c.get("annualCtc"))
            l_monthly = to_money(c.get("monthlySalary"))
            if l_monthly is None and l_annual is not None:
                l_monthly = _per_month(l_annual)  # load_salary.py:546-548
            for field, lv, tv in (("monthly_identity", l_sum, t_sum), ("monthly_ctc", l_monthly, monthly),
                                  ("annual_ctc", l_annual, annual)):
                if lv != tv:
                    self.variance("CTC_MISMATCH", employee_number=num, field=field, legacy=lv, target=tv,
                                  legacy_id=c.get("id"))
            if l_monthly is not None and l_sum != l_monthly:
                # load_salary accepts one paisa (load_salary.py:704-706): the legacy lines themselves
                self.findings.append({"reason": "CTC_IDENTITY_ROUNDING", "employee_number": num,
                                      "monthly_salary": _fmt(l_monthly), "monthly_lines": _fmt(l_sum)})
            last = last_pay.get(_s(c.get("employee_id")))
            if last is not None and l_monthly is not None and last[1] != l_monthly:
                self.findings.append({"reason": "CTC_VS_LAST_PAYRUN", "employee_number": num, "period": last[0],
                                      "ctc_monthly_salary": _fmt(l_monthly), "payrun_monthly_salary": _fmt(last[1])})

    def _legacy_ctc_sum(self, c) -> Decimal:
        """load_salary's identity over the legacy lines (load_salary.py:567-582, 670-697)."""
        lid = _s(c.get("id"))
        total = ZERO
        for e in self.lg.ctc_lines.get(lid, []):
            _freq, per_year = _frequency(e.get("earningFrequency"))
            amount = to_money(e.get("amount"))
            if amount is None:
                amount = to_money(e.get("overrideAmount")) or ZERO
            monthly = amount if per_year == 12 else _per_month((amount * per_year).quantize(ONE_PAISA))
            variable = bool(truthy(e.get("isVariable"), False)) or self.lg.earning_variable.get(_s(e.get("earning_id")))
            if truthy(e.get("enabled"), True) and not variable and per_year == 12:
                total += monthly
        for lines, codes in ((self.lg.epf.get(lid, []), _EPF_CODE), (self.lg.esi.get(lid, []), _ESI_CODE)):
            for s in lines:
                if codes.get((text(s.get("componentCode")) or "").upper()) in _IN_CTC:
                    total += to_money(s.get("monthlyAmount")) or ZERO
        return total.quantize(ONE_PAISA)

    def _last_payrun_salary(self) -> dict[str, tuple[str, Decimal]]:
        """Legacy employee id -> (period, monthlySalary) of the employee's latest migrated pay run
        line (a line of a run the loader did not carry says nothing about the migrated history)."""
        out = {}
        for line in self.lg.lines:
            run = self.lg.payruns.get(_s(line.get("payrun_ref_id")))
            ms = to_money(line.get("monthlySalary"))
            if run is None or ms is None or self.lg.line_tenant(line) != self.tid \
                    or self.dropped.reason("load_payruns", "employee_payruns", line.get("id")) is not None:
                continue
            key = (_parse_period(run) or "", int(line.get("id") or 0))  # load_payruns' own period
            emp = _s(line.get("employee_ref_id"))
            if emp not in out or key > out[emp][0]:
                out[emp] = (key, ms)
        return {k: (v[0][0], v[1]) for k, v in out.items()}

    # ---- 4. leave
    def expected_allocations(self) -> dict[str, tuple[Decimal, str | None, str, object]]:
        """target allocation id -> (legacy remaining days, employee id, legacy table, legacy id)."""
        lg, tid, out = self.lg, self.tid, {}
        if tid == lg.founder:
            # HRMS leave_balances: five columns, a column with nothing allotted or used is not
            # carried (load_leave.py:361-375).
            for b in self.kept("load_leave", "leave_balances", lg.h_balances, lambda r: tid):
                ref = text(b.get("employee_id"))
                eid = self.idmap.get("hrms.emp_code", ref) or self.idmap.get("hrms.employee", ref)
                for code, _n, col, _d in SEEDED:
                    allotted, used = _days(b.get(f"{col}_allotted")), _days(b.get(f"{col}_used"))
                    if not allotted and not used:
                        continue
                    out[det_id(tid, f"leave_balances.{code}", b.get("id"))] = (allotted - used, eid, "leave_balances",
                                                                               b.get("id"))
        else:
            # Payroll employee_leave_allocation: annual + carried forward - consumed (W-70 Task 1).
            # The founder's Payroll leave is not carried (W-67 R2, load_leave.py:461-468).
            for a in self.kept("load_leave", "employee_leave_allocation", lg.p_allocations,
                               lambda r: lg.employee_tenant_by_uuid(r.get("employee_id"))):
                eid = self.idmap.get("payroll.employee.uuid", a.get("employee_id"))
                remaining = _days(a.get("annual_days")) + _days(a.get("carried_forward_days")) \
                    - _days(a.get("consumed_days"))
                out[det_id(tid, "employee_leave_allocation", a.get("id"))] = (
                    remaining, eid, "employee_leave_allocation", a.get("id"))
        return out

    def leave(self) -> None:
        cur, tid = self.cur, self.tid
        cur.execute("SELECT a.id::text, a.employee_id::text, a.leave_year, a.entitlement_days + a.accrued_days "
                    "+ a.carried_forward_days - coalesce((SELECT sum(c.consumed_days) FROM core.leave_consumption c "
                    "WHERE c.tenant_id = a.tenant_id AND c.allocation_id = a.id), 0) "
                    "FROM core.leave_allocation a WHERE a.tenant_id = %s AND a.created_by = %s", (tid, db.AUDIT_USER))
        target = {r[0]: r[1:] for r in cur.fetchall()}
        for aid, (remaining, eid, table, legacy_id) in sorted(self.expected_allocations().items()):
            if aid not in target:
                self.variance("LEAVE_MISMATCH", employee_number=self.number(eid), field="allocation_missing",
                              legacy=remaining, target=None, legacy_table=table, legacy_id=legacy_id)
                continue
            emp, year, t_remaining = target.pop(aid)
            if t_remaining != remaining:
                self.variance("LEAVE_MISMATCH", employee_number=self.number(emp), leave_year=year,
                              field="remaining_days", legacy=remaining, target=t_remaining,
                              legacy_table=table, legacy_id=legacy_id)
        for aid, (emp, year, t_remaining) in sorted(target.items()):
            self.variance("LEAVE_MISMATCH", employee_number=self.number(emp), leave_year=year,
                          field="allocation_unexpected", legacy=None, target=t_remaining)

    # ---- 5. tax
    def tax(self) -> None:
        lg, cur, tid, fy = self.lg, self.cur, self.tid, current_fy()
        org = lambda r: lg.org_tenant(r.get("organizationId"))  # noqa: E731
        decls = {_s(d.get("id")): d for d in self.kept("load_tax", "employee_investment_declaration",
                                                       lg.decls.values(), org)}
        cur.execute("SELECT s.id::text, d.employee_id::text, s.regime, s.tax_to_be_paid "
                    "FROM payroll.employee_inv_tax_summary s "
                    "JOIN payroll.employee_investment_declaration d ON d.id = s.declaration_id "
                    "WHERE s.tenant_id = %s AND s.created_by = %s", (tid, db.AUDIT_USER))
        target = {r[0]: r[1:] for r in cur.fetchall()}
        # A summary loads when its declaration loaded (load_tax.py:267-273, 426-450).
        for s in self.kept("load_tax", "employee_inv_tax_summary", lg.summaries,
                           lambda r: tid if _s(r.get("declaration_id")) in decls else None):
            eid = lg.payroll_employee(decls[_s(s.get("declaration_id"))].get("employee_id"))
            sid = det_id(tid, "employee_inv_tax_summary", s.get("id"))
            legacy = to_money(s.get("tax_to_be_paid"))
            if sid not in target:
                self.variance("TAX_MISMATCH", employee_number=self.number(eid), field="tax_summary_missing",
                              legacy=legacy, target=None, legacy_id=s.get("id"))
                continue
            _emp, regime, t_value = target.pop(sid)
            if legacy != t_value:
                self.variance("TAX_MISMATCH", employee_number=self.number(eid), regime=regime,
                              field="tax_to_be_paid", legacy=legacy, target=t_value, legacy_id=s.get("id"))
        for sid, (emp, regime, t_value) in sorted(target.items()):
            self.variance("TAX_MISMATCH", employee_number=self.number(emp), regime=regime,
                          field="tax_summary_unexpected", legacy=None, target=t_value)

        # Active TDS rows: load_tax keeps one active row per employee per FY, the newest active
        # legacy row (load_tax.py:707-725). Legacy: an employee with any active current-FY row.
        legacy_active = defaultdict(int)
        for r in self.kept("load_tax", "employee_tds", lg.tds, lg.tds_tenant):
            if r.get("fiscal_year") is None or int(r["fiscal_year"]) != fy.end:
                continue
            eid = self.idmap.get("payroll.employee.uuid", r.get("employee_id")) or \
                lg.payroll_employee(r.get("employee_id"))
            legacy_active[eid] = max(legacy_active[eid], 1 if truthy(r.get("is_active"), True) else 0)
        cur.execute("SELECT employee_id::text, count(*) FILTER (WHERE is_active) FROM payroll.employee_tds "
                    "WHERE tenant_id = %s AND created_by = %s AND financial_year = %s GROUP BY employee_id",
                    (tid, db.AUDIT_USER, fy.label))
        target_active = dict(cur.fetchall())
        for eid in sorted(set(legacy_active) | set(target_active), key=_s):
            lv, tv = legacy_active.get(eid, 0), target_active.get(eid, 0)
            if lv != tv:
                self.variance("TAX_MISMATCH", employee_number=self.number(eid), field="active_tds_rows",
                              legacy=lv, target=tv)

    # ---- result
    def result(self) -> dict:
        lop = staging.stg_path(f"lop_summary_{self.tid}.json")
        lop_entries = None
        if lop.exists():
            with open(lop, encoding="utf-8") as f:
                lop_entries = json.load(f).get("logged_total")
        clean = (all(c["delta"] == 0 for c in self.counts)
                 and all(Decimal(v["delta"]) == ZERO for k, v in self.totals.items() if k != "header_vs_lines")
                 and not self.totals.get("header_vs_lines")
                 and not any(not v["expected"] for v in self.variances))
        return {"tenant_id": self.tid, "reconciled_at": datetime.now(timezone.utc).isoformat(),
                "counts": self.counts, "totals": self.totals, "per_employee_variances": self.variances,
                "legacy_findings": self.findings, "lop_entries": lop_entries, "clean": clean}


def _log_table(r: dict) -> None:
    log.info("=" * 78)
    log.info("RECONCILIATION tenant=%s  %s", r["tenant_id"], "CLEAN" if r["clean"] else "VARIANCE")
    log.info("%-42s %10s %10s %8s", "table", "expected", "actual", "delta")
    for c in r["counts"]:
        log.info("%-42s %10d %10d %8d", c["table"], c["expected"], c["actual"], c["delta"])
    for k in ("gross", "deductions", "net"):
        t = r["totals"].get(k, {})
        log.info("%-42s %18s %18s %10s", f"pay {k}", t.get("legacy"), t.get("target"), t.get("delta"))
    real = [v for v in r["per_employee_variances"] if not v["expected"]]
    log.info("variances %d (expected misses %d), header mismatches %d, legacy findings %d, lop entries %s",
             len(real), len(r["per_employee_variances"]) - len(real), len(r["totals"].get("header_vs_lines", [])),
             len(r["legacy_findings"]), r["lop_entries"])
    for v in real:
        log.info("  %s %s", v["reason"], {k: x for k, x in v.items() if k not in ("reason", "expected")})
    log.info("=" * 78)


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    lg = Legacy(tenants, idmap)
    dropped = Dropped()
    results: dict[str, dict] = {}

    def work(cur, t: Tenant) -> StepResult:
        check = Check(cur, t, lg, dropped)
        check.row_counts()
        check.pay()
        check.ctc()
        check.leave()
        check.tax()
        r = check.result()
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        staging.write(f"reconciliation_{t.tenant_id}_{stamp}.json", r)
        _log_table(r)
        results[t.tenant_id] = r
        return StepResult(processed=sum(c["actual"] for c in r["counts"]),
                          skipped=sum(1 for v in r["per_employee_variances"] if not v["expected"]))

    own = conn is None
    conn = conn or db.connect()
    try:
        # force: a reconciliation always re-reads the target, even when staging did not change
        summary = run_per_tenant(conn, STEP, tenants.all(), staging.checksum(INPUTS), work, force=True)
    finally:
        if own:
            conn.close()
    return summary.ok and bool(results) and all(r["clean"] for r in results.values())


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
