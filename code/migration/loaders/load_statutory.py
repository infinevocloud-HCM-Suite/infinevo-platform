"""3f — statutory settings (W-68 Task 3, mapping §3.5).

  epf                                   -> payroll.epf_setting (one row per tenant)
  esi                                   -> payroll.esi_setting (one row per tenant)
  professionalTax + slabRateConfiguration + slabDetail (+ slabDeductionMonths) + org_pt_override
                                        -> payroll.org_pt_override + payroll.org_pt_override_slab,
                                           only for a state whose slabs differ from reference.pt_slab

Professional tax. The legacy showed an org the master slabs of a state unless org_pt_override held
its own (ProfessionalTaxServiceImpl.java:851-870: the override's slabs replace the master's). The
older professionalTax / slabRateConfiguration / slabDetail tables hold per-org slabs too. Per state:
the override's slabs when it has any, else the professionalTax slabs (the active rate
configuration's when there is one). Those are compared with the current reference.pt_slab rows of
the state - the target's equivalent of the legacy master - and written only when they differ.

Rates. The legacy stored contribution rates as text: '12_actual', 'restrict_15000', '0.75%'. The
leading number is the rate (parseContributionRate, editESI.js:91-104); text with no number keeps the
target default, which is the statutory rate. 'restrict...' sets the wage-ceiling restriction
(editEPF.js:109-121).
"""
from __future__ import annotations

import json
import logging
import re
import sys
from collections import defaultdict
from decimal import Decimal

from common import db, staging
from common.ids import child_id, det_id
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp, truthy
from common.money import to_decimal, to_money
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_statutory"
INPUTS = [
    "stg_payroll_epf.json", "stg_payroll_esi.json", "stg_payroll_professionalTax.json",
    "stg_payroll_slabRateConfiguration.json", "stg_payroll_slabDetail.json",
    "stg_payroll_slabDeductionMonths.json", "stg_payroll_org_pt_override.json",
    # org_pt_override.organization_id is the organisation's string organizationId
    "stg_payroll_organization.json",
]
RATE = Decimal("0.0001")
log = logging.getLogger(STEP)
_MONTHS = {m: i for i, m in enumerate(
    ("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"), start=1)}


def _rate(v) -> Decimal | None:
    if v is None:
        return None
    m = re.match(r"^\s*(\d+(?:\.\d+)?)", str(v))
    return to_decimal(m.group(1), RATE) if m else None


def _restricted(v) -> bool:
    return "restrict" in (text(v) or "").lower()


def _cycle(v) -> str | None:
    """DeductionCycle has MONTHLY only. None when the legacy says something else."""
    s = (text(v) or "monthly").lower()
    return "MONTHLY" if s == "monthly" else None


def _latest(rows: list[dict]) -> tuple[dict | None, list[dict]]:
    rows = sorted(rows, key=lambda r: int(r.get("id") or 0))
    return (rows[-1], rows[:-1]) if rows else (None, [])


def _months(values) -> str | None:
    """Legacy month list (numbers or names) -> target '2,3' (PtSlabDto.formatMonths); every month
    or none -> NULL. Raises ValueError on a month it cannot read."""
    out = set()
    for v in values or []:
        s = str(v).strip().lower()
        if not s:
            continue
        if s.isdigit() and 1 <= int(s) <= 12:
            out.add(int(s))
        elif s[:3] in _MONTHS:
            out.add(_MONTHS[s[:3]])
        else:
            raise ValueError(s)
    if not out or len(out) == 12:
        return None
    return ",".join(str(m) for m in sorted(out))


def _slab(start, end, pay, female, months) -> tuple:
    return (to_money(start) or Decimal("0.00"), to_money(end), to_money(pay) or Decimal("0.00"),
            bool(truthy(female, False)), _months(months))


def _sorted(slabs: list[tuple]) -> list[tuple]:
    return sorted(slabs, key=lambda s: (s[0], s[4] or "", s[2]))


def run(conn=None) -> bool:
    tenants = Tenants.load()
    q = Quarantine(STEP)

    def by_org(rows, key="organizationId"):
        out = defaultdict(list)
        for r in rows:
            out[str(r.get(key))].append(r)
        return out

    epf = by_org(staging.load("stg_payroll_epf.json"))
    esi = by_org(staging.load("stg_payroll_esi.json"))
    pts = by_org(staging.load("stg_payroll_professionalTax.json"))
    configs = defaultdict(list)
    for c in staging.load("stg_payroll_slabRateConfiguration.json"):
        configs[str(c.get("professionalTaxId"))].append(c)
    slabs_by_pt, slabs_by_cfg = defaultdict(list), defaultdict(list)
    for s in staging.load("stg_payroll_slabDetail.json"):
        if s.get("slabRateConfigId") is not None:
            slabs_by_cfg[str(s.get("slabRateConfigId"))].append(s)
        if s.get("professionalTaxId") is not None:
            slabs_by_pt[str(s.get("professionalTaxId"))].append(s)
    months = defaultdict(list)
    for m in staging.load("stg_payroll_slabDeductionMonths.json"):
        months[str(m.get("slabId"))].append(m.get("deductionMonth"))
    # org_pt_override.organization_id holds Organization.organizationId (a string), not the numeric
    # id every other table joins on (OrgPTOverride.java:21-22, ProfessionalTaxServiceImpl.java:248).
    org_by_string = {str(o.get("organizationId")): str(o.get("id"))
                     for o in staging.load("stg_payroll_organization.json") if o.get("organizationId")}
    org_ids = {str(o.get("id")) for o in staging.load("stg_payroll_organization.json")}
    overrides = defaultdict(list)
    for o in staging.load("stg_payroll_org_pt_override.json"):
        raw = str(o.get("organization_id"))
        org = org_by_string.get(raw) or (raw if raw in org_ids else None)
        if org is None or tenants.for_org(org) is None:
            q.add("ORG_NOT_MIGRATED", "org_pt_override", o.get("id"), organization_id=raw)
            continue
        overrides[org].append(o)
    for name, rows in (("epf", epf), ("esi", esi), ("professionalTax", pts)):
        for org, rs in rows.items():
            if tenants.for_org(org) is None:
                for r in rs:
                    q.add("ORG_NOT_MIGRATED", name, r.get("id"), organization_id=org)

    def work(cur, t: Tenant) -> StepResult:
        tid, org = t.tenant_id, t.legacy_org_id
        audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
        res = StepResult()

        # ---------------- EPF
        e, dupes = _latest(epf.get(org, []))
        for d in dupes:
            q.add("SECOND_SETTING_ROW", "epf", d.get("id"), tid, kept=e.get("id")); res.skipped += 1
        if e:
            cycle = _cycle(e.get("deductionCycle"))
            if cycle is None:
                q.add("DEDUCTION_CYCLE_UNMAPPED", "epf", e.get("id"), tid, value=text(e.get("deductionCycle"), 32))
                res.skipped += 1
            else:
                edli_ctc = bool(truthy(e.get("isEdliIncludedCtc"), False))
                admin_ctc = bool(truthy(e.get("isAdminChargesIncludedCtc"), False))
                edli_ss = bool(truthy(e.get("isEdliIncludedSalaryStructure"), False))
                admin_ss = bool(truthy(e.get("isAdminChargesIncludedSalaryStructure"), False))
                row = {
                    "id": det_id(tid, "epf", e.get("id")), "tenant_id": tid,
                    "is_enabled": bool(truthy(e.get("isActive"), False)),
                    "registration_number": text(e.get("registrationNumber"), 32),
                    "registration_date": to_date(e.get("registrationDate")),
                    "deduction_cycle": cycle,
                    "employee_rate": _rate(e.get("epfEmployeeContribution")),
                    "employer_rate": _rate(e.get("epfEmployerContribution")),
                    "eps_rate": _rate(e.get("epsEmployerContribution")),
                    "edli_rate": _rate(e.get("edliEmployerContribution")),
                    "admin_charge_rate": _rate(e.get("epfAdminChargesEmployerContribution")),
                    "restrict_employee_to_ceiling": bool(truthy(e.get("isEmployeeRestrictedBasicEnabled"), False))
                    or _restricted(e.get("epfEmployeeContribution")),
                    "restrict_employer_to_ceiling": bool(truthy(e.get("isEmployerRestrictedBasicEnabled"), False))
                    or _restricted(e.get("epfEmployerContribution")),
                    "prorate_restricted_wage": bool(truthy(e.get("canProRateRestrictedBasic"), False)),
                    "consider_earned_wage": bool(truthy(e.get("considerEarnedSalaryForEpf"), True)),
                    "eps_senior_age": e.get("epsSeniorCategoryAge") if str(e.get("epsSeniorCategoryAge") or "").isdigit() else None,
                    "include_employer_in_ctc": bool(truthy(e.get("isEmployerContributionIncludedCtc"), False)),
                    "include_edli_in_ctc": edli_ctc, "include_admin_in_ctc": admin_ctc,
                    # the pre-V153 combined flags: on only when both halves are on
                    "include_edli_admin_in_ctc": edli_ctc and admin_ctc,
                    "include_employer_in_structure": bool(truthy(e.get("isEmployerContributionIncludedSalaryStructure"), False)),
                    "include_edli_in_structure": edli_ss, "include_admin_in_structure": admin_ss,
                    "include_edli_admin_in_structure": edli_ss and admin_ss,
                    "abry_scheme": bool(truthy(e.get("isEligibleForAbryScheme"), False)),
                    **audit,
                }
                # A rate the legacy text does not state keeps the target's statutory default.
                row = {k: v for k, v in row.items()
                       if not (v is None and k in ("employee_rate", "employer_rate", "eps_rate", "edli_rate",
                                                   "admin_charge_rate", "eps_senior_age"))}
                db.upsert(cur, "payroll.epf_setting", [row], conflict=("tenant_id",), keep_on_update=("id",))
                res.processed += 1

        # ---------------- ESI
        s, dupes = _latest(esi.get(org, []))
        for d in dupes:
            q.add("SECOND_SETTING_ROW", "esi", d.get("id"), tid, kept=s.get("id")); res.skipped += 1
        if s:
            cycle = _cycle(s.get("deductionCycle"))
            if cycle is None:
                q.add("DEDUCTION_CYCLE_UNMAPPED", "esi", s.get("id"), tid, value=text(s.get("deductionCycle"), 32))
                res.skipped += 1
            else:
                in_ctc = truthy(s.get("canEnableEmployerEsiInCtc"))
                if in_ctc is None:
                    in_ctc = truthy(s.get("isIncludedInCtc"), False)
                row = {
                    "id": det_id(tid, "esi", s.get("id")), "tenant_id": tid,
                    "is_enabled": bool(truthy(s.get("isActive"), False)),
                    "registration_number": text(s.get("registrationNumber"), 32),
                    "registration_date": to_date(s.get("registrationDate")),
                    "deduction_cycle": cycle,
                    "employee_rate": _rate(s.get("employeeContribution")),
                    "employer_rate": _rate(s.get("employerContribution")),
                    "include_employer_in_ctc": bool(in_ctc),
                    "include_in_structure": bool(truthy(s.get("isIncludedInSalaryStructure"), False)),
                    **audit,
                }
                row = {k: v for k, v in row.items() if not (v is None and k in ("employee_rate", "employer_rate"))}
                db.upsert(cur, "payroll.esi_setting", [row], conflict=("tenant_id",), keep_on_update=("id",))
                res.processed += 1

        # ---------------- Professional tax
        cur.execute("SELECT lower(name), code FROM reference.state")
        state_by_name = dict(cur.fetchall())
        cur.execute("SELECT state_code FROM reference.pt_state")
        pt_states = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT state_code, from_amount, to_amount, amount, is_female_exempt, deduction_months "
                    "FROM reference.pt_slab WHERE effective_to IS NULL ORDER BY state_code, sort_order")
        reference = defaultdict(list)
        for sc, fr, to, amt, fem, mon in cur.fetchall():
            reference[sc].append((to_money(fr), to_money(to), to_money(amt), bool(fem), mon or None))

        def state_code(v) -> str | None:
            s_ = text(v)
            if not s_:
                return None
            if s_.upper() in pt_states:
                return s_.upper()
            code = state_by_name.get(s_.lower())
            return code if code in pt_states else None

        cand = {}  # state code -> candidate
        for p in sorted(pts.get(org, []), key=lambda r: int(r.get("id") or 0)):
            sc = state_code(p.get("stateCode")) or state_code(p.get("state"))
            if sc is None:
                q.add("PT_STATE_UNKNOWN", "professionalTax", p.get("id"), tid, state=text(p.get("state"), 64))
                res.skipped += 1
                continue
            if sc in cand:
                q.add("SECOND_PT_FOR_STATE", "professionalTax", p.get("id"), tid, state_code=sc)
                res.skipped += 1
                continue
            active = [c for c in configs.get(str(p.get("id")), []) if truthy(c.get("isActive"), False)]
            active.sort(key=lambda c: (str(to_date(c.get("effectiveFrom")) or ""), int(c.get("id") or 0)))
            if active:
                cfg = active[-1]
                rows = slabs_by_cfg.get(str(cfg.get("id")), [])
                eff = to_date(cfg.get("effectiveFrom")) or to_date(p.get("effectiveFrom"))
            else:
                own = slabs_by_pt.get(str(p.get("id")), [])
                rows = [r for r in own if r.get("slabRateConfigId") is None] or own
                eff = to_date(p.get("effectiveFrom"))
            try:
                slabs = [_slab(r.get("startAmount"), r.get("endAmount"), r.get("payAmount"),
                               r.get("isFemaleExempted"), months.get(str(r.get("id")), [])) for r in rows]
            except ValueError:
                q.add("PT_MONTH_UNREADABLE", "professionalTax", p.get("id"), tid, state_code=sc)
                res.skipped += 1
                continue
            cand[sc] = {"table": "professionalTax", "legacy_id": p.get("id"), "effective_from": eff,
                        "registration_number": text(p.get("registrationNumber"), 32), "slabs": slabs}

        for o in sorted(overrides.get(org, []), key=lambda r: int(r.get("id") or 0)):
            sc = state_code(o.get("state"))
            if sc is None:
                q.add("PT_STATE_UNKNOWN", "org_pt_override", o.get("id"), tid, state=text(o.get("state"), 64))
                res.skipped += 1
                continue
            js = o.get("overrideJson")
            if isinstance(js, str):
                js = json.loads(js) if js.strip() else None
            c = cand.get(sc, {"slabs": [], "registration_number": None, "effective_from": None})
            eff = to_date(o.get("effectiveFrom"))
            if eff is None and o.get("changedAt"):  # the legacy applied an override from when it was saved
                eff = to_timestamp(o.get("changedAt")).date()
            if js:
                try:
                    slabs = [_slab(j.get("startAmount"), j.get("endAmount"), j.get("payAmount"),
                                   j.get("isFemaleExempted"), j.get("deductionMonths")) for j in js]
                except ValueError:
                    q.add("PT_MONTH_UNREADABLE", "org_pt_override", o.get("id"), tid, state_code=sc)
                    res.skipped += 1
                    continue
                c = {**c, "slabs": slabs, "effective_from": eff or c.get("effective_from")}
            c = {**c, "table": "org_pt_override", "legacy_id": o.get("id"),
                 "registration_number": text(o.get("registrationNumber"), 32) or c.get("registration_number")}
            cand[sc] = c

        for sc in sorted(cand):
            c = cand[sc]
            if not c["slabs"] or _sorted(c["slabs"]) == _sorted(reference.get(sc, [])):
                q.add("PT_MATCHES_REFERENCE", c["table"], c["legacy_id"], tid, state_code=sc,
                      registration_number_dropped=bool(c.get("registration_number")) or None)
                res.skipped += 1
                continue
            if c.get("effective_from") is None:
                q.add("PT_NO_EFFECTIVE_FROM", c["table"], c["legacy_id"], tid, state_code=sc)
                res.skipped += 1
                continue
            oid = det_id(tid, "org_pt_override", sc)
            db.upsert(cur, "payroll.org_pt_override", [{
                "id": oid, "tenant_id": tid, "state_code": sc, "registration_number": c.get("registration_number"),
                "effective_from": c["effective_from"], **audit,
            }], conflict=("tenant_id", "state_code"), keep_on_update=("id",))
            cur.execute("SELECT id::text FROM payroll.org_pt_override WHERE tenant_id = %s AND state_code = %s",
                        (tid, sc))
            oid = cur.fetchone()[0]
            slab_rows = [{
                "id": child_id(oid, f"slab:{n}"), "tenant_id": tid, "override_id": oid,
                "from_amount": fr, "to_amount": to, "amount": amt, "is_female_exempt": fem,
                "deduction_months": mon, "sort_order": n, **audit,
            } for n, (fr, to, amt, fem, mon) in enumerate(_sorted(c["slabs"]), start=1)]
            db.upsert(cur, "payroll.org_pt_override_slab", slab_rows)
            cur.execute("DELETE FROM payroll.org_pt_override_slab WHERE override_id = %s AND NOT (id = ANY(%s::uuid[]))",
                        (oid, [r["id"] for r in slab_rows]))
            res.processed += 1 + len(slab_rows)
        return res

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
