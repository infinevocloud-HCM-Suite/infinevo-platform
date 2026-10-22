"""3e — salary components and the active salary structure (W-68 Task 3, mapping §3.4, W-67 X-05).

  earnings, deductions, benefits, reimbursements -> payroll.earning, deduction, benefit, reimbursement
  FBP                                            -> payroll.fbp (one row per tenant)
  ctc_structure (is_active AND NOT is_deleted)   -> payroll.ctc_structure (one per employee, revisions dropped)
  employee_earnings, employee_variable_earnings  -> payroll.employee_earning
  employee_benefits                              -> payroll.employee_benefit
  employee_reimbursements                        -> payroll.employee_reimbursement
  employee_fbp_components                        -> payroll.employee_fbp_component
  ctc_epf_components, ctc_esi_components         -> payroll.ctc_epf_component, ctc_esi_component

Component masters become the tenant's own rows. The country template (W-73.9,
SalaryComponentTemplateContributor) writes only for a tenant with no earning and no deduction of its
own, so a migrated tenant keeps exactly its legacy catalogue. The one seed already present when this
step runs is V175's five reimbursements (INTERNET, TRAVEL, MEDICAL, FOOD, FUEL), written by a trigger
on core.tenant: a legacy reimbursement whose code equals one of them lands on that row (same
(tenant_id, code), its id kept) instead of creating a duplicate.

Codes: config/component_map.json names the code for a legacy component name where the slug would be
wrong (per legacy org id, falling back to "default"); every other component gets the upper-snake
slug of its name, made unique per tenant. A CTC line whose component did not load, or whose
variableCode / componentCode resolves to no component, quarantines the whole structure - never a
guess at which component was meant.

Sum check: the enabled, non-variable, monthly earning lines plus the employer EPF and ESI lines must
equal monthlySalary to the paisa (one paisa of rounding allowed), else CTC_SUM_MISMATCH. That is the
legacy CTC identity: Annual CTC = Basic + HRA + Conveyance + Fixed + employer EPF + employer ESI
(Payroll-Fend-react editSalaryDetails.js:457-476, editReviseSalary.js:446-458); bonus and other
variable pay sit outside it.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

from common import db, staging
from common.ids import det_id, slug_code
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp, truthy
from common.money import to_decimal, to_money
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_salary"
INPUTS = [
    "stg_payroll_earnings.json", "stg_payroll_deductions.json", "stg_payroll_benefits.json",
    "stg_payroll_reimbursements.json", "stg_payroll_FBP.json", "stg_payroll_ctc_structure.json",
    "stg_payroll_employee_earnings.json", "stg_payroll_employee_benefits.json",
    "stg_payroll_employee_reimbursements.json", "stg_payroll_employee_fbp_components.json",
    "stg_payroll_employee_variable_earnings.json", "stg_payroll_ctc_epf_components.json",
    "stg_payroll_ctc_esi_components.json",
    # employee ids in id_map.json come from the merge step's output
    "stg_merged_employees.json",
]
_MAP_DEFAULT = Path(__file__).resolve().parents[1] / "config" / "component_map.json"
KINDS = ("earnings", "deductions", "benefits", "reimbursements")
CODE_RE = re.compile(r"^[A-Z0-9_]{1,32}$")
RATE = Decimal("0.0001")
TWELVE = Decimal("12")
ONE_PAISA = Decimal("0.01")
log = logging.getLogger(STEP)

# Legacy free text -> target values (target componentFields.js, V162 country template).
_FREQUENCY = {"monthly": ("MONTHLY", 12), "quarterly": ("QUARTERLY", 4), "half_yearly": ("HALF_YEARLY", 2),
              "halfyearly": ("HALF_YEARLY", 2), "half yearly": ("HALF_YEARLY", 2),
              "yearly": ("YEARLY", 1), "annually": ("YEARLY", 1), "annual": ("YEARLY", 1)}
# Legacy addNewCustomEarning.js:557-572 offers "Always" / "Conditional"; the target names the same
# two choices ALWAYS / WHEN_PF_WAGE_BELOW_15000 (componentFields.js:61-65).
_EPF_INCLUSION = {"always": "ALWAYS", "conditional": "WHEN_PF_WAGE_BELOW_15000",
                  "when_pf_wage_below_15000": "WHEN_PF_WAGE_BELOW_15000", "never": "NEVER"}
# Legacy addNewReimbursement.js:56 writes carry_forward / monthly_encash.
_CARRY = {"carry_forward": "CARRY_FORWARD", "monthly_encash": "MONTHLY_ENCASH"}
# Legacy codes (editSalaryDetails.js:707,726, editReviseSalary.js:806-842, SalaryDetailsTab.js:515-517)
# -> target CHECK values (V068, V069).
_EPF_CODE = {"EPF_EMPLOYER": "EPF_EMPLOYER", "EPF_EMPLOYEE": "EPF_EMPLOYEE", "EPS_EMPLOYER": "EPS_EMPLOYER",
             "EDLI": "EDLI", "EDLI_EMPLOYER": "EDLI", "EPF_ADMIN": "EPF_ADMIN", "EPF_ADMIN_CHARGES": "EPF_ADMIN",
             "ADMIN": "EPF_ADMIN"}
_ESI_CODE = {"ESI_EMPLOYER": "ESI_EMPLOYER", "ESI_EMPLOYEE": "ESI_EMPLOYEE"}
# The lines the legacy CTC identity counts (see module docstring); the target's own included_in_ctc.
_IN_CTC = {"EPF_EMPLOYER", "ESI_EMPLOYER"}


# ---------------------------------------------------------------- helpers

def _rate(v) -> Decimal | None:
    """A percentage as the legacy stored it: 12, '12', '0.50', '3.25%', '12_actual' (leading number).
    Ports parseContributionRate (Payroll-Fend-react editESI.js:91-104)."""
    if v is None:
        return None
    m = re.match(r"^\s*(\d+(?:\.\d+)?)", str(v))
    return to_decimal(m.group(1), RATE) if m else None


def _frequency(v) -> tuple[str | None, int]:
    """(target frequency or None, occurrences per year). Unknown / 'default' / empty -> (None, 12)."""
    s = (text(v) or "").lower().replace("-", "_")
    return _FREQUENCY.get(s, (None, 12))


def _per_month(annual: Decimal) -> Decimal:
    return (annual / TWELVE).quantize(ONE_PAISA, rounding=ROUND_HALF_UP)


def _load_component_map() -> tuple[dict, str]:
    path = Path(os.environ.get("MIGRATION_COMPONENT_MAP") or _MAP_DEFAULT)
    if not path.exists():
        return {}, ""
    raw = path.read_bytes()
    data = json.loads(raw.decode("utf-8"))
    for org, sections in data.items():
        if org.startswith("_"):
            continue
        for kind, entries in sections.items():
            for name, code in entries.items():
                if not CODE_RE.match(str(code)):
                    raise ValueError(f"component_map.json {org}.{kind}.{name!r}: code {code!r} is not ^[A-Z0-9_]{{1,32}}$")
    return data, hashlib.sha256(raw).hexdigest()


def _mapped(cmap: dict, org: str, kind: str, name: str | None) -> str | None:
    """The configured code for a legacy name: the org's own entry, else 'default'. Case-insensitive."""
    if not name:
        return None
    key = name.strip().lower()
    for section in (cmap.get(org, {}), cmap.get("default", {})):
        for n, code in section.get(kind, {}).items():
            if n.strip().lower() == key:
                return code
    return None


def _assign_codes(rows: list[dict], name_key: str, mapped: dict) -> dict:
    """{legacy id: code}. Mapped codes are fixed (two legacy rows may share one: they merge);
    the rest are slugs made unique against everything already taken, in legacy-id order."""
    out, used = {}, set(mapped.values())
    for r in rows:
        if r["id"] in mapped:
            out[r["id"]] = mapped[r["id"]]
    for r in rows:
        if r["id"] in out:
            continue
        base = slug_code(text(r.get(name_key)), 32)
        code, n = base, 2
        while code in used:
            suffix = f"_{n}"
            code = base[: 32 - len(suffix)] + suffix
            n += 1
        used.add(code)
        out[r["id"]] = code
    return out


def _by_org(rows: list[dict], key: str = "organizationId") -> dict[str, list[dict]]:
    out = defaultdict(list)
    for r in rows:
        out[str(r.get(key))].append(r)
    for v in out.values():
        v.sort(key=lambda r: int(r.get("id") or 0))
    return out


def _by_ctc(rows: list[dict]) -> dict[str, list[dict]]:
    out = defaultdict(list)
    for r in rows:
        out[str(r.get("ctc_structure_id"))].append(r)
    for v in out.values():
        v.sort(key=lambda r: int(r.get("id") or 0))
    return out


class _Reject(Exception):
    """A CTC structure that cannot be carried whole; reason + ids only."""

    def __init__(self, reason: str, **detail):
        super().__init__(reason)
        self.reason = reason
        self.detail = detail


# ---------------------------------------------------------------- the step

def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    cmap, cmap_sum = _load_component_map()
    run_start = datetime.now(timezone.utc)

    masters = {
        "earnings": staging.load("stg_payroll_earnings.json"),
        "deductions": staging.load("stg_payroll_deductions.json"),
        "benefits": staging.load("stg_payroll_benefits.json"),
        "reimbursements": staging.load("stg_payroll_reimbursements.json"),
    }
    fbp_rows = staging.load("stg_payroll_FBP.json")
    ctc_rows = staging.load("stg_payroll_ctc_structure.json")
    lines = {
        "earnings": _by_ctc(staging.load("stg_payroll_employee_earnings.json")),
        "benefits": _by_ctc(staging.load("stg_payroll_employee_benefits.json")),
        "reimbursements": _by_ctc(staging.load("stg_payroll_employee_reimbursements.json")),
        "fbp": _by_ctc(staging.load("stg_payroll_employee_fbp_components.json")),
        "variable": _by_ctc(staging.load("stg_payroll_employee_variable_earnings.json")),
        "epf": _by_ctc(staging.load("stg_payroll_ctc_epf_components.json")),
        "esi": _by_ctc(staging.load("stg_payroll_ctc_esi_components.json")),
    }

    # Rows of organisations that are not tenants: reported once, here.
    for kind, rows in list(masters.items()) + [("FBP", fbp_rows), ("ctc_structure", ctc_rows)]:
        for r in rows:
            if tenants.for_org(r.get("organizationId")) is None:
                q.add("ORG_NOT_MIGRATED", kind, r.get("id"), organization_id=r.get("organizationId"))

    by_org = {k: _by_org(v) for k, v in masters.items()}
    fbp_by_org = {str(r.get("organizationId")): r for r in sorted(fbp_rows, key=lambda r: int(r.get("id") or 0))}
    ctc_by_org = _by_org(ctc_rows)
    checksum = staging.checksum(INPUTS)
    if cmap_sum:
        checksum = hashlib.sha256(f"{checksum}:{cmap_sum}".encode()).hexdigest()

    def work(cur, t: Tenant) -> StepResult:
        tid, org = t.tenant_id, t.legacy_org_id
        audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
        res = StepResult()
        comp = {}  # kind -> {legacy id (str): stored target id}
        comp_by_uuid = {}  # kind -> {legacy uuid string: stored target id}
        comp_by_code = {}  # kind -> {target code: stored target id}
        earning_type = {}  # stored earning id -> legacy earningType (lower)
        variable_earning = set()  # stored earning ids that are variable

        # ---------------- masters
        for kind in KINDS:
            rows = by_org[kind].get(org, [])
            spec = _MASTER[kind]
            good = []
            for r in rows:
                problem = spec["check"](r)
                if problem:
                    q.add(problem, kind, r.get("id"), tid)
                    res.skipped += 1
                else:
                    good.append(r)
            mapped = {r["id"]: c for r in good
                      if (c := _mapped(cmap, org, kind, text(r.get(spec["name"]))))}
            codes = _assign_codes(good, spec["name"], mapped)
            table = spec["table"]
            cur.execute(db.on_table("SELECT code, id FROM {} WHERE tenant_id = %s", table), (tid,))
            existing = {c: str(i) for c, i in cur.fetchall()}
            stored_for = {}  # code -> id the row will have
            for r in good:
                code = codes[r["id"]]
                stored_for.setdefault(code, existing.get(code) or det_id(tid, kind, r["id"]))
            payload, seen = [], set()
            for r in good:
                code = codes[r["id"]]
                if code in seen:  # a second legacy row mapped onto the same code merges into the first
                    continue
                seen.add(code)
                row = spec["build"](r, tid, code, stored_for, existing, codes, good, run_start)
                row.update(audit)
                payload.append(row)
            if payload:
                db.upsert(cur, table, payload, conflict=("tenant_id", "code"), keep_on_update=("id",))
                res.processed += len(payload)
            cur.execute(db.on_table("SELECT code, id FROM {} WHERE tenant_id = %s", table), (tid,))
            stored = {c: str(i) for c, i in cur.fetchall()}
            comp[kind], comp_by_uuid[kind], comp_by_code[kind] = {}, {}, stored
            for r in good:
                sid = stored[codes[r["id"]]]
                comp[kind][str(r["id"])] = sid
                idmap.put(f"payroll.{kind}", r["id"], sid)
                uuid_col = spec["uuid"]
                if r.get(uuid_col):
                    comp_by_uuid[kind][str(r[uuid_col])] = sid
                    idmap.put(f"payroll.{kind}.uuid", r[uuid_col], sid)
                if kind == "earnings":
                    earning_type[sid] = (text(r.get("earningType")) or "").lower()
                    if truthy(r.get("isVariable"), False):
                        variable_earning.add(sid)

        # ---------------- FBP settings
        f = fbp_by_org.get(org)
        if f:
            closes = text(f.get("lastDateForFbpDeclaration"))
            row = {
                "id": det_id(tid, "FBP", f.get("id")), "tenant_id": tid,
                "is_enabled": bool(truthy(f.get("isFbpEnabled"), False)),
                "window_closes_on": to_date(closes),
                "is_locked": bool(truthy(f.get("isFbpDeclarationLocked"), False)),
                "notify_on_release": bool(truthy(f.get("sendMailOnFbpDeclarationRelease"), True)),
                "notify_on_lock": bool(truthy(f.get("sendMailOnFbpDeclarationLock"), True)),
                # The legacy reminder days live in `reminder`, which W-67 R6 does not carry: an org
                # with reminders on keeps the target default {5,1}; one with them off gets none.
                "reminder_days_before_close": [5, 1] if truthy(f.get("isAnyReminderBeforeLockdateEnabled"), False) else [],
                **audit,
            }
            if closes and row["window_closes_on"] is None:
                q.add("FBP_DATE_UNREADABLE", "FBP", f.get("id"), tid, note="window_closes_on left empty")
            db.upsert(cur, "payroll.fbp", [row], conflict=("tenant_id",), keep_on_update=("id",))
            res.processed += 1

        # ---------------- active salary structures
        active = [c for c in ctc_by_org.get(org, [])
                  if truthy(c.get("is_active"), False) and not truthy(c.get("is_deleted"), False)]
        per_emp = defaultdict(list)
        for c in active:
            per_emp[str(c.get("employee_id"))].append(c)
        chosen = []
        for emp, cs in per_emp.items():
            cs.sort(key=lambda c: (str(to_date(c.get("effective_date")) or ""), int(c.get("id") or 0)))
            chosen.append(cs[-1])
            for older in cs[:-1]:
                q.add("SECOND_ACTIVE_CTC", "ctc_structure", older.get("id"), tid, kept=cs[-1].get("id"))
                res.skipped += 1
        chosen.sort(key=lambda c: int(c.get("id") or 0))

        built = []
        for c in chosen:
            try:
                built.append(_build_ctc(c, tid, idmap, comp, comp_by_uuid, comp_by_code, earning_type,
                                        variable_earning, cmap, org, lines, q, run_start))
            except _Reject as rej:
                q.add(rej.reason, "ctc_structure", c.get("id"), tid, **rej.detail)
                res.skipped += 1

        if built:
            headers = [b["header"] | audit for b in built]
            db.upsert(cur, "payroll.ctc_structure", headers,
                      conflict=("tenant_id", "employee_id", "effective_from"), keep_on_update=("id",))
            cur.execute("SELECT employee_id::text, effective_from, id::text FROM payroll.ctc_structure "
                        "WHERE tenant_id = %s", (tid,))
            ids = {(e, d): i for e, d, i in cur.fetchall()}
            out = defaultdict(list)
            for b in built:
                h = b["header"]
                sid = ids[(h["employee_id"], h["effective_from"])]
                idmap.put("payroll.ctc_structure", b["legacy_id"], sid)
                for table, rows in b["children"].items():
                    for r in rows:
                        out[table].append({**r, "ctc_structure_id": sid, **audit})
            res.processed += len(built)
            for table, rows in out.items():
                if table in ("payroll.ctc_epf_component", "payroll.ctc_esi_component"):
                    db.upsert(cur, table, rows, conflict=("tenant_id", "ctc_structure_id", "component_code"),
                              keep_on_update=("id",))
                else:
                    db.upsert(cur, table, rows)
                res.processed += len(rows)
        return res

    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, tenants.all(), checksum, work)
    finally:
        if own:
            conn.close()
    idmap.save()
    q.write()
    return summary.ok


# ---------------------------------------------------------------- masters

def _bool(r, k, default):
    return bool(truthy(r.get(k), default))


def _created(r, k, run_start):
    return to_timestamp(r.get(k)) or run_start


def _check_earning(r):
    if not text(r.get("earningName")):
        return "NO_NAME"
    if not text(r.get("earningType")):
        return "NO_EARNING_TYPE"  # payroll.earning.earning_type is NOT NULL; nothing to derive it from
    return None


def _build_earning(r, tid, code, stored_for, existing, codes, good, run_start):
    pct = bool(truthy(r.get("isAmountInPercentage"), False))
    # editEarning.js:64-65 - the master's value is a % of CTC or a flat amount.
    value = r.get("value") if r.get("value") is not None else r.get("amount")
    in_epf = bool(truthy(r.get("isIncludedInEpf"), False))
    epf_type = _EPF_INCLUSION.get((text(r.get("epfInclusionType")) or "").lower()) if in_epf else "NEVER"
    parent = None
    if text(r.get("parentEarningId")):
        by_uuid = {str(g.get("earningId")): g for g in good}
        p = by_uuid.get(str(r.get("parentEarningId")))
        if p is not None:
            parent = stored_for[codes[p["id"]]]
    return {
        "id": existing.get(code) or det_id(tid, "earnings", r["id"]), "tenant_id": tid, "code": code,
        "name": text(r.get("earningName"), 128), "display_name": text(r.get("displayName"), 128),
        "earning_type": text(r.get("earningType"), 32),
        "calculation_type": "PERCENTAGE" if pct else "FLAT",
        "default_value": to_decimal(value, RATE) if pct else to_money(value),
        "percentage_of": "CTC" if pct else None,
        "max_limit": to_money(r.get("maxLimit")),
        "earning_frequency": _frequency(r.get("earningFrequency"))[0],
        "parent_earning_id": parent,
        "is_pro_rata": _bool(r, "isProRata", False),
        "is_included_in_ctc": _bool(r, "isIncludedInCtc", False),
        "is_included_in_salary_structure": _bool(r, "isIncludedInSalaryStructure", False),
        "is_taxable": _bool(r, "isTaxable", False),
        "is_variable": _bool(r, "isVariable", False),
        "is_one_time": _bool(r, "isOneTimeComponent", False),
        "is_fbp_component": _bool(r, "isFbpComponent", False),
        "is_included_in_epf": in_epf,
        "epf_inclusion_type": epf_type,
        "is_included_in_esi": _bool(r, "isIncludedInEsi", False),
        "show_in_payslip": _bool(r, "showInPayslip", True),
        "is_scheduled_earning": _bool(r, "isScheduledEarning", False),
        "is_active": _bool(r, "status", True),
        "is_deleted": _bool(r, "isDeleted", False),
        "created_at": _created(r, "createdTime", run_start),
    }


def _check_deduction(r):
    return None if text(r.get("deductionName")) else "NO_NAME"


def _deduction_type(v) -> str:
    # Legacy addNewDeduction.js:73 writes "custom"; the target knows POST_TAX / PRE_TAX only
    # (ComponentDrawer.jsx:427-431). Legacy payroll deductions come off net pay: POST_TAX,
    # unless the legacy text itself says pre-tax.
    s = re.sub(r"[^a-z]", "", (text(v) or "").lower())
    return "PRE_TAX" if s == "pretax" else "POST_TAX"


def _build_deduction(r, tid, code, stored_for, existing, codes, good, run_start):
    dtype = _deduction_type(r.get("deductionType"))
    return {
        "id": existing.get(code) or det_id(tid, "deductions", r["id"]), "tenant_id": tid, "code": code,
        "name": text(r.get("deductionName"), 128), "display_name": None,
        "deduction_type": dtype, "calculation_type": "FLAT",
        "is_recurring": _bool(r, "isRecurring", False), "is_pre_tax": dtype == "PRE_TAX",
        "emi_type": text(r.get("emiType"), 32),
        "perquisite_interest_rate": _rate(r.get("perquisiteInterestRate")),
        "emi_interest_rate": _rate(r.get("emiInterestRate")),
        "is_active": _bool(r, "status", True), "is_deleted": _bool(r, "isDeleted", False),
        "created_at": _created(r, "createdTime", run_start),
    }


def _check_benefit(r):
    return None if text(r.get("benefitName")) else "NO_NAME"


def _build_benefit(r, tid, code, stored_for, existing, codes, good, run_start):
    return {
        "id": existing.get(code) or det_id(tid, "benefits", r["id"]), "tenant_id": tid, "code": code,
        "name": text(r.get("benefitName"), 128), "display_name": None,
        "benefit_plan": text(r.get("benefitPlan"), 64), "benefit_category": text(r.get("benefitCategory"), 64),
        "calculation_type": "FLAT",
        "is_pre_tax": _bool(r, "isPreTax", False), "is_one_time": _bool(r, "isOneTime", False),
        "is_pro_rata": _bool(r, "isProRata", False), "is_superannuation": _bool(r, "isSuperannuationBenefit", False),
        "is_included_in_ctc": _bool(r, "isIncludedInCtc", False),
        "is_included_in_salary_structure": _bool(r, "isIncludedInSalaryStructure", False),
        "allows_employer_contribution": _bool(r, "canAllowEmployerContribution", False),
        "allows_employee_contribution": _bool(r, "canAllowEmployeeContribution", False),
        "tax_exempt_section": text(r.get("taxExemptSection"), 32),
        "tax_exemption_sub_type": text(r.get("taxExemptionSubType"), 64),
        "is_active": _bool(r, "status", True), "is_deleted": _bool(r, "isDeleted", False),
    }


def _check_reimbursement(r):
    if not text(r.get("reimbursementName")):
        return "NO_NAME"
    if not text(r.get("reimbursementType")):
        return "NO_REIMBURSEMENT_TYPE"  # reimbursement_type is NOT NULL; legacy required it (addNewReimbursement.js:34)
    return None


def _carry(v):
    s = (text(v) or "").lower().replace("-", "_")
    return _CARRY.get(s) or (slug_code(s, 32) if s else None)


def _build_reimbursement(r, tid, code, stored_for, existing, codes, good, run_start):
    return {
        "id": existing.get(code) or det_id(tid, "reimbursements", r["id"]), "tenant_id": tid, "code": code,
        "name": text(r.get("reimbursementName"), 128), "display_name": text(r.get("displayName"), 128),
        "reimbursement_type": slug_code(text(r.get("reimbursementType")), 32), "calculation_type": "FLAT",
        "max_limit": to_money(r.get("maxLimit")), "carry_forward_option": _carry(r.get("carryForwardOption")),
        "is_included_in_ctc": _bool(r, "isIncludedInCtc", False),
        "is_included_in_salary_structure": _bool(r, "isIncludedInSalaryStructure", False),
        "is_fbp_component": _bool(r, "isFbpComponent", False), "is_opt_in": _bool(r, "isOptIn", False),
        "is_active": _bool(r, "status", True), "is_deleted": _bool(r, "isDeleted", False),
    }


_MASTER = {
    "earnings": {"table": "payroll.earning", "name": "earningName", "uuid": "earningId",
                 "check": _check_earning, "build": _build_earning},
    "deductions": {"table": "payroll.deduction", "name": "deductionName", "uuid": "deductionId",
                   "check": _check_deduction, "build": _build_deduction},
    "benefits": {"table": "payroll.benefit", "name": "benefitName", "uuid": "benefitId",
                 "check": _check_benefit, "build": _build_benefit},
    "reimbursements": {"table": "payroll.reimbursement", "name": "reimbursementName", "uuid": "reimbursementId",
                       "check": _check_reimbursement, "build": _build_reimbursement},
}


# ---------------------------------------------------------------- one salary structure

def _resolve_code(value, kind, comp, comp_by_uuid, comp_by_code, cmap, org, section):
    """A legacy free-text component reference -> stored id: the legacy component's uuid
    (earningId / reimbursementId), its target code, or a component_map entry. None if nothing."""
    s = text(value)
    if not s:
        return None
    if s in comp_by_uuid[kind]:
        return comp_by_uuid[kind][s]
    if s.upper() in comp_by_code[kind]:
        return comp_by_code[kind][s.upper()]
    code = _mapped(cmap, org, section, s)
    if code and code in comp_by_code[kind]:
        return comp_by_code[kind][code]
    return None


def _build_ctc(c, tid, idmap, comp, comp_by_uuid, comp_by_code, earning_type, variable_earning,
               cmap, org, lines, q, run_start) -> dict:
    lid = str(c.get("id"))
    emp_id = idmap.get("payroll.employee", c.get("employee_id"))
    if emp_id is None or idmap.get("employee.tenant", emp_id) not in (None, tid):
        raise _Reject("EMPLOYEE_NOT_MIGRATED", employee_id=c.get("employee_id"))
    effective = to_date(c.get("effective_date"))
    if effective is None:
        raise _Reject("NO_EFFECTIVE_DATE")
    annual = to_money(c.get("annualCtc"))
    if annual is None:
        raise _Reject("NO_ANNUAL_CTC")
    monthly = to_money(c.get("monthlySalary"))
    if monthly is None:  # CtcStructureServiceImpl.java:124-131: ctc / 12, HALF_UP to 2 places
        monthly = _per_month(annual)
    created = to_timestamp(c.get("created_at")) or to_timestamp(effective.isoformat())

    notes = []
    for col in ("revision_status", "is_revision", "change_in_percent", "payment_month", "applied_in_payrun"):
        if c.get(col) is not None:
            notes.append(f"{col}={c.get(col)}")
    header = {
        "id": det_id(tid, "ctc_structure", lid), "tenant_id": tid, "employee_id": emp_id,
        "effective_from": effective, "annual_ctc": annual, "monthly_ctc": monthly, "is_cancelled": False,
        "notes": ("Migrated from legacy; " + "; ".join(notes))[:255] if notes else "Migrated from legacy",
        "created_at": created,
    }
    children = defaultdict(list)
    in_sum = Decimal("0")
    basic_monthly = None
    gross_monthly = Decimal("0")

    # Earnings (CtcStructureServiceImpl.java:133-159: amount is per occurrence of earningFrequency)
    for e in lines["earnings"].get(lid, []):
        cid = comp["earnings"].get(str(e.get("earning_id")))
        if cid is None:
            raise _Reject("UNMAPPED_COMPONENT", line_table="employee_earnings", line_id=e.get("id"),
                          component_id=e.get("earning_id"))
        freq, per_year = _frequency(e.get("earningFrequency"))
        amount = to_money(e.get("amount"))
        if amount is None:
            amount = to_money(e.get("overrideAmount")) or Decimal("0.00")
        annual_line = (amount * per_year).quantize(ONE_PAISA)
        monthly_line = amount if per_year == 12 else _per_month(annual_line)
        enabled = bool(truthy(e.get("enabled"), True))
        variable = bool(truthy(e.get("isVariable"), False)) or cid in variable_earning
        if enabled and not variable and per_year == 12:
            in_sum += monthly_line
            gross_monthly += monthly_line
        # Basic is % of CTC and HRA % of Basic in the legacy screen (editSalaryDetails.js:531-545);
        # every other legacy "percentage" line is really an amount, so it is carried FLAT.
        etype = earning_type.get(cid, "")
        pct = to_decimal(e.get("amountInPercentage"), RATE)
        calc, pct_of, value = "FLAT", None, monthly_line
        if (text(e.get("calculation_basis")) or "").upper() == "PERCENTAGE" and pct is not None:
            if etype == "basic":
                calc, pct_of, value = "PERCENTAGE", "CTC", pct
            elif etype == "house rent allowance":
                calc, pct_of, value = "PERCENTAGE", "BASIC", pct
        if etype == "basic" and enabled:
            basic_monthly = monthly_line
        children["payroll.employee_earning"].append({
            "id": det_id(tid, "employee_earnings", e.get("id")), "tenant_id": tid, "component_id": cid,
            "calculation_type": calc, "value": value, "percentage_of": pct_of,
            "monthly_amount": monthly_line, "annual_amount": annual_line, "is_enabled": enabled,
            "earning_frequency": freq,
        })

    # Variable earnings (variableCode names an earning; the legacy UI never wrote this table)
    for v in lines["variable"].get(lid, []):
        cid = _resolve_code(v.get("variableCode"), "earnings", comp, comp_by_uuid, comp_by_code, cmap, org, "variables")
        if cid is None:
            raise _Reject("UNMAPPED_VARIABLE_CODE", line_table="employee_variable_earnings", line_id=v.get("id"),
                          code=text(v.get("variableCode"), 64))
        amount = to_money(v.get("amount")) or Decimal("0.00")
        children["payroll.employee_earning"].append({
            "id": det_id(tid, "employee_variable_earnings", v.get("id")), "tenant_id": tid, "component_id": cid,
            "calculation_type": "FLAT", "value": amount, "percentage_of": None,
            "monthly_amount": amount, "annual_amount": amount * 12, "is_enabled": bool(truthy(v.get("enabled"), True)),
            "earning_frequency": None,
        })

    for b in lines["benefits"].get(lid, []):
        cid = comp["benefits"].get(str(b.get("benefit_id")))
        if cid is None:
            raise _Reject("UNMAPPED_COMPONENT", line_table="employee_benefits", line_id=b.get("id"),
                          component_id=b.get("benefit_id"))
        amount = to_money(b.get("amount")) or Decimal("0.00")
        children["payroll.employee_benefit"].append({
            "id": det_id(tid, "employee_benefits", b.get("id")), "tenant_id": tid, "component_id": cid,
            "calculation_type": "FLAT", "value": amount, "percentage_of": None,
            "monthly_amount": amount, "annual_amount": amount * 12, "is_enabled": bool(truthy(b.get("enabled"), True)),
        })

    for r in lines["reimbursements"].get(lid, []):
        cid = comp["reimbursements"].get(str(r.get("reimbursement_id")))
        if cid is None:
            raise _Reject("UNMAPPED_COMPONENT", line_table="employee_reimbursements", line_id=r.get("id"),
                          component_id=r.get("reimbursement_id"))
        amount = to_money(r.get("amount")) or Decimal("0.00")
        children["payroll.employee_reimbursement"].append({
            "id": det_id(tid, "employee_reimbursements", r.get("id")), "tenant_id": tid, "component_id": cid,
            "calculation_type": "FLAT", "value": amount, "percentage_of": None,
            "monthly_amount": amount, "annual_amount": amount * 12,
            "is_enabled": bool(truthy(r.get("enabled"), True)), "carry_forward_option": _carry(r.get("carryForwardOption")),
        })

    # FBP declarations: componentCode names an earning or a reimbursement; amount is the annual
    # declaration (the target derives monthly = annual / 12, FbpDeclarationServiceImpl.java:384).
    seen_fbp = set()
    for fl in lines["fbp"].get(lid, []):
        if not truthy(fl.get("enabled"), True):
            q.add("FBP_LINE_DISABLED", "employee_fbp_components", fl.get("id"), tid, ctc_structure_id=lid)
            continue
        eid = _resolve_code(fl.get("componentCode"), "earnings", comp, comp_by_uuid, comp_by_code, cmap, org, "fbp")
        rid = _resolve_code(fl.get("componentCode"), "reimbursements", comp, comp_by_uuid, comp_by_code, cmap, org, "fbp")
        if (eid is None) == (rid is None):
            raise _Reject("UNMAPPED_FBP_CODE" if eid is None else "AMBIGUOUS_FBP_CODE",
                          line_table="employee_fbp_components", line_id=fl.get("id"),
                          code=text(fl.get("componentCode"), 64))
        key = eid or rid
        if key in seen_fbp:
            raise _Reject("DUPLICATE_FBP_LINE", line_table="employee_fbp_components", line_id=fl.get("id"))
        seen_fbp.add(key)
        fa = to_money(fl.get("amount")) or Decimal("0.00")
        if fa < 0:
            raise _Reject("NEGATIVE_FBP_AMOUNT", line_table="employee_fbp_components", line_id=fl.get("id"))
        children["payroll.employee_fbp_component"].append({
            "id": det_id(tid, "employee_fbp_components", fl.get("id")), "tenant_id": tid, "employee_id": emp_id,
            "earning_id": eid, "reimbursement_id": rid, "annual_amount": fa, "monthly_amount": _per_month(fa),
            "declared_at": created, "declared_by": "CARRIED",
        })

    # Statutory lines. wage_base has no legacy column; it is the base the legacy computed from:
    # Basic for EPF (editSalaryDetails.js:438), gross monthly earnings for ESI - the target's
    # meaning too (StatutoryLineDeriver.java:54-56, 140-141).
    for kind, table, codes_map in (("epf", "payroll.ctc_epf_component", _EPF_CODE),
                                   ("esi", "payroll.ctc_esi_component", _ESI_CODE)):
        seen = set()
        for s in lines[kind].get(lid, []):
            code = codes_map.get((text(s.get("componentCode")) or "").upper())
            if code is None:
                raise _Reject("UNMAPPED_STATUTORY_CODE", line_table=f"ctc_{kind}_components", line_id=s.get("id"),
                              code=text(s.get("componentCode"), 64))
            if code in seen:
                raise _Reject("DUPLICATE_STATUTORY_LINE", line_table=f"ctc_{kind}_components", line_id=s.get("id"),
                              code=code)
            seen.add(code)
            rate = _rate(s.get("percentage"))
            if rate is None:
                raise _Reject("STATUTORY_RATE_MISSING", line_table=f"ctc_{kind}_components", line_id=s.get("id"))
            m = to_money(s.get("monthlyAmount")) or Decimal("0.00")
            a = to_money(s.get("annualAmount"))
            if a is None:
                a = m * 12
            if kind == "epf":
                if basic_monthly is None:
                    raise _Reject("NO_BASIC_FOR_EPF", line_table="ctc_epf_components", line_id=s.get("id"))
                base = basic_monthly
            else:
                base = gross_monthly
            share = "EMPLOYEE" if code.endswith("_EMPLOYEE") else "EMPLOYER"
            if code in _IN_CTC:
                in_sum += m
            children[table].append({
                "id": det_id(tid, f"ctc_{kind}_components", s.get("id")), "tenant_id": tid,
                "component_code": code, "share": share, "wage_base": base, "rate": rate,
                "monthly_amount": m, "annual_amount": a, "is_included_in_ctc": code in _IN_CTC,
            })

    if abs(in_sum.quantize(ONE_PAISA, rounding=ROUND_HALF_UP) - monthly) > ONE_PAISA:
        raise _Reject("CTC_SUM_MISMATCH", monthly_salary=str(monthly),
                      monthly_lines=str(in_sum.quantize(ONE_PAISA, rounding=ROUND_HALF_UP)))
    return {"legacy_id": lid, "header": header, "children": children}


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
