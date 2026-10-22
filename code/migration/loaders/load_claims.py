"""3l — reimbursement claims and salary deductions (W-68 Task 3, mapping §3.6 rows 159-160).

  employee_reimbursement_request -> payroll.employee_reimbursement_request
  employee_deduction             -> payroll.employee_deduction (+ its core.pay_input ledger row)

Every status is carried (founder decision "carry all history", over X-14's "open only").
document_id stays NULL here; load_documents (W-69) fills it after the verified upload, and a
re-run of this step never clears it (keep_on_update).

Claims. Legacy stores a claim's type as an enum (MEDICAL, TRAVEL, FOOD, INTERNET, FUEL,
OTHER - EmployeeReimbursementRequest.java reimbursementType) and has no link to the
reimbursement component master; the target needs reimbursement_id NOT NULL. The claim is
matched to a legacy `reimbursements` master of its organisation whose type or name names
the same thing ("Internet Reimbursement" for INTERNET), and that master's target id is
read from the id-map key load_salary publishes ("payroll.reimbursements"). No matching
master -> NO_MATCHING_COMPONENT; a match the salary step did not load -> COMPONENT_NOT_LOADED.
Status PENDING -> SUBMITTED, APPROVED, REJECTED as is. payment_status (PAID, UNPAID,
INPAYRUN) has no target column; no pay_input is written for a claim.

Deductions. The target ledger needs a core.pay_input row for every deduction
(pay_input_id NOT NULL), written the way EmployeeDeductionServiceImpl does it: kind
AD_HOC_DEDUCTION, source_module 'payroll', source_ref 'employee_deduction:<id>'. Status is
POSTED (the target has POSTED and REVERSED only). A deduction not yet taken (ACTIVE,
INPAYRUN) posts to its own month or, when that month is locked, the next open one - as
PayInputServiceImpl.nextOpenPeriod does. One already taken (PROCESSED) is not carried and is
quarantined ALREADY_DEDUCTED_IN_LEGACY: its money is in the migrated pay run totals, and a
POSTED pay_input would let a target pay run deduct it again. pay_input is append-only, so an
existing row is never rewritten.
"""
from __future__ import annotations

import logging
import sys
from collections import defaultdict

from common import db, staging
from common.ids import det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp
from common.money import to_money
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_claims"
CLAIMS = "stg_payroll_employee_reimbursement_request.json"
DEDUCTIONS = "stg_payroll_employee_deduction.json"
MASTERS = "stg_payroll_reimbursements.json"
# The id-map entries this step reads come from these files (employees, reimbursement masters).
INPUTS = [CLAIMS, DEDUCTIONS, MASTERS, "stg_payroll_organization.json",
          "stg_merged_employees.json", "stg_payroll_employee.json"]
log = logging.getLogger(STEP)

CLAIM_STATUS = {"PENDING": "SUBMITTED", "SUBMITTED": "SUBMITTED", "APPROVED": "APPROVED", "REJECTED": "REJECTED"}
DEDUCTION_TYPES = ("ADVANCE_RECOVERY", "LOAN_RECOVERY", "DAMAGE", "PENALTY", "EXCESS_PAYMENT", "OTHER")
OPEN_DEDUCTION = {"ACTIVE", "INPAYRUN"}
TAKEN_DEDUCTION = {"PROCESSED"}
PAY_INPUT_SOURCE = "payroll"
PAY_INPUT_REF = "employee_deduction:"


def deduction_type(raw) -> str:
    """Legacy free text -> the target's closed list; anything else is OTHER (reason keeps the words).
    Same rules as the branch loader (load_claims.py normalize_deduction_type)."""
    s = (text(raw) or "").upper().replace(" ", "_").replace("-", "_")
    if s in DEDUCTION_TYPES:
        return s
    for word, code in (("ADVANCE", "ADVANCE_RECOVERY"), ("LOAN", "LOAN_RECOVERY"), ("DAMAGE", "DAMAGE"),
                       ("PENALTY", "PENALTY"), ("FINE", "PENALTY"), ("EXCESS", "EXCESS_PAYMENT"),
                       ("OVERPAY", "EXCESS_PAYMENT")):
        if word in s:
            return code
    return "OTHER"


def _period(value) -> str | None:
    d = to_date(value)
    return f"{d.year:04d}-{d.month:02d}" if d else None


def _next_period(p: str) -> str:
    y, m = int(p[:4]), int(p[5:])
    return f"{y + (m == 12):04d}-{(m % 12) + 1:02d}"


def _norm(s) -> str:
    return " ".join((text(s) or "").lower().replace("_", " ").split())


def match_master(claim_type: str, masters: list[dict]) -> dict | None:
    """The org's reimbursement master for a legacy claim type: one whose type or name is the
    type word (with or without 'reimbursement'), else one that contains it. Lowest id wins."""
    word = _norm(claim_type)
    if not word:
        return None
    live = sorted((m for m in masters if not m.get("isDeleted")), key=lambda m: int(m.get("id") or 0))
    names = [(m, {_norm(m.get("reimbursementType")), _norm(m.get("reimbursementName")),
                  _norm(m.get("displayName"))} - {""}) for m in live]
    for m, ns in names:
        if any(n == word or n == f"{word} reimbursement" for n in ns):
            return m
    for m, ns in names:
        if any(word in n.split() for n in ns):
            return m
    return None


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)

    org_by_code = {str(o.get("organizationId")): o.get("id")
                   for o in staging.load("stg_payroll_organization.json") if o.get("organizationId")}
    masters_by_org = defaultdict(list)
    for m in staging.load(MASTERS):
        masters_by_org[str(m.get("organizationId"))].append(m)

    def org_tenant(org_ref):
        """Legacy claims carry organization.organizationId (a code); older rows the numeric id."""
        legacy = org_by_code.get(str(org_ref), org_ref)
        t = tenants.for_org(legacy)
        return t.tenant_id if t else None

    pre_skipped = defaultdict(int)  # refused before the tenant's transaction, counted in its ledger row

    def refuse(reason, table, lid, tid, **detail):
        q.add(reason, table, lid, tid, **detail)
        if tid:
            pre_skipped[tid] += 1

    def place(table: str, r: dict):
        """(tenant_id, employee_id) for a Payroll row keyed by the Keycloak/employee UUID, or None."""
        emp = idmap.get("payroll.employee.uuid", r.get("employee_id"))
        org_tid = org_tenant(r.get("organization_id"))
        if not emp:
            refuse("EMPLOYEE_NOT_MIGRATED", table, r.get("id"), org_tid)
            return None
        tid = idmap.get("employee.tenant", emp)
        if org_tid is None:
            refuse("ORG_NOT_MIGRATED", table, r.get("id"), tid)
            return None
        if org_tid != tid:
            refuse("TENANT_MISMATCH", table, r.get("id"), tid)
            return None
        return tid, emp

    claims_by_tenant = defaultdict(list)
    for r in staging.load(CLAIMS):
        p = place("employee_reimbursement_request", r)
        if p:
            claims_by_tenant[p[0]].append((r, p[1]))
    deds_by_tenant = defaultdict(list)
    for r in staging.load(DEDUCTIONS):
        p = place("employee_deduction", r)
        if p:
            deds_by_tenant[p[0]].append((r, p[1]))

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
        before = q.count(tid) - pre_skipped[tid]

        # ---- claims
        cur.execute("SELECT id::text FROM payroll.reimbursement WHERE tenant_id = %s", (tid,))
        loaded_masters = {r[0] for r in cur.fetchall()}
        claims = []
        for r, emp in claims_by_tenant.get(tid, []):
            lid = r.get("id")
            status = CLAIM_STATUS.get((text(r.get("status")) or "").upper())
            requested = to_money(r.get("requested_amount"))
            approved = to_money(r.get("approved_amount"))
            bill_date = to_date(r.get("bill_date"))
            master = match_master(r.get("reimbursement_type"), masters_by_org.get(t.legacy_org_id, []))
            comp = None
            if master is not None:
                comp = idmap.get("payroll.reimbursements", master.get("id")) or \
                    idmap.get("payroll.reimbursements", master.get("reimbursementId")) or \
                    det_id(tid, "reimbursements", master.get("id"))
            problem = (
                "STATUS_UNMAPPED" if status is None else
                "AMOUNT_NOT_POSITIVE" if requested is None or requested <= 0 else
                "APPROVED_AMOUNT_NEGATIVE" if approved is not None and approved < 0 else
                "NO_BILL_DATE" if bill_date is None else
                "NO_MATCHING_COMPONENT" if master is None else
                "COMPONENT_NOT_LOADED" if comp not in loaded_masters else None
            )
            if problem:
                q.add(problem, "employee_reimbursement_request", lid, tid,
                      status=text(r.get("status")) if problem == "STATUS_UNMAPPED" else None,
                      reimbursement_type=text(r.get("reimbursement_type"))
                      if problem in ("NO_MATCHING_COMPONENT", "COMPONENT_NOT_LOADED") else None)
                continue
            cid = det_id(tid, "employee_reimbursement_request", lid)
            claims.append({
                "id": cid, "tenant_id": tid, "employee_id": emp, "reimbursement_id": comp,
                "requested_amount": requested, "approved_amount": approved, "bill_date": bill_date,
                "description": text(r.get("description"), 500), "document_id": None, "status": status,
                "remarks": text(r.get("remarks"), 500),
                "approved_by": idmap.get("payroll.employee.uuid", r.get("approved_by")),
                "approved_at": to_timestamp(r.get("approved_at")),
                "created_at": to_timestamp(r.get("created_at")), **audit,
            })
            idmap.put("payroll.employee_reimbursement_request", lid, cid)
        if any(c["created_at"] is None for c in claims):
            for c in claims:
                c.pop("created_at")  # one column set per statement; the default stamps the run
        db.upsert(cur, "payroll.employee_reimbursement_request", claims, keep_on_update=("document_id",))

        # ---- deductions and their pay_input ledger rows
        cur.execute("SELECT period FROM core.pay_input_period_lock WHERE tenant_id = %s AND run_ref IS NULL", (tid,))
        locked = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT id::text, period FROM core.pay_input WHERE tenant_id = %s AND source_module = %s",
                    (tid, PAY_INPUT_SOURCE))
        existing_inputs = dict(cur.fetchall())
        deds, inputs = [], []
        for r, emp in deds_by_tenant.get(tid, []):
            lid = r.get("id")
            raw_status = (text(r.get("status")) or "").upper()
            amount = to_money(r.get("deduction_amount"))
            period = _period(r.get("deduction_month"))
            reason = text(r.get("reason"), 255)
            problem = (
                "STATUS_UNMAPPED" if raw_status not in OPEN_DEDUCTION | TAKEN_DEDUCTION else
                "AMOUNT_NOT_POSITIVE" if amount is None or amount <= 0 else
                "NO_PERIOD" if period is None else
                "NO_REASON" if reason is None else None
            )
            if problem:
                q.add(problem, "employee_deduction", lid, tid,
                      status=(raw_status or None) if problem == "STATUS_UNMAPPED" else None)
                continue
            did = det_id(tid, "employee_deduction", lid)
            pid = det_id(tid, "employee_deduction.pay_input", lid)
            if pid in existing_inputs:
                posted = existing_inputs[pid]
            else:
                posted = period
                if raw_status in OPEN_DEDUCTION:
                    while posted in locked:
                        posted = _next_period(posted)
                else:
                    # Already taken in legacy (PROCESSED): the migrated pay run totals carry
                    # it. A POSTED pay_input in any month - locked or not - could be taken a
                    # second time by a target pay run, so it is never written.
                    q.add("ALREADY_DEDUCTED_IN_LEGACY", "employee_deduction", lid, tid, period=period)
                    continue
                inputs.append({"id": pid, "tenant_id": tid, "employee_id": emp, "period": posted,
                               "kind": "AD_HOC_DEDUCTION", "amount": amount, "source_module": PAY_INPUT_SOURCE,
                               "source_ref": PAY_INPUT_REF + did, **audit})
            deds.append({
                "id": did, "tenant_id": tid, "employee_id": emp, "period": period,
                "deduction_type": deduction_type(r.get("deduction_type")), "amount": amount,
                "reason": reason, "remarks": text(r.get("remarks"), 500), "document_id": None,
                "status": "POSTED", "pay_input_id": pid, "posted_period": posted,
                "created_at": to_timestamp(r.get("created_at")), **audit,
            })
            idmap.put("payroll.employee_deduction", lid, did)
        if any(d["created_at"] is None for d in deds):
            for d in deds:
                d.pop("created_at")
        # Append-only ledger: new rows are inserted, an existing one is never rewritten.
        db.upsert(cur, "core.pay_input", inputs)
        db.upsert(cur, "payroll.employee_deduction", deds, keep_on_update=("document_id",))
        return StepResult(processed=len(claims) + len(deds), skipped=q.count(tid) - before)

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
