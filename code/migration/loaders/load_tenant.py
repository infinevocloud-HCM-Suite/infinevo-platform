"""3a — legacy Payroll `organization` -> core.tenant, core.subscription,
core.subscription_module (W-68 Task 3, W-67 R5, mapping §3.1).

Does what core.provision_tenant does, with the tenant id from config/org_mapping.json
instead of a random one, so a re-run finds the same tenant. The core.tenant insert
trigger seeds the system roles (V022). The LOP policy is load_org's, from paySchedule.

Skipped: organisations absent from the map or marked keep=false, and deleted ones.
The founder tenant is created even when its organisation row is missing from the dump,
because every HRMS row belongs to it.
"""
from __future__ import annotations

import logging
import sys
from datetime import date

from common import db, staging
from common.ids import det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp, truthy
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_tenant"
INPUTS = ["stg_payroll_organization.json"]
log = logging.getLogger(STEP)


def _timezone(org: dict | None) -> str:
    tz = text((org or {}).get("timezone"), 64)
    return tz if tz and "/" in tz else "Asia/Kolkata"


def run(conn=None) -> bool:
    tenants = Tenants.load()
    orgs = {str(o.get("id")): o for o in staging.load("stg_payroll_organization.json")}
    idmap = IdMap.load()
    q = Quarantine(STEP)

    selected: list[Tenant] = []
    for t in tenants.all():
        org = orgs.get(t.legacy_org_id)
        if org is None and not t.founder:
            q.add("ORG_NOT_IN_DUMP", "organization", t.legacy_org_id, t.tenant_id)
            continue
        if org is not None and truthy(org.get("isDeleted"), False) and not t.founder:
            q.add("ORG_DELETED", "organization", t.legacy_org_id, t.tenant_id)
            continue
        selected.append(t)
    mapped = set(tenants.by_org)
    for org_id in orgs:
        if org_id not in mapped:
            q.add("ORG_NOT_IN_MAPPING", "organization", org_id)

    def work(cur, t: Tenant) -> StepResult:
        org = orgs.get(t.legacy_org_id)
        created = to_timestamp((org or {}).get("createdDate"))
        tenant_row = {
            "tenant_id": t.tenant_id,
            "name": t.name,
            "country_code": t.country_code,
            "timezone": _timezone(org),
            "created_by": db.AUDIT_USER,
            "updated_by": db.AUDIT_USER,
        }
        if created:
            tenant_row["created_at"] = created
        db.upsert(cur, "core.tenant", [tenant_row], conflict=("tenant_id",))

        sub_id = det_id(t.tenant_id, "subscription", "1")
        db.upsert(cur, "core.subscription", [{
            "id": sub_id,
            "tenant_id": t.tenant_id,
            "status": "ACTIVE",
            "started_on": to_date((org or {}).get("createdDate")) or date.today(),
            "created_by": db.AUDIT_USER,
            "updated_by": db.AUDIT_USER,
        }], conflict=("tenant_id",), keep_on_update=("id",))
        cur.execute("SELECT id FROM core.subscription WHERE tenant_id = %s", (t.tenant_id,))
        sub_id = cur.fetchone()[0]

        # Legacy Payroll organisations used payroll; only the founder also ran HRMS.
        modules = ["PAYROLL"] + (["HRMS"] if t.founder else [])
        cur.execute(
            "SELECT module FROM core.subscription_module WHERE tenant_id = %s AND revoked_on IS NULL",
            (t.tenant_id,),
        )
        have = {r[0] for r in cur.fetchall()}
        rows = [{
            "id": det_id(t.tenant_id, "subscription_module", m),
            "tenant_id": t.tenant_id,
            "subscription_id": sub_id,
            "module": m,
            "created_by": db.AUDIT_USER,
            "updated_by": db.AUDIT_USER,
        } for m in modules if m not in have]
        db.upsert(cur, "core.subscription_module", rows)

        idmap.put("payroll.organization", t.legacy_org_id, t.tenant_id)
        return StepResult(processed=1, skipped=0)

    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, selected, staging.checksum(INPUTS), work, creates_tenants=True)
    finally:
        if own:
            conn.close()
    idmap.save()
    q.write()
    log.info("%s: %d done, %d unchanged, %d failed, %d organisations skipped",
             STEP, len(summary.done), len(summary.skipped_unchanged), len(summary.failed), q.count())
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
