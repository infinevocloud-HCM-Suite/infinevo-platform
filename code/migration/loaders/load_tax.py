"""3h - income-tax declarations, TDS and proofs, current financial year only
(W-68 Task 3, mapping §3.7, decisions X-07 and X-08).

  IncomeTaxDeclaration + ProofOfInvestment       -> payroll.income_tax_declaration (one per tenant)
  employee_investment_declaration                -> payroll.employee_investment_declaration
  employee_inv_house_rent, _home_loan, _let_out_property (+ _detail), _other_income,
  _pre_tax_deduction, _prev_employment, _section6a, _tax_summary
                                                 -> the nine payroll.employee_inv_* tables
  employee_tds                                   -> payroll.employee_tds
  employee_proof_of_investment, employee_poi_item, employee_poi_item_comment,
  employee_poi_property_detail                   -> payroll.employee_proof_of_investment,
                                                    employee_proof_item, employee_proof_item_comment,
                                                    employee_inv_let_out_property_line
  employee_poi_document is W-69's: documents become core.document there, and
  payroll.employee_proof_item_document links them through the id-map key
  payroll.employee_poi_item written here.

Financial year: the FY of the run date, or MIGRATION_FY ("2025-26" or "2025-2026"). The
target writes "YYYY-YYYY" (CHECK on employee_tds.financial_year); legacy stores the FY's
end year as an integer (userInvestmentDeclaration.js:15-29: fiscalYear = fiscalYearEnd).
Rows of other years are quarantined NOT_CURRENT_FY (their children go with them).
"""
from __future__ import annotations

import calendar
import hashlib
import logging
import os
import re
import sys
from collections import defaultdict
from datetime import date

from common import db, staging
from common.ids import child_id, det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp, truthy
from common.money import money_or_zero, to_money
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_tax"
INPUTS = ["stg_payroll_IncomeTaxDeclaration.json", "stg_payroll_ProofOfInvestment.json",
          "stg_payroll_employee_investment_declaration.json", "stg_payroll_employee_inv_house_rent.json",
          "stg_payroll_employee_inv_home_loan.json", "stg_payroll_employee_inv_let_out_property.json",
          "stg_payroll_employee_inv_let_out_property_detail.json", "stg_payroll_employee_inv_other_income.json",
          "stg_payroll_employee_inv_pre_tax_deduction.json", "stg_payroll_employee_inv_prev_employment.json",
          "stg_payroll_employee_inv_section6a.json", "stg_payroll_employee_inv_tax_summary.json",
          "stg_payroll_employee_tds.json", "stg_payroll_employee_proof_of_investment.json",
          "stg_payroll_employee_poi_item.json", "stg_payroll_employee_poi_item_comment.json",
          "stg_payroll_employee_poi_property_detail.json",
          # id-map sources (merge + load_employees)
          "stg_merged_employees.json", "stg_payroll_employee.json"]
log = logging.getLogger(STEP)
AUDIT = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}

REGIMES = ("OLD", "NEW")
# Legacy declaration status: DRAFT at creation, SUBMITTED, APPROVED when the proof is approved
# (EmployeeInvestmentDeclarationServiceImpl.java:237, EmployeeProofOfInvestmentServiceImpl.java:1278,1815).
# The target keeps DRAFT/SUBMITTED; approval lives on the proof.
DECL_STATUS = {"DRAFT": "DRAFT", "SUBMITTED": "SUBMITTED", "APPROVED": "SUBMITTED"}
# employee_proof_of_investment.status is a PayRunStatus (EmployeeProofOfInvestment.java:48-50)
POI_STATUS = {"DRAFT": "DRAFT", "SUBMITTED": "SUBMITTED", "APPROVAL_PENDING": "SUBMITTED",
              "APPROVED": "APPROVED", "REJECTED": "REJECTED"}
ITEM_STATUS = {"DRAFT": "PENDING", "READY": "PENDING", "SUBMITTED": "PENDING", "APPROVAL_PENDING": "PENDING",
               "APPROVED": "APPROVED", "COMPLETED": "APPROVED", "REJECTED": "DISALLOWED"}
# Lower-case type values the legacy screens send (userInvestmentDeclaration.js:322-365, 1024-1160)
OTHER_INCOME_KIND = {"OTHER_INCOME": "OTHER", "OTHER": "OTHER", "SAVINGS_INTEREST": "SAVINGS_INTEREST",
                     "FD_INTEREST": "FD_INTEREST", "NSC_INTEREST": "NSC_INTEREST"}
PREV_EMP_KIND = {"INCOME": "INCOME", "INCOME_TAX": "INCOME_TAX_DEDUCTED",
                 "INCOME_TAX_DEDUCTED": "INCOME_TAX_DEDUCTED", "PROFESSIONAL_TAX": "PROFESSIONAL_TAX",
                 "EMPLOYEE_PF": "EMPLOYEE_PF", "LEAVE_ENCASHMENT": "LEAVE_ENCASHMENT"}
PRE_TAX_KIND = {"EPF": "EMPLOYEE_PF", "PF": "EMPLOYEE_PF", "EMPLOYEE_PF": "EMPLOYEE_PF", "VPF": "VPF",
                "PT": "PROFESSIONAL_TAX", "PROFESSIONAL_TAX": "PROFESSIONAL_TAX", "NPS": "NPS_EMPLOYEE",
                "NPS_EMPLOYEE": "NPS_EMPLOYEE"}
LINE_TYPE = {"ANNUAL_RENT": "ANNUAL_RENT", "MUNICIPAL_TAX": "MUNICIPAL_TAX", "MUNICIPAL_TAXES": "MUNICIPAL_TAX",
             "LOAN_INTEREST": "LOAN_INTEREST", "INTEREST_ON_LOAN": "LOAN_INTEREST", "INTEREST": "LOAN_INTEREST"}
# employee_tds.tds_source_type (enumeration/TdsSourceType.java): all three are computed from
# the declaration or the default regime; none is an officer's manual figure.
TDS_SOURCE = {"POI_BASED": "DECLARATION", "DEFAULT_REGIME": "DECLARATION", "SALARY_REVISION": "DECLARATION"}

_MONTHS = {m.lower(): i for i, m in enumerate(calendar.month_name) if m}
_MONTHS.update({m.lower(): i for i, m in enumerate(calendar.month_abbr) if m})


def _norm(v) -> str:
    return re.sub(r"[\s\-]+", "_", (text(v) or "").upper())


class FY:
    def __init__(self, start_year: int):
        self.start, self.end = start_year, start_year + 1
        self.label = f"{self.start}-{self.end}"
        self.opens, self.closes = date(self.start, 4, 1), date(self.end, 3, 31)

    def month(self, m: int) -> date:
        """First day of month m inside this FY (April..December in the start year)."""
        return date(self.start if m >= 4 else self.end, m, 1)


def current_fy(today: date | None = None) -> FY:
    raw = (os.environ.get("MIGRATION_FY") or "").strip()
    if raw:
        m = re.fullmatch(r"(\d{4})-(\d{2}|\d{4})", raw)
        if not m:
            raise ValueError(f"MIGRATION_FY={raw!r}: expected YYYY-YY or YYYY-YYYY")
        start = int(m.group(1))
        end = int(m.group(2)) if len(m.group(2)) == 4 else (start // 100) * 100 + int(m.group(2))
        if end != start + 1:
            raise ValueError(f"MIGRATION_FY={raw!r}: the end year must follow the start year")
        return FY(start)
    t = today or date.today()
    return FY(t.year if t.month >= 4 else t.year - 1)


def _month(v) -> date | None:
    """'2025-04' or a full date -> first day of that month."""
    s = text(v)
    if not s:
        return None
    d = to_date(s + "-01") if re.fullmatch(r"\d{4}-\d{2}", s) else to_date(s)
    return d.replace(day=1) if d else None


def _fy_period(v, fy: FY) -> str | None:
    """employee_tds.effective_from_month: a month name ('march', ProofOfInvestment.monthToConsiderPoi,
    DefaultTdsCreationServiceImpl.java:234-257) or 'YYYY-MM' -> 'YYYY-MM' inside the FY."""
    s = text(v)
    if not s:
        return None
    if re.fullmatch(r"\d{4}-\d{2}", s):
        return s if 1 <= int(s[5:]) <= 12 else None
    n = _MONTHS.get(s.lower()) or (int(s) if s.isdigit() and 1 <= int(s) <= 12 else None)
    if not n:
        return None
    d = fy.month(n)
    return f"{d.year:04d}-{d.month:02d}"


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    fy = current_fy()
    log.info("financial year %s", fy.label)
    plan: dict[str, dict[str, list]] = defaultdict(lambda: defaultdict(list))

    def emp_of(legacy_emp, tid, table, lid, by_uuid=False) -> str | None:
        eid = (idmap.get("payroll.employee.uuid", legacy_emp) if by_uuid else None) or \
            idmap.get("payroll.employee", legacy_emp)
        if eid is None:
            q.add("EMPLOYEE_NOT_MIGRATED", table, lid, tid, employee_ref=legacy_emp)
            return None
        owner = idmap.get("employee.tenant", eid)
        if owner and owner != tid:
            q.add("EMPLOYEE_TENANT_MISMATCH", table, lid, tid, employee_ref=legacy_emp)
            return None
        return eid

    def pan(v, table, lid, tid) -> str | None:
        s = (text(v) or "").upper().replace(" ", "")
        if not s:
            return None
        if len(s) != 10:  # VARCHAR(10); a malformed PAN is dropped, never cut
            q.add("PAN_DROPPED_NOT_10_CHARS", table, lid, tid)
            return None
        return s

    def audit(row: dict, created) -> dict:
        ts = to_timestamp(created)
        out = {**row, **AUDIT}
        if ts:
            out["created_at"] = ts
        return out

    # ---- tenant declaration window -------------------------------------------------------
    itd_by_org = {str(r.get("organizationId")): r for r in staging.load("stg_payroll_IncomeTaxDeclaration.json")}
    poi_cfg_by_org = {str(r.get("organizationId")): r for r in staging.load("stg_payroll_ProofOfInvestment.json")}
    default_regime: dict[str, str] = {}
    for org in sorted(set(itd_by_org) | set(poi_cfg_by_org)):
        t = tenants.for_org(org)
        src = itd_by_org.get(org) or {}
        poi = poi_cfg_by_org.get(org) or {}
        if t is None:
            q.add("ORG_NOT_MIGRATED", "IncomeTaxDeclaration", src.get("id") or poi.get("id"), organization=org)
            continue
        tid = t.tenant_id
        regime = _norm(src.get("default_tax_regime"))
        regime = regime if regime in REGIMES else "NEW"
        if _norm(src.get("default_tax_regime")) in REGIMES:
            default_regime[tid] = regime
        closes = to_date(src.get("lastDateForItDeclaration"))
        if closes is None or closes < fy.opens:
            closes = fy.closes  # no usable lock date for this FY: the window runs to the FY end
        poi_month = _MONTHS.get((text(poi.get("monthToConsiderPoi")) or "").lower())
        poi_opens = fy.month(poi_month) if poi_month else None
        poi_due = to_date(poi.get("lastDateForPoi"))
        if poi_opens and poi_due and poi_due < poi_opens:
            q.add("POI_DUE_BEFORE_OPEN", "ProofOfInvestment", poi.get("id"), tid)
            poi_opens = None
        plan[tid]["itd"].append({
            "id": det_id(tid, "income_tax_declaration", fy.label), "tenant_id": tid, "financial_year": fy.label,
            "window_opens_on": fy.opens, "window_closes_on": closes,
            "is_locked": bool(truthy(src.get("isItDeclarationLocked"), False)), "default_tax_regime": regime,
            "can_change_tax_regime": bool(truthy(src.get("canChangeTaxRegimeIt"), True)),
            "pan_required_for_rent_over_threshold": bool(truthy(
                src.get("isPanMandatoryForAnnualRentOverOneLakh",
                        poi.get("isPanMandatoryForAnnualRentOverOneLakh")), True)),
            "notify_on_lock": bool(truthy(src.get("sendMailOnItDeclarationLock"), False)),
            "notify_on_release": bool(truthy(src.get("sendMailOnItDeclarationRelease"), False)),
            "poi_opens_on": poi_opens, "poi_due_date": poi_due,
            "poi_locked": bool(truthy(poi.get("isPoiLocked"), False)),
            "poi_attachment_mandatory": bool(truthy(poi.get("isAttachmentMandatoryPoiForPortal"), True)),
            "poi_comment_mandatory": bool(truthy(poi.get("isCommentsMandatoryForPoiApproval"), False)),
            **AUDIT})

    # ---- employee declarations -----------------------------------------------------------
    decls: dict[str, dict] = {}         # legacy declaration id -> {tid, id, employee_id}
    other_fy: set[str] = set()          # legacy declarations of another year: children follow silently
    by_emp: dict[tuple, dict] = {}
    for d in sorted(staging.load("stg_payroll_employee_investment_declaration.json"), key=lambda r: int(r["id"])):
        lid = d["id"]
        t = tenants.for_org(d.get("organizationId"))
        if t is None:
            q.add("ORG_NOT_MIGRATED", "employee_investment_declaration", lid, organization=d.get("organizationId"))
            other_fy.add(str(lid))
            continue
        tid = t.tenant_id
        if d.get("fiscal_year") is None or int(d["fiscal_year"]) != fy.end:
            q.add("NOT_CURRENT_FY", "employee_investment_declaration", lid, tid, fiscal_year=d.get("fiscal_year"))
            other_fy.add(str(lid))
            continue
        eid = emp_of(d.get("employee_id"), tid, "employee_investment_declaration", lid)
        if eid is None:
            continue
        status = DECL_STATUS.get(_norm(d.get("status")))
        if status is None:
            q.add("DECLARATION_STATUS_UNMAPPED", "employee_investment_declaration", lid, tid, status=d.get("status"))
            continue
        regime = _norm(d.get("tax_regime"))
        if regime not in REGIMES:
            regime = default_regime.get(tid)  # the org default the legacy app applied
        if regime is None:
            q.add("NO_TAX_REGIME", "employee_investment_declaration", lid, tid)
            continue
        key = (tid, eid)
        if key in by_emp:  # uk (tenant, employee, FY): the newest legacy row wins
            prev = by_emp.pop(key)
            decls.pop(prev["legacy"])
            q.add("DUPLICATE_DECLARATION", "employee_investment_declaration", prev["legacy"], tid, kept=lid)
        target = det_id(tid, "employee_investment_declaration", lid)
        row = audit({"id": target, "tenant_id": tid, "employee_id": eid, "financial_year": fy.label,
                     "tax_regime": regime, "status": status,
                     "is_staying_in_rented_house": bool(truthy(d.get("is_staying_in_rented_house"), False)),
                     "is_repaying_self_occupied_loan": bool(truthy(d.get("is_repaying_self_occupied_loan"), False)),
                     "has_let_out_property": bool(truthy(d.get("has_let_out_property"), False)),
                     "is_locked": not truthy(d.get("can_allow_edit"), True),
                     "submitted_at": to_timestamp(d.get("updated_time")) if status == "SUBMITTED" else None},
                    d.get("created_time"))
        by_emp[key] = {"legacy": str(lid), "row": row}
        decls[str(lid)] = {"tid": tid, "id": target, "employee_id": eid}
    for v in by_emp.values():
        plan[v["row"]["tenant_id"]]["decl"].append(v["row"])
        idmap.put("payroll.employee_investment_declaration", v["legacy"], v["row"]["id"])

    def parent(row, table, col="declaration_id") -> dict | None:
        ref = str(row.get(col))
        if ref in decls:
            return decls[ref]
        if ref not in other_fy:
            q.add("DECLARATION_NOT_LOADED", table, row.get("id"), declaration_ref=row.get(col))
        return None

    def money_ok(v, table, lid, tid, what) -> object:
        m = money_or_zero(v)
        if m < 0:
            q.add("NEGATIVE_AMOUNT", table, lid, tid, field=what)
            return None
        return m

    # ---- house rent ----------------------------------------------------------------------
    rent_ids: dict[str, dict] = {}
    for r in staging.load("stg_payroll_employee_inv_house_rent.json"):
        p = parent(r, "employee_inv_house_rent")
        if not p:
            continue
        tid, lid = p["tid"], r["id"]
        frm, to = _month(r.get("from_month")), _month(r.get("to_month"))
        if frm is None or to is None or frm > to:
            q.add("HOUSE_RENT_BAD_MONTHS", "employee_inv_house_rent", lid, tid)
            continue
        if not text(r.get("address")) or not text(r.get("landlord_name")):
            q.add("HOUSE_RENT_NO_ADDRESS_OR_LANDLORD", "employee_inv_house_rent", lid, tid)
            continue
        amount = money_ok(r.get("amount_per_month"), "employee_inv_house_rent", lid, tid, "amount_per_month")
        if amount is None:
            continue
        target = det_id(tid, "employee_inv_house_rent", lid)
        rent_ids[str(lid)] = {"id": target, "decl": p["id"], "landlord": text(r.get("landlord_name"), 150)}
        plan[tid]["rent"].append(audit({
            "id": target, "tenant_id": tid, "declaration_id": p["id"], "from_month": frm, "to_month": to,
            "address": text(r.get("address"), 1000), "landlord_name": text(r.get("landlord_name"), 150),
            "landlord_pan": pan(r.get("landlord_pan"), "employee_inv_house_rent", lid, tid),
            "is_metro": bool(truthy(r.get("is_metro"), False)), "amount_per_month": amount}, r.get("created_time")))

    # ---- home loan -----------------------------------------------------------------------
    loan_ids: dict[str, dict] = {}
    for r in staging.load("stg_payroll_employee_inv_home_loan.json"):
        p = parent(r, "employee_inv_home_loan")
        if not p:
            continue
        tid, lid = p["tid"], r["id"]
        if not text(r.get("lender_name")):
            q.add("HOME_LOAN_NO_LENDER", "employee_inv_home_loan", lid, tid)
            continue
        principal = money_ok(r.get("principal_paid"), "employee_inv_home_loan", lid, tid, "principal_paid")
        interest = money_ok(r.get("interest_paid"), "employee_inv_home_loan", lid, tid, "interest_paid")
        if principal is None or interest is None:
            continue
        target = det_id(tid, "employee_inv_home_loan", lid)
        loan_ids[str(lid)] = {"id": target, "decl": p["id"], "lender": text(r.get("lender_name"), 150)}
        plan[tid]["loan"].append({
            "id": target, "tenant_id": tid, "declaration_id": p["id"], "lender_name": text(r.get("lender_name"), 150),
            "lender_pan": pan(r.get("lender_pan"), "employee_inv_home_loan", lid, tid),
            "principal_paid": principal, "interest_paid": interest, **AUDIT})

    # ---- let-out property and its lines --------------------------------------------------
    props: dict[str, dict] = {}
    for r in staging.load("stg_payroll_employee_inv_let_out_property.json"):
        p = parent(r, "employee_inv_let_out_property")
        if not p:
            continue
        tid, lid = p["tid"], r["id"]
        if not text(r.get("property_name")):
            q.add("LET_OUT_NO_NAME", "employee_inv_let_out_property", lid, tid)
            continue
        target = det_id(tid, "employee_inv_let_out_property", lid)
        props[str(lid)] = {"id": target, "tid": tid, "decl": p["id"], "lines": {}}
        plan[tid]["prop"].append(audit({
            "id": target, "tenant_id": tid, "declaration_id": p["id"],
            "property_name": text(r.get("property_name"), 150), "address": text(r.get("address"), 1000),
            "net_income_loss": money_or_zero(r.get("net_income_loss"))}, r.get("created_time")))

    def add_line(r, table, prop_ref):
        prop = props.get(str(prop_ref))
        if prop is None:
            q.add("PROPERTY_NOT_LOADED", table, r.get("id"), property_ref=prop_ref)
            return
        tid, lid = prop["tid"], r["id"]
        line_type = LINE_TYPE.get(_norm(r.get("type")))
        if line_type is None:
            q.add("LET_OUT_LINE_TYPE_UNMAPPED", table, lid, tid, type=r.get("type"))
            return
        if line_type in prop["lines"]:
            if table == "employee_poi_property_detail":
                # a copy of the declared line, made when the proof was opened
                # (EmployeeProofOfInvestmentServiceImpl.java:812-823): already loaded
                return
            q.add("DUPLICATE_PROPERTY_LINE", table, lid, tid, kept=prop["lines"][line_type]["legacy"])
            return
        amount = money_ok(r.get("amount"), table, lid, tid, "amount")
        if amount is None:
            return
        target = det_id(tid, table, lid)
        prop["lines"][line_type] = {"id": target, "legacy": lid, "amount": amount}
        plan[tid]["prop_line"].append(audit({
            "id": target, "tenant_id": tid, "declaration_id": prop["decl"], "property_id": prop["id"],
            "line_type": line_type, "amount": amount, "lender_name": text(r.get("name_of_lender"), 150),
            "lender_pan": pan(r.get("pan_of_lender"), table, lid, tid)}, r.get("created_time")))

    for r in sorted(staging.load("stg_payroll_employee_inv_let_out_property_detail.json"), key=lambda x: int(x["id"])):
        add_line(r, "employee_inv_let_out_property_detail", r.get("let_out_property_id"))

    # ---- per-kind tables (one row per declaration per kind) ------------------------------
    def kinded(stg, table, kinds, key, build):
        seen: dict[tuple, object] = {}
        out: dict[str, dict] = {}
        for r in sorted(staging.load(stg), key=lambda x: int(x["id"])):
            p = parent(r, table)
            if not p:
                continue
            tid, lid = p["tid"], r["id"]
            kind = kinds.get(_norm(r.get(key)))
            if kind is None:
                q.add("KIND_UNMAPPED", table, lid, tid, type=r.get(key))
                continue
            if (p["id"], kind) in seen:
                q.add("DUPLICATE_KIND", table, lid, tid, kind=kind, kept=seen[(p["id"], kind)])
                continue
            amount = money_ok(r.get("amount"), table, lid, tid, "amount")
            if amount is None:
                continue
            seen[(p["id"], kind)] = lid
            target = det_id(tid, table, lid)
            out[str(lid)] = {"id": target, "decl": p["id"], "kind": kind}
            plan[tid][table].append(audit({"id": target, "tenant_id": tid, "declaration_id": p["id"], "kind": kind,
                                           "amount": amount, **build(r)}, r.get("created_time")))
        return out

    kinded("stg_payroll_employee_inv_other_income.json", "employee_inv_other_income", OTHER_INCOME_KIND, "type",
           lambda r: {"description": text(r.get("name") or r.get("type_formatted"), 150)})
    kinded("stg_payroll_employee_inv_pre_tax_deduction.json", "employee_inv_pre_tax_deduction", PRE_TAX_KIND,
           "type", lambda r: {})
    prev_ids = kinded("stg_payroll_employee_inv_prev_employment.json", "employee_inv_prev_employment",
                      PREV_EMP_KIND, "type",
                      lambda r: {"employer_name": text(r.get("name"), 150), "employer_tan": None,
                                 # legacy has no author column; declarations are entered in the portal
                                 "entered_by": "EMPLOYEE"})

    # ---- section 6A (section code resolved against reference.section6a_item_master in work) --
    sec6a_rows: list[dict] = []
    for r in staging.load("stg_payroll_employee_inv_section6a.json"):
        p = parent(r, "employee_inv_section6a")
        if not p:
            continue
        tid, lid = p["tid"], r["id"]
        amount = money_ok(r.get("amount"), "employee_inv_section6a", lid, tid, "amount")
        if amount is None:
            continue
        code = re.sub(r"^SECTION", "", re.sub(r"\s+", "", (text(r.get("category")) or "").upper()))
        sec6a_rows.append({"legacy": r, "tid": tid, "decl": p["id"], "code": code, "amount": amount,
                           "id": det_id(tid, "employee_inv_section6a", lid)})

    # ---- tax summary ---------------------------------------------------------------------
    seen_regime: dict[tuple, object] = {}
    for r in sorted(staging.load("stg_payroll_employee_inv_tax_summary.json"), key=lambda x: int(x["id"]),
                    reverse=True):
        p = parent(r, "employee_inv_tax_summary")
        if not p:
            continue
        tid, lid = p["tid"], r["id"]
        regime = _norm(r.get("regime"))
        if regime not in REGIMES:
            q.add("REGIME_UNMAPPED", "employee_inv_tax_summary", lid, tid, regime=r.get("regime"))
            continue
        if (p["id"], regime) in seen_regime:
            q.add("DUPLICATE_KIND", "employee_inv_tax_summary", lid, tid, kept=seen_regime[(p["id"], regime)])
            continue
        seen_regime[(p["id"], regime)] = lid
        months = r.get("no_of_remaining_months")
        plan[tid]["summary"].append(audit({
            "id": det_id(tid, "employee_inv_tax_summary", lid), "tenant_id": tid, "declaration_id": p["id"],
            "regime": regime, **{c: to_money(r.get(c)) for c in (
                "taxable_income", "net_taxable_income", "tax_on_taxable_income", "tax_ytd_amount", "tax_to_be_paid",
                "tds_through_payroll", "tds_previous_employer", "tds_other_income", "other_sources_income",
                "exemption_under_section10")},
            "exemption_under_section6a": to_money(r.get("exemption_under_section6a")),
            "remaining_months": int(months) if months is not None else None,
            "computed_at": to_timestamp(r.get("updated_time") or r.get("created_time"))}, r.get("created_time")))

    # ---- proofs of investment ------------------------------------------------------------
    pois: dict[str, dict] = {}
    poi_by_decl: dict[str, dict] = {}
    for r in sorted(staging.load("stg_payroll_employee_proof_of_investment.json"), key=lambda x: int(x["id"])):
        lid = r["id"]
        if r.get("fiscal_year") is not None and int(r["fiscal_year"]) != fy.end:
            t = tenants.for_org(r.get("organization_id"))
            q.add("NOT_CURRENT_FY", "employee_proof_of_investment", lid, t.tenant_id if t else None,
                  fiscal_year=r.get("fiscal_year"))
            continue
        p = parent(r, "employee_proof_of_investment")
        if not p:
            continue
        tid = p["tid"]
        eid = emp_of(r.get("employee_id"), tid, "employee_proof_of_investment", lid)
        if eid is None:
            continue
        status = POI_STATUS.get(_norm(r.get("status")))
        if status is None:
            q.add("POI_STATUS_UNMAPPED", "employee_proof_of_investment", lid, tid, status=r.get("status"))
            continue
        if p["id"] in poi_by_decl:  # uk (tenant, declaration): newest legacy row wins
            old = poi_by_decl[p["id"]]
            q.add("DUPLICATE_POI", "employee_proof_of_investment", old["legacy"], tid, kept=lid)
            pois.pop(old["legacy"])
        target = det_id(tid, "employee_proof_of_investment", lid)
        entry = {"legacy": str(lid), "id": target, "tid": tid, "decl": p["id"], "employee_id": eid,
                 "row": audit({"id": target, "tenant_id": tid, "employee_id": eid, "declaration_id": p["id"],
                               "financial_year": fy.label, "status": status, "approval_instance_id": None,
                               "reviewer_note": None, "submitted_at": to_timestamp(r.get("submitted_date")),
                               "decided_at": to_timestamp(r.get("approved_date"))
                               if status in ("APPROVED", "REJECTED") else None}, r.get("created_time"))}
        poi_by_decl[p["id"]] = entry
        pois[str(lid)] = entry
    for e in pois.values():
        plan[e["tid"]]["poi"].append(e["row"])
        idmap.put("payroll.employee_proof_of_investment", e["legacy"], e["id"])

    # proof-side property lines first: a property line the declaration lacked becomes a line
    items_raw = {str(r["id"]): r for r in staging.load("stg_payroll_employee_poi_item.json")}
    for r in sorted(staging.load("stg_payroll_employee_poi_property_detail.json"), key=lambda x: int(x["id"])):
        item = items_raw.get(str(r.get("poi_item_id")))
        if item is None or str(item.get("poi_id")) not in pois or _norm(item.get("investment_type")) != "LET_OUT_PROPERTY":
            q.add("PROOF_ITEM_NOT_LOADED", "employee_poi_property_detail", r.get("id"), poi_item_ref=r.get("poi_item_id"))
            continue
        # the item's section6a_item_id is the let-out property id (EmployeeProofOfInvestmentServiceImpl.java:806-808)
        add_line(r, "employee_poi_property_detail", item.get("section6a_item_id"))

    # poi items are resolved in work(): SECTION_6A lines need the section6a ids decided there
    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        p = plan.get(tid) or defaultdict(list)
        cur.execute("SELECT upper(section_code), id::text FROM reference.section6a_item_master WHERE is_active")
        masters = dict(cur.fetchall())
        sec_by_decl: dict[tuple, list[dict]] = defaultdict(list)
        sec6a = []
        for s in sec6a_rows:
            if s["tid"] != tid:
                continue
            r = s["legacy"]
            master = masters.get(s["code"])
            if master is None:
                q.add("SECTION6A_CODE_UNMAPPED", "employee_inv_section6a", r["id"], tid, category=r.get("category"))
                continue
            desc = text(r.get("type_formatted") or r.get("type") or s["code"], 150)
            sec6a.append(audit({"id": s["id"], "tenant_id": tid, "declaration_id": s["decl"],
                                "section6a_item_id": master, "description": desc, "amount": s["amount"]},
                               r.get("created_time")))
            sec_by_decl[(s["decl"], str(r.get("section6a_item_id")))].append(
                {"id": s["id"], "type": _norm(r.get("type")), "description": desc})

        items, item_map = _proof_items(tid, pois, items_raw, rent_ids, loan_ids, props, prev_ids, sec_by_decl, q)
        comments = _comments(tid, item_map, idmap, q)
        for lid, target in item_map.items():
            idmap.put("payroll.employee_poi_item", lid, target)

        tds = _tds(tid, fy, tenants, pois, decls, idmap, emp_of, q)
        for table, key in (("payroll.income_tax_declaration", "itd"),
                           ("payroll.employee_investment_declaration", "decl"),
                           ("payroll.employee_inv_house_rent", "rent"), ("payroll.employee_inv_home_loan", "loan"),
                           ("payroll.employee_inv_let_out_property", "prop"),
                           ("payroll.employee_inv_let_out_property_line", "prop_line"),
                           ("payroll.employee_inv_other_income", "employee_inv_other_income"),
                           ("payroll.employee_inv_pre_tax_deduction", "employee_inv_pre_tax_deduction"),
                           ("payroll.employee_inv_prev_employment", "employee_inv_prev_employment"),
                           ("payroll.employee_inv_section6a", None), ("payroll.employee_inv_tax_summary", "summary"),
                           ("payroll.employee_proof_of_investment", "poi"), ("payroll.employee_proof_item", None),
                           ("payroll.employee_proof_item_comment", None), ("payroll.employee_tds", None)):
            rows = {"payroll.employee_inv_section6a": sec6a, "payroll.employee_proof_item": items,
                    "payroll.employee_proof_item_comment": comments, "payroll.employee_tds": tds}.get(table) \
                if key is None else p[key]
            _upsert_grouped(cur, table, rows)
        processed = sum(len(v) for v in p.values()) + len(sec6a) + len(items) + len(comments) + len(tds)
        return StepResult(processed=processed, skipped=q.count(tid))

    checksum = hashlib.sha256((staging.checksum(INPUTS) + ":" + fy.label).encode()).hexdigest()
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


def _proof_items(tid, pois, items_raw, rent_ids, loan_ids, props, prev_ids, sec_by_decl, q):
    """employee_poi_item -> employee_proof_item. Each target item points at one declaration
    line of its proof's declaration (ProofSourceReader.java:55-107). Legacy items carry that
    line's legacy id in section6a_item_id for every kind but Section 6A, where it is the master
    id (EmployeeProofOfInvestmentServiceImpl.java:682-900)."""
    out, item_map, seen = [], {}, set()
    poi_of = {e["legacy"]: e for e in pois.values() if e["tid"] == tid}
    for r in sorted(items_raw.values(), key=lambda x: int(x["id"])):
        poi = poi_of.get(str(r.get("poi_id")))
        if poi is None:
            continue
        lid, decl = r["id"], poi["decl"]
        status = ITEM_STATUS.get(_norm(r.get("status")))
        if status is None:
            q.add("PROOF_ITEM_STATUS_UNMAPPED", "employee_poi_item", lid, tid, status=r.get("status"))
            continue
        itype, ref = _norm(r.get("investment_type")), str(r.get("section6a_item_id"))
        targets: list[tuple[str, str, str, object]] = []  # (kind, source_line_id, description, declared)
        if itype == "HRA":
            src = rent_ids.get(ref)
            if src and src["decl"] == decl:
                targets.append(("HOUSE_RENT", src["id"], f"House rent paid to {src['landlord']}", None))
        elif itype in ("HOME_LOAN_PRINCIPAL", "HOME_LOAN_INTEREST"):
            src = loan_ids.get(ref)
            if src and src["decl"] == decl:
                part = "principal" if itype.endswith("PRINCIPAL") else "interest"
                targets.append((itype, src["id"], f"Home loan {part}, {src['lender']}", None))
        elif itype == "LET_OUT_PROPERTY":
            prop = props.get(ref)
            if prop and prop["decl"] == decl:
                lines = sorted(prop["lines"].items())
                if len(lines) > 1 and (money_or_zero(r.get("actual_amount")) or money_or_zero(r.get("approved_amount"))):
                    # one legacy amount cannot be split across the target's per-line items
                    q.add("PROOF_LET_OUT_AMOUNT_NOT_SPLITTABLE", "employee_poi_item", lid, tid)
                    continue
                for line_type, line in lines:
                    targets.append(("LET_OUT_PROPERTY", line["id"], f"Let-out property, {line_type}",
                                    line["amount"] if len(lines) > 1 else None))
        elif itype.startswith("PREV_EMPLOYMENT_"):
            src = prev_ids.get(ref)
            if src and src["decl"] == decl:
                targets.append(("PREV_EMPLOYMENT", src["id"], f"Previous employment, {src['kind']}", None))
        elif itype.startswith("OTHER_INCOME_") or itype.startswith("PRE_TAX_"):
            q.add("PROOF_KIND_NOT_IN_TARGET", "employee_poi_item", lid, tid, investment_type=itype)
            continue
        else:
            cands = sec_by_decl.get((decl, ref), [])
            match = [c for c in cands if c["type"] == itype] or cands
            if match:
                targets.append(("SECTION_6A", match[0]["id"], match[0]["description"], None))
        if not targets:
            q.add("PROOF_SOURCE_NOT_FOUND", "employee_poi_item", lid, tid, investment_type=itype)
            continue
        split = len(targets) > 1
        for n, (kind, source, desc, declared) in enumerate(targets):
            if (poi["id"], kind, source) in seen:
                q.add("DUPLICATE_PROOF_ITEM", "employee_poi_item", lid, tid)
                continue
            seen.add((poi["id"], kind, source))
            dec = declared if split else money_or_zero(r.get("declared_amount"))
            claimed = None if split else to_money(r.get("actual_amount"))
            approved = None if split or status == "PENDING" else to_money(r.get("approved_amount"))
            if any(v is not None and v < 0 for v in (dec, claimed, approved)):
                q.add("NEGATIVE_AMOUNT", "employee_poi_item", lid, tid)
                continue
            target = det_id(tid, "employee_poi_item", lid) if n == 0 else child_id(det_id(tid, "employee_poi_item", lid), source)
            if n == 0:
                item_map[str(lid)] = target
            row = {"id": target, "tenant_id": tid, "proof_id": poi["id"], "source_kind": kind,
                   "source_line_id": source, "description": text(desc, 150) or kind, "declared_amount": dec,
                   "claimed_amount": claimed, "approved_amount": approved, "status": status,
                   "employee_note": None, "reviewer_note": text(r.get("admin_comment"), 1000), **AUDIT}
            ts = to_timestamp(r.get("created_time"))
            if ts:
                row["created_at"] = ts
            out.append(row)
    return out, item_map


def _comments(tid, item_map, idmap, q):
    """employee_poi_item_comment -> employee_proof_item_comment. The author must be an
    employee: an admin comment whose commented_by_admin is not an employee id is quarantined.
    Threading (response_to_comment_id) has no target column and is dropped."""
    out = []
    for r in staging.load("stg_payroll_employee_poi_item_comment.json"):
        item = item_map.get(str(r.get("poi_item_id")))
        if item is None:
            continue
        lid = r["id"]
        if r.get("commented_by_employee_id") not in (None, ""):
            author, role = idmap.get("payroll.employee", r.get("commented_by_employee_id")), "EMPLOYEE"
        else:
            ref = r.get("commented_by_admin")
            author = idmap.get("payroll.employee.uuid", ref) or idmap.get("payroll.employee", ref)
            role = "REVIEWER"
        if author is None or idmap.get("employee.tenant", author) not in (None, tid):
            q.add("COMMENT_AUTHOR_NOT_EMPLOYEE", "employee_poi_item_comment", lid, tid, role=role)
            continue
        body = text(r.get("comment"), 1000)
        if not body:
            q.add("COMMENT_EMPTY", "employee_poi_item_comment", lid, tid)
            continue
        row = {"id": det_id(tid, "employee_poi_item_comment", lid), "tenant_id": tid, "item_id": item,
               "author_employee_id": author, "author_role": role, "body": body,
               "created_by": db.AUDIT_USER}  # comments are immutable: no updated_* columns
        ts = to_timestamp(r.get("created_time"))
        if ts:
            row["created_at"] = ts
        out.append(row)
    return out


def _tds(tid, fy, tenants, pois, decls, idmap, emp_of, q):
    """employee_tds -> payroll.employee_tds. One active row per employee per FY
    (uk_employee_tds_tenant_employee_fy_active); an older active row becomes inactive,
    superseded when the next row was created (CHECK is_active = (superseded_at IS NULL))."""
    decl_by_emp = {(d["tid"], d["employee_id"]): d["id"] for d in decls.values()}
    poi_decl = {e["legacy"]: e["decl"] for e in pois.values()}
    groups: dict[str, list[dict]] = defaultdict(list)
    for r in sorted(staging.load("stg_payroll_employee_tds.json"), key=lambda x: int(x["id"])):
        t = tenants.for_org(r.get("organization_id"))
        if t is None or t.tenant_id != tid:
            continue
        lid = r["id"]
        if r.get("fiscal_year") is None or int(r["fiscal_year"]) != fy.end:
            q.add("NOT_CURRENT_FY", "employee_tds", lid, tid, fiscal_year=r.get("fiscal_year"))
            continue
        eid = emp_of(r.get("employee_id"), tid, "employee_tds", lid, by_uuid=True)
        if eid is None:
            continue
        regime = _norm(r.get("tax_regime"))
        source = TDS_SOURCE.get(_norm(r.get("tds_source_type")))
        period = _fy_period(r.get("effective_from_month"), fy)
        amounts = [to_money(r.get(c)) for c in ("annual_gross_salary", "annual_taxable_income", "final_annual_tax")]
        if regime not in REGIMES or source is None or period is None:
            q.add("TDS_UNMAPPED", "employee_tds", lid, tid, regime=r.get("tax_regime"),
                  source=r.get("tds_source_type"), effective_from=r.get("effective_from_month"))
            continue
        if any(a is None or a < 0 for a in amounts):
            q.add("TDS_AMOUNT_INVALID", "employee_tds", lid, tid)
            continue
        decl = poi_decl.get(str(r.get("poi_id"))) or decl_by_emp.get((tid, eid))
        groups[eid].append({"legacy": r, "row": {
            "id": det_id(tid, "employee_tds", lid), "tenant_id": tid, "employee_id": eid,
            "financial_year": fy.label, "regime": regime, "source": source, "declaration_id": decl,
            "annual_gross": amounts[0], "annual_taxable_income": amounts[1], "annual_tax": amounts[2],
            "effective_from_period": period, "is_active": False, "superseded_at": None, "note": None, **AUDIT}})
    out = []
    for eid, rows in groups.items():
        active = [x for x in rows if truthy(x["legacy"].get("is_active"), True)]
        keep = active[-1] if active else None
        for i, x in enumerate(rows):
            row = x["row"]
            created = to_timestamp(x["legacy"].get("created_at"))
            if x is keep:
                row["is_active"] = True
            else:
                later = [to_timestamp(y["legacy"].get("created_at")) for y in rows[i + 1:]]
                row["superseded_at"] = next((ts for ts in later if ts), None) or created
                if row["superseded_at"] is None:
                    q.add("TDS_NO_SUPERSEDED_TIME", "employee_tds", x["legacy"]["id"], tid)
                    continue
            if created:
                row["created_at"] = created
            idmap.put("payroll.employee_tds", x["legacy"]["id"], row["id"])
            out.append(row)
    return out


def _upsert_grouped(cur, table: str, rows: list[dict]) -> None:
    """Rows with and without a legacy created_at go in separate statements (one key set each)."""
    for cols in sorted({tuple(r.keys()) for r in rows}):
        db.upsert(cur, table, [r for r in rows if tuple(r.keys()) == cols])


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
