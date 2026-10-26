"""3i — leave, holidays and attendance preferences (W-68 Task 3, W-67 R2, mapping §3.8, X-01..X-04).

Founder tenant, from HRMS (the founder's leave authority; HRMS has no organisation column):
  leave_types        -> core.leave_type + core.leave_policy. Five codes are always seeded for the
                        fixed balance columns (ANNUAL, SICK, CASUAL, MATERNITY, PATERNITY) plus
                        LEGACY; an HRMS type whose name is one of them fills that code, any other
                        HRMS type gets its own code.
  employee_leave_balances -> one core.leave_allocation per employee and leave type in the leave
                        year of the run date, entitlement = the remaining days HRMS shows; a
                        negative balance is entitlement 0 plus one core.leave_consumption
                        (founder decision 2026-10-26: prod leave_balances.*_used is 0 in every
                        row, the HRMS screens read employee_leave_balances). Migration-made
                        allocations not in this set are removed.
  leave_balances     -> used only when employee_leave_balances is empty: one row, five fixed
                        columns -> up to five core.leave_allocation rows; used days ->
                        core.leave_consumption (one row per type).
  leave_requests     -> core.leave_request under the tenant's LEGACY type (the table has no type
                        column, X-01; never "any active type"). hr_status wins over
                        reporting_manager_status. Each request gets one core.approval_instance
                        and two core.approval_step rows (manager, then HR) so who decided stays visible.
  holidays           -> core.holiday in the tenant's default core.holiday_calendar.

Every other tenant, from Payroll:
  leave_type (+ leave_type_genders / _departments / _designations / _work_locations)
                     -> core.leave_type + core.leave_policy + core.leave_policy_eligibility
  employee_leave_allocation          -> core.leave_allocation
  employee_leave_balance_consumption -> core.leave_consumption
  holidays (+ holiday_locations)     -> core.holiday; a holiday limited to work locations goes into
                        a calendar per location (core.holiday_calendar_location), which also
                        holds the tenant-wide holidays, because the app reads one calendar per
                        location and falls back to the default calendar.

Every tenant, from Payroll:
  attendance_preferences -> hrms.attendance_preference. The can_include_*_for_pay flags are not
                        written to core.lop_policy: load_org owns that row (effective 1900-01-01,
                        from paySchedule), and legacy never read either pair of flags. A difference
                        is reported in quarantine as LOP_POLICY_KEPT_FROM_PAY_SCHEDULE.

Leave year: legacy years are calendar years. Legacy year Y becomes the tenant leave year that
starts in Y (core.tenant.leave_year_start_month, April by default): April Y - March Y+1, label
"Y-YY"; a tenant starting in January keeps "Y".
"""
from __future__ import annotations

import logging
import re
import sys
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta, timezone

from common import db, staging
from common.ids import child_id, det_id, slug_code, unique_codes
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp, truthy
from common.money import to_decimal
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_leave"
HRMS_INPUTS = ["stg_hrms_leave_types.json", "stg_hrms_leave_balances.json", "stg_hrms_leave_requests.json",
               "stg_hrms_holidays.json", "stg_hrms_employee_leave_balances.json"]
PAYROLL_INPUTS = [
    "stg_payroll_leave_type.json", "stg_payroll_leave_type_genders.json",
    "stg_payroll_leave_type_departments.json", "stg_payroll_leave_type_designations.json",
    "stg_payroll_leave_type_work_locations.json", "stg_payroll_employee_leave_allocation.json",
    "stg_payroll_employee_leave_balance_consumption.json", "stg_payroll_holidays.json",
    "stg_payroll_holiday_locations.json", "stg_payroll_attendance_preferences.json",
]
# The files the id-map entries this step reads were built from (id_map.json itself is not an input).
REF_INPUTS = ["stg_merged_employees.json", "stg_payroll_department.json", "stg_payroll_designation.json",
              "stg_payroll_workLocations.json"]
INPUTS = HRMS_INPUTS + PAYROLL_INPUTS + REF_INPUTS
log = logging.getLogger(STEP)

# HRMS leave_balances columns -> seeded code. Default annual days are the legacy entity's
# own defaults (legacy/HRMS_Backend/.../entity/LeaveBalance.java: 24, 12, 8, 90, 10), used only
# when HRMS leave_types has no type of that name.
SEEDED = (("ANNUAL", "Annual Leave", "annual", 24), ("SICK", "Sick Leave", "sick", 12),
          ("CASUAL", "Casual Leave", "casual", 8), ("MATERNITY", "Maternity Leave", "maternity", 90),
          ("PATERNITY", "Paternity Leave", "paternity", 10))
LEGACY_CODE, LEGACY_NAME = "LEGACY", "Legacy leave (type not recorded)"
CLOSED_BY_MIGRATION = "Closed at migration: legacy recorded no decision at this step"
SINCE_ALWAYS = date(1900, 1, 1)  # load_org's "no recorded start" date for core.lop_policy
REQUEST_STATUS = {"APPROVED", "REJECTED", "PENDING"}
_MONTHS = {m: i for i, m in enumerate(
    ("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"), start=1)}
_DAYS = lambda v: to_decimal(v)  # noqa: E731 - days are NUMERIC(10,2) in the target


# ---------------------------------------------------------------- helpers

def leave_year(year: int, start_month: int) -> tuple[str, date, date]:
    """Legacy calendar year -> (label, first day, last day) of the tenant leave year starting in it."""
    start = date(year, start_month, 1)
    end = date(year + 1, start_month, 1) - timedelta(days=1)
    label = str(year) if start_month == 1 else f"{year}-{(year + 1) % 100:02d}"
    return label, start, end


def current_leave_year(today: date, start_month: int) -> tuple[str, date, date]:
    """The tenant leave year containing today: where HRMS remaining balances open."""
    return leave_year(today.year if today.month >= start_month else today.year - 1, start_month)


def legacy_year(v) -> int | None:
    s = text(v)
    if not s:
        return None
    m = re.match(r"^\s*(\d{4})", s)
    return int(m.group(1)) if m else None


def period_of(leave_month) -> str | None:
    """Payroll leave_month 'September 2025' (frontend markLeaveAddEmploy.jsx:114), also 'Sep 2025',
    '2025-09' -> 'YYYY-MM'."""
    s = text(leave_month)
    if not s:
        return None
    m = re.match(r"^(\d{4})-(\d{1,2})", s)
    if m and 1 <= int(m.group(2)) <= 12:
        return f"{m.group(1)}-{int(m.group(2)):02d}"
    m = re.match(r"^([A-Za-z]+)[\s,-]+(\d{4})$", s)
    if m and m.group(1)[:3].lower() in _MONTHS:
        return f"{m.group(2)}-{_MONTHS[m.group(1)[:3].lower()]:02d}"
    return None


def hours_of(v):
    """'08:30' or '8.5' -> Decimal hours, 2 places."""
    s = text(v)
    if not s:
        return None
    m = re.match(r"^(\d{1,2}):(\d{2})", s)
    if m:
        return to_decimal(int(m.group(1)) + to_decimal(m.group(2), None) / 60)
    return to_decimal(s)


def int_or_none(v) -> int | None:
    d = to_decimal(v, None)
    return int(d) if d is not None else None


def _ts(v, fallback):
    return to_timestamp(v) or fallback


def _approval_definition(cur, tenant_id: str, flow: str) -> str | None:
    """The definition the app's ApprovalService.start would pick (the latest effective one)."""
    cur.execute("SELECT id FROM core.approval_definition WHERE tenant_id = %s AND flow_type = %s AND is_active "
                "ORDER BY effective_from DESC LIMIT 1", (tenant_id, flow))
    row = cur.fetchone()
    return str(row[0]) if row else None


def _primary_managers(cur, tenant_id: str) -> dict[str, str]:
    cur.execute("SELECT employee_id, manager_id FROM core.reporting_line WHERE tenant_id = %s AND kind = 'PRIMARY' "
                "AND effective_to IS NULL ORDER BY effective_from", (tenant_id,))
    return {str(e): str(m) for e, m in cur.fetchall()}


def _loaded_employees(cur, tenant_id: str) -> set[str]:
    cur.execute("SELECT id FROM core.employee WHERE tenant_id = %s", (tenant_id,))
    return {str(r[0]) for r in cur.fetchall()}


# ---------------------------------------------------------------- run

def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    run_start = datetime.now(timezone.utc)
    audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}

    h_types = staging.load("stg_hrms_leave_types.json")
    h_bal = staging.load("stg_hrms_leave_balances.json")
    h_emp_bal = staging.load("stg_hrms_employee_leave_balances.json")
    h_req = staging.load("stg_hrms_leave_requests.json")
    h_hol = staging.load("stg_hrms_holidays.json")
    for twin in ("stg_hrms_leave_request.json",):
        log.info("not migrated (W-67 R2, superseded twin): %s rows=%d", twin, len(staging.load(twin)))

    def by_org(name: str) -> dict[str, list[dict]]:
        out = defaultdict(list)
        for r in staging.load(name):
            out[str(r.get("organizationId"))].append(r)
        return out

    p_types = by_org("stg_payroll_leave_type.json")
    p_hol = by_org("stg_payroll_holidays.json")
    p_pref = by_org("stg_payroll_attendance_preferences.json")
    elig_rows = {}
    for dim, name, key in (("gender", "stg_payroll_leave_type_genders.json", "gender"),
                           ("department", "stg_payroll_leave_type_departments.json", "department_id"),
                           ("designation", "stg_payroll_leave_type_designations.json", "designation_id"),
                           ("work_location", "stg_payroll_leave_type_work_locations.json", "work_location_id")):
        d = defaultdict(list)
        for r in staging.load(name):
            d[str(r.get("leave_type_id"))].append(r.get(key))
        elig_rows[dim] = d
    hol_locs = defaultdict(list)
    for r in staging.load("stg_payroll_holiday_locations.json"):
        hol_locs[str(r.get("holiday_id"))].append(text(r.get("location")))
    wl_rows = staging.load("stg_payroll_workLocations.json")
    wl_by_uuid = {str(w["workLocationId"]): w.get("id") for w in wl_rows if w.get("workLocationId")}
    wl_by_name = {(str(w.get("organizationId")), (text(w.get("workLocationName")) or "").lower()): w.get("id")
                  for w in wl_rows}

    # Payroll rows of organisations that are not tenants, and the founder's Payroll leave
    live_orgs = set(tenants.by_org)
    for name, groups in (("leave_type", p_types), ("holidays", p_hol), ("attendance_preferences", p_pref)):
        for org, rows in groups.items():
            if org not in live_orgs:
                for r in rows:
                    q.add("ORG_NOT_MIGRATED", name, r.get("id"), None, organization=org)

    # Payroll allocation / consumption: tenant from the employee (organization_id there is the
    # organisation's public UUID, not its id: EmployeeLeaveAllocationServiceImpl.java:246-249).
    def pay_emp(ref) -> str | None:
        return idmap.get("payroll.employee.uuid", ref)

    p_alloc, p_cons = defaultdict(list), defaultdict(list)
    for name, target in (("stg_payroll_employee_leave_allocation.json", p_alloc),
                         ("stg_payroll_employee_leave_balance_consumption.json", p_cons)):
        legacy_table = name[len("stg_payroll_"):-len(".json")]
        for r in staging.load(name):
            eid = pay_emp(r.get("employee_id"))
            tid = idmap.get("employee.tenant", eid) if eid else None
            if not tid:
                q.add("EMPLOYEE_NOT_FOUND", legacy_table, r.get("id"))
                continue
            target[tid].append(r)

    def hrms_emp(ref, loaded: set[str]) -> str | None:
        ref = text(ref)
        if not ref:
            return None
        eid = idmap.get("hrms.emp_code", ref) or idmap.get("hrms.employee", ref)
        return eid if eid in loaded else None

    # ------------------------------------------------------------ shared writers

    def ensure_default_calendar(cur, tid: str) -> str:
        cur.execute("SELECT id FROM core.holiday_calendar WHERE tenant_id = %s AND is_default", (tid,))
        row = cur.fetchone()
        if row:
            return str(row[0])
        cal = det_id(tid, "holiday_calendar", "default")
        db.upsert(cur, "core.holiday_calendar", [{"id": cal, "tenant_id": tid, "name": "Holidays",
                                                  "is_default": True, **audit}])
        return cal

    def write_types(cur, tid: str, types: list[dict]) -> dict[str, str]:
        """Upsert core.leave_type on (tenant_id, code); return {code: stored id}."""
        db.upsert(cur, "core.leave_type", types, conflict=("tenant_id", "code"), keep_on_update=("id",))
        cur.execute("SELECT code, id FROM core.leave_type WHERE tenant_id = %s", (tid,))
        return {c: str(i) for c, i in cur.fetchall()}

    def policy_row(tid, pid, type_id, annual, effective_from, created_at, **kw):
        row = {"id": pid, "tenant_id": tid, "leave_type_id": type_id, "annual_days": annual,
               "accrual_enabled": None, "accrual_frequency": None, "accrual_units": None,
               "reset_enabled": None, "reset_frequency": None, "carry_forward_enabled": None,
               "carry_forward_cap": None, "requires_document": False, "past_booking_limit_days": None,
               "future_booking_limit_days": None, "include_weekend": None, "include_holiday": None,
               "exceed_balance_mode": "markAsLOP", "exceed_balance_limit_days": None,
               "pro_rate_enabled": None, "max_days_per_application": None, "gender": None,
               "effective_from": effective_from, "created_at": created_at, **audit}
        row.update(kw)
        return row

    def approval(tid, flow_def, subject_table, subject_id, emp, status, started, completed, steps, label):
        """One approval_instance and its steps; steps = [(kind, assignee, decision, comment, decided_at)].

        The app's queue is every step with no decision (ApprovalStepRepository.findPendingForAssigneeOrUnassigned),
        so a step legacy never decided, in a request that is already decided, is closed with the outcome,
        as the app closes the open steps of a rejected instance (ApprovalService.decide)."""
        if status != "PENDING":
            steps = [(k, a, d, c, at) if d else (k, a, status, CLOSED_BY_MIGRATION, completed)
                     for k, a, d, c, at in steps]
        inst_id = det_id(tid, f"approval_instance.{subject_table}", subject_id)
        inst = {"id": inst_id, "tenant_id": tid, "flow_type": label, "definition_id": flow_def,
                "subject_table": subject_table, "subject_id": subject_id, "subject_employee_id": emp,
                "status": status, "started_at": started, "completed_at": completed,
                "created_at": started, **audit}
        step_rows = [{"id": child_id(inst_id, f"step{i}"), "tenant_id": tid, "instance_id": inst_id,
                      "step_index": i, "approver_kind": kind, "assignee_employee_id": assignee,
                      "decision": decision, "comment": comment, "decided_at": decided_at,
                      "created_at": started, **audit}
                     for i, (kind, assignee, decision, comment, decided_at) in enumerate(steps)]
        return inst_id, inst, step_rows

    # ------------------------------------------------------------ founder: HRMS

    def founder_leave(cur, t: Tenant, start_month: int, loaded: set[str]) -> StepResult:
        tid = t.tenant_id
        done = skipped = 0

        # Earliest leave date in the data: valid_from for the seeded and LEGACY types.
        starts = [leave_year(y, start_month)[1] for y in (legacy_year(b.get("year")) for b in h_bal) if y]
        starts += [d for d in (to_date(r.get("from_date")) for r in h_req) if d]
        earliest = min(starts) if starts else SINCE_ALWAYS

        # Leave types: seeded codes first, HRMS types fill a seeded code by name or get their own.
        by_seed, extra = {}, []
        for lt in h_types:
            name = text(lt.get("name"), 128)
            if not name:
                q.add("NO_NAME", "leave_types", lt.get("id"), tid); skipped += 1
                continue
            slug = slug_code(name)
            code = next((c for c, *_ in SEEDED if slug in (c, f"{c}_LEAVE") and c not in by_seed), None)
            if code:
                by_seed[code] = lt
            else:
                extra.append(lt)
        codes = unique_codes([c for c, *_ in SEEDED] + [LEGACY_CODE] + [text(lt["name"]) for lt in extra], 32)
        extra_codes = codes[len(SEEDED) + 1:]

        types, policies = [], []
        for code, name, _col, default_days in SEEDED:
            lt = by_seed.get(code)
            vf = (to_date(lt.get("start_date")) if lt else None) or earliest
            types.append({"id": det_id(tid, "hrms.leave_type", code), "tenant_id": tid, "code": code,
                          "name": text(lt.get("name"), 128) if lt else name, "is_paid": True, "unit": "DAYS",
                          "allow_half_day": True, "valid_from": vf,
                          "valid_to": to_date(lt.get("end_date")) if lt else None, "is_active": True,
                          "created_at": _ts(lt.get("created_at") if lt else None, run_start), **audit})
            policies.append((code, vf, _DAYS(lt.get("default_days")) if lt else _DAYS(default_days),
                             truthy(lt.get("leave_carried_forward")) if lt else None,
                             _ts(lt.get("created_at") if lt else None, run_start)))
        # LEGACY: holds the requests HRMS recorded without a type (X-01). Paid, so no migrated
        # request can raise loss of pay (W-67 R4); inactive, so nobody applies for it again.
        types.append({"id": det_id(tid, "hrms.leave_type", LEGACY_CODE), "tenant_id": tid, "code": LEGACY_CODE,
                      "name": LEGACY_NAME, "is_paid": True, "unit": "DAYS", "allow_half_day": True,
                      "valid_from": earliest, "valid_to": None, "is_active": False, "created_at": run_start,
                      **audit})
        policies.append((LEGACY_CODE, earliest, _DAYS(0), None, run_start))
        for lt, code in zip(extra, extra_codes):
            vf = to_date(lt.get("start_date")) or earliest
            types.append({"id": det_id(tid, "hrms.leave_type", code), "tenant_id": tid, "code": code,
                          "name": text(lt.get("name"), 128), "is_paid": True, "unit": "DAYS",
                          "allow_half_day": True, "valid_from": vf, "valid_to": to_date(lt.get("end_date")),
                          "is_active": True, "created_at": _ts(lt.get("created_at"), run_start), **audit})
            policies.append((code, vf, _DAYS(lt.get("default_days")) or _DAYS(0),
                             truthy(lt.get("leave_carried_forward")), _ts(lt.get("created_at"), run_start)))
        type_ids = write_types(cur, tid, types)
        code_of = {str(lt.get("id")): c for c, lt in by_seed.items()}
        code_of.update({str(lt.get("id")): c for lt, c in zip(extra, extra_codes)})
        codes_by_name = defaultdict(set)
        for lt in h_types:
            if str(lt.get("id")) in code_of:
                codes_by_name[(text(lt.get("name")) or "").lower()].add(code_of[str(lt.get("id"))])
        # HRMS turned days beyond the balance into loss of pay (LeaveRequests.lopAllocation): markAsLOP.
        pol_ids, pol_rows = {}, []
        for code, vf, annual, cf, created in policies:
            pid = det_id(tid, "hrms.leave_policy", code)
            pol_ids[code] = pid
            pol_rows.append(policy_row(tid, pid, type_ids[code], annual, vf, created, carry_forward_enabled=cf))
        db.upsert(cur, "core.leave_policy", pol_rows)
        done += len(types) + len(pol_rows)

        # Balances: HRMS employee_leave_balances when it has rows, else the five fixed columns.
        allocs, cons, seen = [], [], set()
        if h_emp_bal:
            label, ys, ye = current_leave_year(run_start.date(), start_month)
            # Two rows of one employee can land on one type (a deleted HRMS type found by name): the row
            # on a type that still exists wins, then the newest row; the other is a duplicate.
            for b in sorted(h_emp_bal, key=lambda x: (str(x.get("leave_type_id")) not in code_of,
                                                      -int(x.get("id") or 0))):
                lid = b.get("id")
                eid = hrms_emp(b.get("employee_id"), loaded)
                # The type by id; a row whose type was deleted in HRMS by its name, when one type has it.
                code = code_of.get(str(b.get("leave_type_id")))
                if code is None:
                    named = codes_by_name.get((text(b.get("leave_type")) or "").lower(), set())
                    code = next(iter(named)) if len(named) == 1 else None
                remaining = _DAYS(b.get("remaining_days"))
                if eid is None:
                    q.add("EMPLOYEE_NOT_FOUND", "employee_leave_balances", lid, tid); skipped += 1
                    continue
                if code is None:
                    q.add("LEAVE_TYPE_NOT_FOUND", "employee_leave_balances", lid, tid,
                          leave_type=text(b.get("leave_type"))); skipped += 1
                    continue
                if remaining is None:
                    q.add("NO_REMAINING_DAYS", "employee_leave_balances", lid, tid); skipped += 1
                    continue
                if (eid, code) in seen:
                    q.add("DUPLICATE_BALANCE_TYPE", "employee_leave_balances", lid, tid); skipped += 1
                    continue
                seen.add((eid, code))
                aid = det_id(tid, f"employee_leave_balances.{label}", lid)
                allocs.append({"id": aid, "tenant_id": tid, "employee_id": eid, "leave_type_id": type_ids[code],
                               "leave_year": label, "year_start_date": ys, "year_end_date": ye,
                               "entitlement_days": max(remaining, _DAYS(0)), "policy_id": pol_ids[code], **audit})
                if remaining < 0:
                    cons.append({"id": child_id(aid, "overdrawn"), "tenant_id": tid, "employee_id": eid,
                                 "allocation_id": aid, "consumed_days": -remaining, "consumed_on": ys,
                                 "period": ys.strftime("%Y-%m"),
                                 "reason": "Overdrawn balance from HRMS employee_leave_balances", **audit})
            # The opening balances replace every allocation an earlier run made: its own consumptions
            # (dated, no request) go first, so neither a stale overdrawn row nor an old used-days row
            # survives. A consumption the app wrote keeps its allocation, and reconciliation shows it.
            cur.execute("DELETE FROM core.leave_consumption WHERE tenant_id = %s AND created_by = %s "
                        "AND leave_request_id IS NULL", (tid, db.AUDIT_USER))
            cur.execute("DELETE FROM core.leave_allocation a WHERE a.tenant_id = %s AND a.created_by = %s "
                        "AND NOT (a.id = ANY(%s::uuid[])) AND NOT EXISTS (SELECT 1 FROM core.leave_consumption c "
                        "WHERE c.tenant_id = a.tenant_id AND c.allocation_id = a.id)",
                        (tid, db.AUDIT_USER, [a["id"] for a in allocs]))
            if cur.rowcount:
                log.info("tenant %s: %d earlier migrated allocations replaced", tid, cur.rowcount)
        for b in ([] if h_emp_bal else h_bal):
            eid = hrms_emp(b.get("employee_id"), loaded)
            year = legacy_year(b.get("year"))
            if eid is None:
                q.add("EMPLOYEE_NOT_FOUND", "leave_balances", b.get("id"), tid); skipped += 1
                continue
            if year is None:
                q.add("NO_YEAR", "leave_balances", b.get("id"), tid); skipped += 1
                continue
            if (eid, year) in seen:
                q.add("DUPLICATE_BALANCE_YEAR", "leave_balances", b.get("id"), tid, year=year); skipped += 1
                continue
            seen.add((eid, year))
            label, ys, ye = leave_year(year, start_month)
            for code, _n, col, _d in SEEDED:
                allotted = _DAYS(b.get(f"{col}_allotted")) or _DAYS(0)
                used = _DAYS(b.get(f"{col}_used")) or _DAYS(0)
                if not allotted and not used:
                    continue
                aid = det_id(tid, f"leave_balances.{code}", b.get("id"))
                allocs.append({"id": aid, "tenant_id": tid, "employee_id": eid, "leave_type_id": type_ids[code],
                               "leave_year": label, "year_start_date": ys, "year_end_date": ye,
                               "entitlement_days": allotted, "policy_id": pol_ids[code], **audit})
                if used > 0:
                    # HRMS kept a count, not dates: the consumption is dated the first day of its year.
                    cons.append({"id": child_id(aid, "used"), "tenant_id": tid, "employee_id": eid,
                                 "allocation_id": aid, "consumed_days": used, "consumed_on": ys,
                                 "period": ys.strftime("%Y-%m"),
                                 "reason": "Used days from HRMS leave_balances (no dates recorded)", **audit})
        db.upsert(cur, "core.leave_allocation", allocs, conflict=("tenant_id", "employee_id", "leave_type_id",
                                                                  "leave_year"), keep_on_update=("id",))
        db.upsert(cur, "core.leave_consumption", cons)
        done += len(allocs) + len(cons)

        # Requests under LEGACY, each with an approval instance (manager step, HR step).
        flow_def = _approval_definition(cur, tid, "LEAVE")
        managers = _primary_managers(cur, tid)
        reqs, insts, steps, approved = [], [], [], defaultdict(list)
        for r in sorted(h_req, key=lambda x: int(x.get("id") or 0)):
            rid = r.get("id")
            eid = hrms_emp(r.get("employee_id"), loaded)
            frm, to = to_date(r.get("from_date")), to_date(r.get("to_date"))
            if eid is None:
                q.add("EMPLOYEE_NOT_FOUND", "leave_requests", rid, tid); skipped += 1
                continue
            if frm is None or to is None or to < frm:
                q.add("BAD_DATES", "leave_requests", rid, tid); skipped += 1
                continue
            mgr = (text(r.get("reporting_manager_status")) or "PENDING").upper()
            hr = (text(r.get("hr_status")) or "PENDING").upper()
            if mgr not in REQUEST_STATUS or hr not in REQUEST_STATUS:
                q.add("STATUS_UNMAPPED", "leave_requests", rid, tid, manager=mgr, hr=hr); skipped += 1
                continue
            status = hr  # W-67 R2: hr_status wins over reporting_manager_status
            half = bool(truthy(r.get("is_half_day"), False))
            period = None
            if half:
                p = (text(r.get("half_day_period")) or "").lower()
                period = "first" if p.startswith(("first", "1")) else "second" if p.startswith(("second", "2")) else None
                if period is None:
                    q.add("HALF_DAY_PERIOD_UNMAPPED", "leave_requests", rid, tid); skipped += 1
                    continue
            days = _DAYS(r.get("total_days"))
            if days is None or days < 0:
                q.add("BAD_TOTAL_DAYS", "leave_requests", rid, tid); skipped += 1
                continue
            if status == "APPROVED":
                if any(frm <= e and s <= to for s, e in approved[eid]):
                    q.add("APPROVED_OVERLAP", "leave_requests", rid, tid,
                          note="overlaps an earlier approved request of the same employee"); skipped += 1
                    continue
                approved[eid].append((frm, to))
            req_id = det_id(tid, "leave_requests", rid)
            created = _ts(r.get("created_date"), run_start)
            mgr_at, hr_at = to_timestamp(r.get("reporting_manager_updated_at")), to_timestamp(r.get("hr_updated_at"))
            decided = (hr_at or mgr_at) if status != "PENDING" else None
            inst_id = None
            if flow_def:
                inst_id, inst, st = approval(
                    tid, flow_def, "leave_request", req_id, eid, status, created, decided,
                    [("REPORTING_MANAGER", managers.get(eid), mgr if mgr != "PENDING" else None,
                      text(r.get("reporting_manager_comment"), 1000), mgr_at if mgr != "PENDING" else None),
                     ("ROLE", None, hr if hr != "PENDING" else None,
                      text(r.get("hr_comment"), 1000), hr_at if hr != "PENDING" else None)], "LEAVE")
                insts.append(inst)
                steps += st
            else:
                q.add("NO_APPROVAL_DEFINITION", "leave_requests", rid, tid, note="loaded without approval history")
            reqs.append({"id": req_id, "tenant_id": tid, "employee_id": eid, "leave_type_id": type_ids[LEGACY_CODE],
                         "from_date": frm, "to_date": to, "is_half_day": half, "half_day_period": period,
                         "working_days": days, "reason": text(r.get("reason")), "status": status,
                         "approval_instance_id": inst_id, "on_behalf": False, "decided_at": decided,
                         "created_at": created, **audit})
        db.upsert(cur, "core.approval_instance", insts)
        db.upsert(cur, "core.approval_step", steps)
        db.upsert(cur, "core.leave_request", reqs)
        done += len(reqs) + len(insts) + len(steps)

        # Holidays into the default calendar
        hols = []
        for h in h_hol:
            name, d = text(h.get("name"), 128), to_date(h.get("date"))
            if not name or d is None:
                q.add("NO_NAME" if not name else "NO_DATE", "holidays", h.get("id"), tid); skipped += 1
                continue
            hols.append((h.get("id"), name, d))
        if hols:
            cal = ensure_default_calendar(cur, tid)
            rows = [{"id": det_id(tid, "hrms.holidays", hid), "tenant_id": tid, "calendar_id": cal, "name": name,
                     "from_date": d, "to_date": d, "is_restricted": False, "description": None, **audit}
                    for hid, name, d in hols]
            db.upsert(cur, "core.holiday", rows)
            done += len(rows)

        # Payroll's own leave rows for the founder's organisation are not carried (W-67 R2).
        for legacy_table, n in (("leave_type", len(p_types.get(t.legacy_org_id, []))),
                                ("employee_leave_allocation", len(p_alloc.get(tid, []))),
                                ("employee_leave_balance_consumption", len(p_cons.get(tid, []))),
                                ("holidays", len(p_hol.get(t.legacy_org_id, [])))):
            if n:
                q.add("FOUNDER_LEAVE_FROM_HRMS", legacy_table, None, tid, rows=n)
                skipped += n
        return StepResult(done, skipped)

    # ------------------------------------------------------------ other tenants: Payroll

    def payroll_leave(cur, t: Tenant, start_month: int, loaded: set[str]) -> StepResult:
        tid, org = t.tenant_id, t.legacy_org_id
        done = skipped = 0
        allocs_in = p_alloc.get(tid, [])

        # Leave types and policies
        alloc_by_type = defaultdict(list)
        for a in allocs_in:
            alloc_by_type[(text(a.get("leave_type")) or "").lower()].append(a)
        accepted = []
        for lt in p_types.get(org, []):
            lid = lt.get("id")
            name = text(lt.get("name"), 128)
            kind = (text(lt.get("type")) or "").lower()
            unit = (text(lt.get("unit")) or "").lower()
            if not name:
                q.add("NO_NAME", "leave_type", lid, tid); skipped += 1
                continue
            if kind not in ("paid", "unpaid"):
                q.add("LEAVE_KIND_UNMAPPED", "leave_type", lid, tid, value=kind); skipped += 1
                continue
            if unit not in ("day_based", "days", "day"):
                q.add("UNIT_UNMAPPED", "leave_type", lid, tid, value=unit); skipped += 1
                continue
            vf = to_date(lt.get("validity_from"))
            if vf is None:
                # No validity start recorded: the start of the earliest leave year it was allocated in.
                years = [y for y in (legacy_year(a.get("year")) for a in alloc_by_type[name.lower()]) if y]
                vf = leave_year(min(years), start_month)[1] if years else None
            if vf is None:
                q.add("NO_VALID_FROM", "leave_type", lid, tid); skipped += 1
                continue
            accepted.append((lt, name, kind, vf))
        codes = unique_codes([text(lt.get("code")) or name for lt, name, _k, _v in accepted], 32)
        types = [{"id": det_id(tid, "leave_type", lt.get("id")), "tenant_id": tid, "code": code, "name": name,
                  "is_paid": kind == "paid", "unit": "DAYS", "allow_half_day": bool(truthy(lt.get("allow_half_day"), False)),
                  "valid_from": vf, "valid_to": to_date(lt.get("validity_to")),
                  "is_active": (text(lt.get("status")) or "active").lower() == "active", **audit}
                 for (lt, name, kind, vf), code in zip(accepted, codes)]
        type_ids = write_types(cur, tid, types) if types else {}
        by_name, pol_by_type, pol_rows, elig = {}, {}, [], []
        for (lt, name, _k, vf), code in zip(accepted, codes):
            lid = lt.get("id")
            type_id = type_ids[code]
            by_name[name.lower()] = type_id
            if text(lt.get("code")):
                by_name.setdefault(text(lt.get("code")).lower(), type_id)
            freq = (text(lt.get("accrual_frequency")) or "").lower() or None
            if freq not in (None, "monthly", "yearly"):
                q.add("ACCRUAL_FREQUENCY_UNMAPPED", "leave_type", lid, tid, value=freq, note="loaded as none")
                freq = None
            reset = text(lt.get("reset_frequency"))
            reset = {"yearly": "yearly", "monthly": "monthly", "quarterly": "quarterly",
                     "halfyearly": "halfYearly", "half_yearly": "halfYearly"}.get((reset or "").lower())
            units = to_decimal(lt.get("accrual_units"))
            # annual_days has no Payroll column (entitlement was typed per employee): the accrual
            # it implies, else the entitlement most of its allocations carry, else 0.
            accrual_on = bool(truthy(lt.get("accrual_is_enabled"), False))
            if accrual_on and units is not None and freq:
                annual = to_decimal(units * (12 if freq == "monthly" else 1))
            else:
                common = Counter(_DAYS(a.get("annual_days")) for a in alloc_by_type[name.lower()]
                                 if _DAYS(a.get("annual_days")) is not None).most_common()
                annual = max(v for v, n in common if n == common[0][1]) if common else _DAYS(0)
            # Exceed balance: disabled meant the balance could not go below zero -> yearEndLimit 0.
            mode = text(lt.get("exceed_balance_mode"))
            if not truthy(lt.get("exceed_balance_is_enabled"), False) or mode == "yearEndLimit":
                mode, limit = "yearEndLimit", _DAYS(0)
            elif mode in ("noLimit", "markAsLOP"):
                limit = None
            else:
                q.add("EXCEED_MODE_UNMAPPED", "leave_type", lid, tid, value=mode, note="loaded as noLimit")
                mode, limit = "noLimit", None
            genders = {(text(g) or "").upper() for g in elig_rows["gender"].get(str(lid), []) if text(g)}
            max_app = to_decimal(lt.get("max_leave_per_application"))
            pid = det_id(tid, "leave_type.policy", lid)
            pol_by_type[type_id] = pid
            pol_rows.append(policy_row(
                tid, pid, type_id, annual, vf, run_start,
                accrual_enabled=accrual_on, accrual_frequency=freq, accrual_units=units,
                reset_enabled=truthy(lt.get("reset_is_enabled")), reset_frequency=reset,
                carry_forward_enabled=truthy(lt.get("carry_forward_is_enabled")),
                carry_forward_cap=to_decimal(lt.get("carry_forward_units")),
                past_booking_limit_days=int_or_none(lt.get("past_booking_limit_days")),
                future_booking_limit_days=int_or_none(lt.get("future_booking_limit_days")),
                include_weekend=truthy(lt.get("include_weekend_is_enabled")),
                include_holiday=truthy(lt.get("include_holiday_is_enabled")),
                exceed_balance_mode=mode, exceed_balance_limit_days=limit,
                pro_rate_enabled=truthy(lt.get("pro_rate_is_enabled")),
                max_days_per_application=max_app if max_app is not None and max_app > 0 else None,
                gender=next(iter(genders)) if len(genders) == 1 else None))
            for dim, key, wl in (("department", "payroll.department.uuid", False),
                                 ("designation", "payroll.designation.uuid", False),
                                 ("work_location", "payroll.workLocations", True)):
                for ref in elig_rows[dim].get(str(lid), []):
                    value = idmap.get(key, wl_by_uuid.get(str(ref)) if wl else ref)
                    if value is None:
                        q.add(f"{dim.upper()}_NOT_FOUND", f"leave_type_{dim}s", lid, tid); skipped += 1
                        continue
                    elig.append({"id": det_id(tid, f"leave_policy_eligibility.{dim}", f"{lid}:{ref}"),
                                 "tenant_id": tid, "policy_id": pid, "dimension": dim, "value_id": value, **audit})
        db.upsert(cur, "core.leave_policy", pol_rows)
        # On the deterministic id: every other column is the natural key, so a conflict on that key
        # would leave nothing to compare and touch updated_at on every run.
        elig = list({e["id"]: e for e in elig}.values())  # a value listed twice in legacy
        db.upsert(cur, "core.leave_policy_eligibility", elig)
        done += len(types) + len(pol_rows) + len(elig)

        # Allocations
        allocs, alloc_key, legacy_alloc = [], {}, {}
        for a in sorted(allocs_in, key=lambda x: int(x.get("id") or 0)):
            aid_legacy = a.get("id")
            eid = pay_emp(a.get("employee_id"))
            type_id = by_name.get((text(a.get("leave_type")) or "").lower())
            year = legacy_year(a.get("year"))
            if eid not in loaded:
                q.add("EMPLOYEE_NOT_LOADED", "employee_leave_allocation", aid_legacy, tid); skipped += 1
                continue
            if type_id is None:
                q.add("LEAVE_TYPE_NOT_FOUND", "employee_leave_allocation", aid_legacy, tid); skipped += 1
                continue
            if year is None:
                q.add("NO_YEAR", "employee_leave_allocation", aid_legacy, tid); skipped += 1
                continue
            entitlement = _DAYS(a.get("annual_days"))
            if entitlement is None:
                q.add("NO_ANNUAL_DAYS", "employee_leave_allocation", aid_legacy, tid); skipped += 1
                continue
            key = (eid, type_id, year)
            if key in alloc_key:
                q.add("DUPLICATE_ALLOCATION", "employee_leave_allocation", aid_legacy, tid); skipped += 1
                continue
            label, ys, ye = leave_year(year, start_month)
            new_id = det_id(tid, "employee_leave_allocation", aid_legacy)
            alloc_key[key] = new_id
            legacy_alloc[str(aid_legacy)] = new_id
            allocs.append({"id": new_id, "tenant_id": tid, "employee_id": eid, "leave_type_id": type_id,
                           "leave_year": label, "year_start_date": ys, "year_end_date": ye,
                           "entitlement_days": entitlement,
                           "carried_forward_days": _DAYS(a.get("carried_forward_days")) or _DAYS(0),
                           "carry_forward_expires_on": to_date(a.get("expiration_date")),
                           "policy_id": pol_by_type[type_id], "created_at": _ts(a.get("created_at"), run_start),
                           **audit})
        db.upsert(cur, "core.leave_allocation", allocs, conflict=("tenant_id", "employee_id", "leave_type_id",
                                                                  "leave_year"), keep_on_update=("id",))
        if allocs:
            # A row already stored under the same natural key keeps its id; point at that one.
            cur.execute("SELECT employee_id, leave_type_id, leave_year, id FROM core.leave_allocation "
                        "WHERE tenant_id = %s", (tid,))
            stored = {(str(e), str(lt), y): str(i) for e, lt, y, i in cur.fetchall()}
            real = {a["id"]: stored[(a["employee_id"], a["leave_type_id"], a["leave_year"])] for a in allocs}
            alloc_key = {k: real[v] for k, v in alloc_key.items()}
            legacy_alloc = {k: real[v] for k, v in legacy_alloc.items()}
        done += len(allocs)

        alloc_owner = {v: k[0] for k, v in alloc_key.items()}

        # Consumption (allocation.consumed_days is the sum of these rows in legacy:
        # EmployeeLeaveAllocationServiceImpl.java:494-505, so it is not loaded separately).
        cons = []
        for c in p_cons.get(tid, []):
            cid = c.get("id")
            eid = pay_emp(c.get("employee_id"))
            if eid not in loaded:
                q.add("EMPLOYEE_NOT_LOADED", "employee_leave_balance_consumption", cid, tid); skipped += 1
                continue
            alloc = legacy_alloc.get(str(c.get("allocation_id")))
            if alloc is not None and alloc_owner.get(alloc) != eid:
                alloc = None
            if alloc is None:
                type_id = by_name.get((text(c.get("leave_type")) or "").lower())
                alloc = alloc_key.get((eid, type_id, legacy_year(c.get("year"))))
            if alloc is None:
                q.add("ALLOCATION_NOT_FOUND", "employee_leave_balance_consumption", cid, tid); skipped += 1
                continue
            days = _DAYS(c.get("consumed_days"))
            if not days or days <= 0:
                q.add("ZERO_DAYS", "employee_leave_balance_consumption", cid, tid,
                      note="loss-of-pay only row; LOP history comes from pay runs (W-67 R4)"); skipped += 1
                continue
            period = period_of(c.get("leave_month"))
            if period is None:
                q.add("BAD_LEAVE_MONTH", "employee_leave_balance_consumption", cid, tid); skipped += 1
                continue
            y, m = (int(x) for x in period.split("-"))
            cons.append({"id": det_id(tid, "employee_leave_balance_consumption", cid), "tenant_id": tid,
                         "employee_id": eid, "allocation_id": alloc, "consumed_days": days,
                         "consumed_on": date(y, m, 1), "period": period, "reason": text(c.get("reason"), 500),
                         "created_at": _ts(c.get("created_at"), run_start), **audit})
        db.upsert(cur, "core.leave_consumption", cons)
        done += len(cons)

        # Holidays: tenant-wide ones in the default calendar; location-limited ones in a calendar
        # per location that also holds the tenant-wide ones.
        general, per_loc = [], defaultdict(list)
        cur.execute("SELECT id, name FROM core.work_location WHERE tenant_id = %s", (tid,))
        loc_names = {str(i): n for i, n in cur.fetchall()}
        for h in p_hol.get(org, []):
            hid = h.get("id")
            name, frm, to = text(h.get("name"), 128), to_date(h.get("from_date")), to_date(h.get("to_date"))
            if not truthy(h.get("status"), True):
                q.add("HOLIDAY_INACTIVE", "holidays", hid, tid); skipped += 1
                continue
            if not name or frm is None or to is None or to < frm:
                q.add("NO_NAME" if not name else "BAD_DATES", "holidays", hid, tid); skipped += 1
                continue
            refs = [r for r in hol_locs.get(str(hid), []) if r]
            locs = []
            for ref in refs:
                legacy_id = wl_by_uuid.get(ref) or wl_by_name.get((org, ref.lower()))
                loc = idmap.get("payroll.workLocations", legacy_id)
                if loc in loc_names:
                    locs.append(loc)
            if refs and len(locs) != len(refs):
                q.add("LOCATION_NOT_FOUND", "holiday_locations", hid, tid); skipped += 1
                continue
            row = (hid, name, frm, to, text(h.get("description"), 500), bool(truthy(h.get("is_restricted_holiday"), False)))
            if locs:
                for loc in locs:
                    per_loc[loc].append(row)
            else:
                general.append(row)
        if general or per_loc:
            default_cal = ensure_default_calendar(cur, tid)
            cals, links, rows = [], [], []

            def put(cal, hs, suffix):
                for hid, name, frm, to, desc, restricted in hs:
                    rows.append({"id": det_id(tid, "holidays", f"{hid}{suffix}"), "tenant_id": tid,
                                 "calendar_id": cal, "name": name, "from_date": frm, "to_date": to,
                                 "is_restricted": restricted, "description": desc, **audit})
            put(default_cal, general, "")
            for loc in sorted(per_loc):
                cal = det_id(tid, "holiday_calendar.location", loc)
                cals.append({"id": cal, "tenant_id": tid, "name": f"{loc_names[loc]} holidays"[:128],
                             "is_default": False, **audit})
                links.append({"id": det_id(tid, "holiday_calendar_location", loc), "tenant_id": tid,
                              "calendar_id": cal, "work_location_id": loc, **audit})
                put(cal, general + per_loc[loc], f"@{loc}")
            db.upsert(cur, "core.holiday_calendar", cals)
            db.upsert(cur, "core.holiday_calendar_location", links, conflict=("tenant_id", "work_location_id"),
                      keep_on_update=("id",))
            db.upsert(cur, "core.holiday", rows)
            done += len(cals) + len(links) + len(rows)
        return StepResult(done, skipped)

    # ------------------------------------------------------------ every tenant: attendance preferences

    def attendance_pref(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        rows = sorted(p_pref.get(t.legacy_org_id, []), key=lambda x: int(x.get("id") or 0))
        if not rows:
            return StepResult()
        skipped = 0
        for extra in rows[1:]:
            q.add("SECOND_ATTENDANCE_PREFERENCE", "attendance_preferences", extra.get("id"), tid); skipped += 1
        p = rows[0]
        full, half = hours_of(p.get("full_day_minimum_hours")), hours_of(p.get("half_day_minimum_hours"))
        if full is None or half is None or not (0 < half < full <= 24):
            q.add("MINIMUM_HOURS_INVALID", "attendance_preferences", p.get("id"), tid)
            return StepResult(0, skipped + 1)
        db.upsert(cur, "hrms.attendance_preference", [{
            "id": det_id(tid, "attendance_preferences", p.get("id")), "tenant_id": tid,
            "hours_calculation": "FIRST_IN_LAST_OUT" if truthy(p.get("calculation_on_firstin_lastout"), False)
            else "EVERY_SESSION",
            "full_day_minimum_hours": full, "half_day_minimum_hours": half,
            "regularization_window_days": int_or_none(p.get("request_days_buffer")),
            "max_regularizations_per_month": int_or_none(p.get("maximum_requests_allowed")),
            "allow_regularization_without_session": bool(truthy(p.get("can_create_new_entries"), True)),
            **audit}], conflict=("tenant_id",), keep_on_update=("id",))
        cur.execute("SELECT holidays_payable, weekends_payable FROM core.lop_policy WHERE tenant_id = %s "
                    "AND effective_from = %s", (tid, SINCE_ALWAYS))
        lop = cur.fetchone()
        wanted = (truthy(p.get("can_include_holidays_for_pay")), truthy(p.get("can_include_weekends_for_pay")))
        if lop is None or tuple(lop) != wanted:
            q.add("LOP_POLICY_KEPT_FROM_PAY_SCHEDULE", "attendance_preferences", p.get("id"), tid,
                  note="core.lop_policy is load_org's (paySchedule); legacy read neither flag pair")
        return StepResult(1, skipped)

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        cur.execute("SELECT leave_year_start_month FROM core.tenant WHERE tenant_id = %s", (tid,))
        row = cur.fetchone()
        start_month = int(row[0]) if row and row[0] and 1 <= int(row[0]) <= 12 else 4
        loaded = _loaded_employees(cur, tid)
        res = founder_leave(cur, t, start_month, loaded) if t.founder else payroll_leave(cur, t, start_month, loaded)
        pref = attendance_pref(cur, t)
        return StepResult(res.processed + pref.processed, res.skipped + pref.skipped)

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
