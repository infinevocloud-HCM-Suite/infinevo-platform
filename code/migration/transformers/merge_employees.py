"""W-68.2 — one person across two systems (W-67 R1).

Reads the HRMS employee tables and the Payroll employee, employee_personal_detail and
employee_bank_detail staging files, and writes:

  staging/stg_merged_employees.json   one record per target employee, every field load_employees needs
  staging/stg_quarantined_duplicates.json   duplicate source rows and rows with no joining date
  staging/id_map.json                 hrms.employee, hrms.emp_code, payroll.employee, payroll.employee.uuid -> employee id

Match keys, in order: Payroll work_mail = HRMS work_email, = HRMS personal_email, then PAN.
Never phone, never name. Cohorts: 1 both, 2 HRMS only, 3 Payroll only. Cohort 2 is
quarantined HRMS_ONLY_NOT_MIGRATED when the founder's map row says hrms_only=false (R1).
Identity (unchanged since the branch, so ids are stable across runs and versions):
uuid5(NAMESPACE, "<tenant_id>:<work_email or employee number>"); Payroll-only rows without
an email use "PAY-<payroll employee_id>".
HRMS wins names, gender, DOB, DOJ, addresses, emergency contacts, PAN, Aadhaar; Payroll
fills what HRMS leaves empty and wins statutory fields, bank, father's name, disability and
the tax-exemption flag. A row with no joining date in either system is quarantined NO_DOJ.
HRMS rows belong to the founder tenant unless their matched Payroll row is in another (R5).
"""
from __future__ import annotations

import logging
import sys
import uuid
from collections import defaultdict
from datetime import date

from common import staging
from common.idmap import IdMap
from common.ids import NAMESPACE
from common.legacy import text, to_date, truthy
from common.tenants import Tenants

log = logging.getLogger("merge_employees")


def employee_uuid(tenant_id: str, identifier: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{tenant_id}:{identifier}"))


def _email(v) -> str | None:
    s = text(v)
    return s.lower() if s else None


def _pan(v) -> str | None:
    s = text(v)
    return s.upper() if s else None


_GENDER = {"m": "MALE", "male": "MALE", "f": "FEMALE", "female": "FEMALE", "o": "OTHER", "other": "OTHER",
           "others": "OTHER", "transgender": "OTHER", "undisclosed": "UNDISCLOSED",
           "prefer not to say": "UNDISCLOSED", "not disclosed": "UNDISCLOSED"}


def norm_gender(v) -> str | None:
    s = (text(v) or "").lower()
    return _GENDER.get(s) if s else None


_EMPLOYMENT = {"permanent": "PERMANENT", "full-time permanent": "PERMANENT", "full time permanent": "PERMANENT",
               "full time": "PERMANENT", "full-time": "PERMANENT", "fulltime": "PERMANENT",
               "contract": "CONTRACT", "contractual": "CONTRACT", "part time": "PART_TIME", "part-time": "PART_TIME",
               "parttime": "PART_TIME", "intern": "INTERN", "internship": "INTERN", "trainee": "INTERN",
               "probation": "PROBATION", "on probation": "PROBATION", "consultant": "CONSULTANT"}


def norm_employment(v) -> str | None:
    return _EMPLOYMENT.get((text(v) or "").lower())


def sanitize_dob(dob: date | None, doj: date | None) -> date | None:
    """R1 bad dates: DOB after 2010, or on/after the joining date, becomes null."""
    if dob is None:
        return None
    if dob.year > 2010 or (doj is not None and dob >= doj):
        return None
    return dob


def _status(legacy_status, termination: date | None, deleted: bool) -> str:
    s = (text(legacy_status) or "ACTIVE").upper()
    if termination is not None and termination <= date.today():
        return "TERMINATED"
    if s in ("INACTIVE", "TERMINATED", "EXITED", "RELIEVED", "RESIGNED") or deleted:
        return "TERMINATED"
    if s == "SUSPENDED":
        return "SUSPENDED"
    return "ACTIVE"


def _first(*values):
    for v in values:
        if v not in (None, ""):
            return v
    return None


def run() -> list[dict]:
    tenants = Tenants.load()
    founder = tenants.founder
    idmap = IdMap.load()
    quarantined: list[dict] = []

    # ---- HRMS: employee joins personal, contact, identification, work, report
    personal = {r["id"]: r for r in staging.load("stg_hrms_personal.json")}
    contact = {r["id"]: r for r in staging.load("stg_hrms_contact.json")}
    ident = {r["id"]: r for r in staging.load("stg_hrms_identification.json")}
    work = {r["id"]: r for r in staging.load("stg_hrms_work.json")}
    report = {r["id"]: r for r in staging.load("stg_hrms_report.json")}

    hrms: list[dict] = []
    seen_hrms_email = set()
    for e in staging.load("stg_hrms_employee.json"):
        p = personal.get(e.get("personal_id"), {})
        c = contact.get(e.get("contact_id"), {})
        i = ident.get(e.get("identification_id"), {})
        w = work.get(e.get("work_id"), {})
        work_email = _email(c.get("work_email"))
        if work_email and work_email in seen_hrms_email:
            quarantined.append({"system": "HRMS", "reason": "DUPLICATE_SOURCE_EMAIL", "record_id": e["id"]})
            continue
        if work_email:
            seen_hrms_email.add(work_email)
        hrms.append({
            "hrms_id": e["id"], "emp_code": text(p.get("emp_id")), "report_id": e.get("report_id"),
            "employee_number": text(p.get("emp_id")) or f"HRMS-{e['id']}",
            "first_name": text(p.get("first_name")), "middle_name": text(p.get("middle_name")),
            "last_name": text(p.get("last_name")), "gender": norm_gender(p.get("gender")),
            "date_of_birth": to_date(p.get("date_of_birth")), "date_of_joining": to_date(w.get("doj")),
            "termination_date": to_date(w.get("termination_date")),
            "marital_status": text(p.get("marital_status"), 32), "nationality": text(p.get("nationality"), 64),
            "ethnicity": text(p.get("ethnicity"), 64),
            "employment_type": norm_employment(p.get("employment_status")),
            "employment_type_raw": text(p.get("employment_status")),
            "work_email": work_email, "personal_email": _email(c.get("personal_email")),
            "mobile": text(c.get("mobile_number"), 32),
            "address_line1": text(c.get("residential_address"), 255), "city": text(c.get("city"), 100),
            "state": text(c.get("state"), 100), "zip_code": text(c.get("postal_code"), 20),
            "permanent_address_line1": text(c.get("permanent_address"), 255),
            "emergency_contact_name": text(c.get("primary_emergency_contact_name"), 150),
            "emergency_contact_number": text(c.get("primary_emergency_contact_number"), 32),
            "emergency_contact_relationship": text(c.get("relationship_to_primary_emergency_contact"), 64),
            "secondary_emergency_contact_name": text(c.get("secondary_emergency_contact_name"), 150),
            "secondary_emergency_contact_number": text(c.get("secondary_emergency_contact_number"), 32),
            "secondary_emergency_contact_relationship": text(c.get("relationship_to_secondary_emergency_contact"), 64),
            "family_doctor_name": text(c.get("family_doctor_name"), 150),
            "family_doctor_contact_number": text(c.get("family_doctor_contact_number"), 32),
            "pan_number": _pan(i.get("pan_card_number")), "aadhaar_number": text(i.get("aadhar_card_number"), 12),
            "immigration_status": text(i.get("immigration_status"), 64),
            "personal_tax_id": text(i.get("personal_tax_id"), 64),
            "social_insurance_number": text(i.get("social_insurance"), 64),
            "id_proof_type": text(i.get("id_proof"), 64), "id_document_name": text(i.get("document_name"), 150),
            "id_document_number": text(i.get("document_number"), 64),
            "address_proof_type": text(i.get("address_proof"), 64),
            "address_document_name": text(i.get("address_document_name"), 150),
            "address_document_number": text(i.get("address_document_number"), 64),
            "pay_grade": text(w.get("pay_grade"), 32), "workstation_id": text(w.get("workstation_id"), 64),
            "time_zone": text(w.get("time_zone"), 64), "shift_start_time": text(w.get("shift_start_time")),
            "shift_end_time": text(w.get("shift_end_time")), "department_name": text(w.get("department"), 150),
            "job_title": text(w.get("job_title"), 150),
            "note": text(report.get(e.get("report_id"), {}).get("note"), 2000),
        })

    # ---- Payroll: employee + personal detail + bank detail
    pdetail = {r.get("employee_id"): r for r in staging.load("stg_payroll_employee_personal_detail.json")}
    bank = {r.get("employee_id"): r for r in staging.load("stg_payroll_employee_bank_detail.json")}
    payroll: list[dict] = []
    seen_key = set()
    for r in staging.load("stg_payroll_employee.json"):
        t = tenants.for_org(r.get("organizationId"))
        if t is None:
            quarantined.append({"system": "PAYROLL", "reason": "ORG_NOT_MIGRATED", "record_id": r.get("employee_id"),
                                "organization_id": r.get("organizationId")})
            continue
        work_mail = _email(r.get("work_mail"))
        key = (t.tenant_id, work_mail or f"#{text(r.get('employee_number'))}")
        if key in seen_key:
            quarantined.append({"system": "PAYROLL", "reason": "DUPLICATE_SOURCE_RECORD_IN_ORG",
                                "record_id": r.get("employee_id"), "organization_id": r.get("organizationId")})
            continue
        seen_key.add(key)
        pd = pdetail.get(r.get("employee_id"), {})
        b = bank.get(r.get("employee_id"))
        payroll.append({
            "payroll_id": r.get("employee_id"), "payroll_uuid": text(r.get("employeeId")), "tenant_id": t.tenant_id,
            "employee_number": text(r.get("employee_number")), "first_name": text(r.get("first_name")),
            "middle_name": text(r.get("middle_name")), "last_name": text(r.get("last_name")),
            "gender": norm_gender(r.get("gender")), "date_of_joining": to_date(r.get("date_of_joining")),
            "work_email": work_mail, "mobile": text(r.get("mobile"), 32),
            "legacy_status": text(r.get("employee_status")),
            "is_portal_enabled": bool(truthy(r.get("is_portal_enabled"), True)),
            "is_deleted": bool(truthy(r.get("is_deleted"), False)),
            "department_legacy_id": r.get("department_id"), "designation_legacy_id": r.get("designation_id"),
            "work_location_legacy_id": r.get("work_location_id"), "hr_user": text(r.get("hr_user")),
            "statutory": {
                "is_eligible_for_pf": bool(truthy(r.get("is_eligible_for_pf"), False)),
                "is_eligible_for_pt": bool(truthy(r.get("is_eligible_for_pt"), False)),
                "is_eligible_for_lwf": bool(truthy(r.get("is_eligible_for_lwf"), False)),
                "is_eligible_for_esi": bool(truthy(r.get("is_eligible_for_esi"), False)),
                "is_eligible_for_eps": bool(truthy(r.get("is_eligible_for_eps"), False)),
                "contributes_eps_on_higher_wages": bool(truthy(r.get("can_contribute_to_eps_on_higher_wages"), False)),
                "is_director": bool(truthy(r.get("is_director"), False)),
                "pf_account_number": text(r.get("pf_account_number"), 64), "uan": text(r.get("uan"), 12),
                "esi_number": text(r.get("esi_number"), 32),
            },
            "pd": {
                "date_of_birth": to_date(pd.get("date_of_birth")), "personal_email": _email(pd.get("personal_mail")),
                "pan_number": _pan(pd.get("pan")), "father_name": text(pd.get("father_name"), 150),
                "differently_abled_type": text(pd.get("differently_abled_type"), 64),
                "is_eligible_for_full_tax_exemption": bool(truthy(pd.get("is_eligible_for_full_income_tax_exemption"), False)),
                "address_line1": text(pd.get("addressLine1"), 255), "address_line2": text(pd.get("addressLine2"), 255),
                "city": text(pd.get("city"), 100), "state": text(pd.get("state"), 100),
                "state_code": text(pd.get("stateCode"), 8), "zip_code": text(pd.get("zipCode"), 20),
            },
            "bank": None if b is None else {
                "payment_mode": text(b.get("payment_mode")), "account_holder_name": text(b.get("account_holder_name"), 150),
                "bank_name": text(b.get("bank_name"), 150), "ifsc_code": text(b.get("ifsc_code"), 20),
                "bank_account_number": text(b.get("bank_account_number"), 64),
                "bank_account_type": text(b.get("bank_account_type")),
            },
        })

    # ---- Match (R1): work_mail -> HRMS work_email, -> HRMS personal_email, -> PAN
    by_work = {h["work_email"]: h for h in hrms if h["work_email"]}
    by_personal = {h["personal_email"]: h for h in hrms if h["personal_email"]}
    by_pan = {h["pan_number"]: h for h in hrms if h["pan_number"]}
    matched: dict[int, dict] = {}
    merged: list[dict] = []
    counts = defaultdict(int)

    def base_record(tenant_id: str, identifier: str, cohort: str) -> dict:
        return {"id": employee_uuid(tenant_id, identifier), "tenant_id": tenant_id, "cohort": cohort}

    for p in payroll:
        h = None
        if p["work_email"]:
            h = by_work.get(p["work_email"]) or by_personal.get(p["work_email"])
        if h is None and p["pd"]["pan_number"]:
            h = by_pan.get(p["pd"]["pan_number"])
        if h is not None and h["hrms_id"] in matched:
            h = None  # one HRMS person merges with one Payroll row; a second becomes Payroll-only
        if h is not None:
            matched[h["hrms_id"]] = p
            rec = base_record(p["tenant_id"], h["work_email"] or h["employee_number"], "COHORT_1_BOTH")
            counts["COHORT_1_BOTH"] += 1
        else:
            rec = base_record(p["tenant_id"], p["work_email"] or f"PAY-{p['payroll_id']}", "COHORT_3_PAYROLL_ONLY")
            counts["COHORT_3_PAYROLL_ONLY"] += 1
        merged.append(_combine(rec, h, p))

    for h in hrms:
        if h["hrms_id"] in matched:
            continue
        if founder is None:
            quarantined.append({"system": "HRMS", "reason": "NO_FOUNDER_TENANT", "record_id": h["hrms_id"]})
            continue
        if not founder.hrms_only:
            quarantined.append({"system": "HRMS", "reason": "HRMS_ONLY_NOT_MIGRATED", "record_id": h["hrms_id"]})
            continue
        rec = base_record(founder.tenant_id, h["work_email"] or h["employee_number"], "COHORT_2_HRMS_ONLY")
        counts["COHORT_2_HRMS_ONLY"] += 1
        merged.append(_combine(rec, h, None))

    # ---- No joining date in either system: quarantine (W-67 R1 change, W-68 Task 2)
    kept = []
    for m in merged:
        if m["date_of_joining"] is None:
            quarantined.append({"system": m["cohort"], "reason": "NO_DOJ",
                                "record_id": m["source_legacy_payroll_id"] or m["source_legacy_hrms_id"]})
            counts[m["cohort"]] -= 1
            continue
        kept.append(m)
    merged = kept

    # ---- Duplicate employee number within a tenant: suffix -P<id> / -H<id>
    used = defaultdict(set)
    for m in merged:
        num = m["employee_number"]
        if num in used[m["tenant_id"]]:
            suffix = f"-P{m['source_legacy_payroll_id']}" if m["source_legacy_payroll_id"] else f"-H{m['source_legacy_hrms_id']}"
            log.info("duplicate employee_number in tenant %s: %s -> %s%s", m["tenant_id"], num, num, suffix)
            m["employee_number"] = f"{num}{suffix}"
        used[m["tenant_id"]].add(m["employee_number"])

    # ---- id map
    for m in merged:
        idmap.put("hrms.employee", m["source_legacy_hrms_id"], m["id"])
        idmap.put("hrms.emp_code", m["hrms_emp_code"], m["id"])
        idmap.put("payroll.employee", m["source_legacy_payroll_id"], m["id"])
        idmap.put("payroll.employee.uuid", m["payroll_uuid"], m["id"])
        idmap.put("employee.tenant", m["id"], m["tenant_id"])
    idmap.save()

    staging.write("stg_merged_employees.json", merged)
    staging.write("stg_quarantined_duplicates.json", quarantined)
    log.info("merged %d employees %s, quarantined %d", len(merged), dict(counts), len(quarantined))
    return merged


def _combine(rec: dict, h: dict | None, p: dict | None) -> dict:
    """R1 field precedence."""
    h = h or {}
    pp = p or {}
    pd = pp.get("pd", {})
    doj = _first(h.get("date_of_joining"), pp.get("date_of_joining"))
    termination = h.get("termination_date")
    deleted = pp.get("is_deleted", False)
    rec.update({
        "employee_number": _first(h.get("employee_number") if h.get("emp_code") else None,
                                  pp.get("employee_number"), h.get("employee_number"),
                                  f"PAY-{pp.get('payroll_id')}" if p else None),
        "first_name": _first(h.get("first_name"), pp.get("first_name")),
        "middle_name": _first(h.get("middle_name"), pp.get("middle_name")),
        "last_name": _first(h.get("last_name"), pp.get("last_name")),
        "gender": _first(h.get("gender"), pp.get("gender")),
        "date_of_joining": doj,
        "termination_date": termination,
        "date_of_birth": sanitize_dob(_first(h.get("date_of_birth"), pd.get("date_of_birth")), doj),
        "work_email": _first(h.get("work_email"), pp.get("work_email")),
        "mobile": _first(h.get("mobile"), pp.get("mobile")),
        "status": _status(pp.get("legacy_status"), termination, deleted),
        "is_portal_enabled": pp.get("is_portal_enabled", True) if p else True,
        "is_deleted": deleted,
        "personal": {
            "marital_status": h.get("marital_status"), "nationality": h.get("nationality"),
            "ethnicity": h.get("ethnicity"), "father_name": pd.get("father_name"),
            "differently_abled_type": pd.get("differently_abled_type"),
            "is_eligible_for_full_tax_exemption": pd.get("is_eligible_for_full_tax_exemption", False),
        },
        "contact": {
            "personal_email": _first(h.get("personal_email"), pd.get("personal_email")),
            "address_line1": _first(h.get("address_line1"), pd.get("address_line1")),
            "address_line2": None if h.get("address_line1") else pd.get("address_line2"),
            "city": _first(h.get("city"), pd.get("city")), "state": _first(h.get("state"), pd.get("state")),
            "state_code": None if h.get("state") else pd.get("state_code"),
            "zip_code": _first(h.get("zip_code"), pd.get("zip_code")),
            "permanent_address_line1": h.get("permanent_address_line1"),
            **{k: h.get(k) for k in (
                "emergency_contact_name", "emergency_contact_number", "emergency_contact_relationship",
                "secondary_emergency_contact_name", "secondary_emergency_contact_number",
                "secondary_emergency_contact_relationship", "family_doctor_name", "family_doctor_contact_number")},
        },
        "identification": {
            "pan_number": _first(h.get("pan_number"), pd.get("pan_number")),
            "aadhaar_number": h.get("aadhaar_number"),
            **{k: h.get(k) for k in (
                "immigration_status", "personal_tax_id", "social_insurance_number", "id_proof_type",
                "id_document_name", "id_document_number", "address_proof_type", "address_document_name",
                "address_document_number")},
        },
        "employment": {
            **{k: h.get(k) for k in ("pay_grade", "workstation_id", "time_zone", "shift_start_time",
                                     "shift_end_time", "note", "employment_type")},
            "employment_type_raw": h.get("employment_type_raw"),
        },
        "statutory": pp.get("statutory"),
        "bank": pp.get("bank"),
        "department_legacy_id": pp.get("department_legacy_id"), "department_name": h.get("department_name"),
        "designation_legacy_id": pp.get("designation_legacy_id"), "job_title": h.get("job_title"),
        "work_location_legacy_id": pp.get("work_location_legacy_id"),
        "hr_user": pp.get("hr_user"), "report_id": h.get("report_id"), "hrms_emp_code": h.get("emp_code"),
        "source_legacy_hrms_id": h.get("hrms_id"), "source_legacy_payroll_id": pp.get("payroll_id"),
        "payroll_uuid": pp.get("payroll_uuid"),
    })
    return rec


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    run()
    sys.exit(0)
