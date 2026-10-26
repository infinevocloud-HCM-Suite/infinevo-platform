"""W-67 R1 (2026-10-26) — the founder's map row says hrms_only=false.

HRMS-only employees (cohort 2: E011..E020 in fx_employees.py) stay behind, and with them
every HRMS row and login that is theirs; matched and Payroll-only employees still load, and
reconciliation still comes out clean because every row left behind is quarantined with a reason.
"""
import json

import pytest

from tests.fixtures import FOUNDER_TENANT, LIVE_TENANTS, ORG_MAPPING
from tests.pipeline import ORDER, run_steps

HRMS_ONLY_EMAILS = [f"e{n}@founder.test" for n in range(11, 21)]


@pytest.fixture
def hrms_only_off(legacy_fixture, tmp_path, monkeypatch):
    mapping = json.loads(json.dumps(ORG_MAPPING))
    founder = next(e for e in mapping.values() if e.get("founder"))
    founder["hrms_only"] = False
    p = tmp_path / "org_mapping_hrms_only_off.json"
    p.write_text(json.dumps(mapping), encoding="utf-8")
    monkeypatch.setenv("MIGRATION_ORG_MAPPING", str(p))
    monkeypatch.setenv("MIGRATION_KEYCLOAK", "off")
    monkeypatch.setenv("MIGRATION_FY", "2025-26")  # the tax fixture's financial year (fx_tax.py)
    return legacy_fixture


def test_cohort_2_is_quarantined_not_merged(hrms_only_off):
    from transformers import merge_employees

    merged = merge_employees.run()
    cohorts = {}
    for m in merged:
        cohorts[m["cohort"]] = cohorts.get(m["cohort"], 0) + 1
    assert cohorts == {"COHORT_1_BOTH": 10, "COHORT_3_PAYROLL_ONLY": 4}
    q = json.loads((hrms_only_off / "stg_quarantined_duplicates.json").read_text())
    assert sum(1 for i in q if i["reason"] == "HRMS_ONLY_NOT_MIGRATED") == 10


def test_hrms_only_people_and_their_rows_stay_behind(hrms_only_off, query):
    run_steps(ORDER + ["reconciliation.lop_mismatch_generator"])

    emails = {r[0] for r in query("SELECT lower(work_email) FROM core.employee WHERE tenant_id = %s",
                                  (FOUNDER_TENANT,))}
    assert not emails & set(HRMS_ONLY_EMAILS)
    assert "e1@founder.test" in emails  # matched (cohort 1) still loads

    # e15's login is tied to an HRMS-only employee: no account. ops@ has no employee link: kept.
    users = json.loads((hrms_only_off / "stg_quarantined_load_users.json").read_text())
    assert any(i["reason"] == "EMPLOYEE_NOT_MIGRATED" and str(i["legacy_id"]) == "4" for i in users)
    assert not any(str(i["legacy_id"]) == "5" and i["legacy_table"] == "ourusers" for i in users
                   if i["reason"] == "EMPLOYEE_NOT_MIGRATED")

    from reconciliation import financial_reconciliation as fr

    assert fr.run() is True
    for tid in LIVE_TENANTS:
        result = json.loads(sorted(hrms_only_off.glob(f"reconciliation_{tid}_*.json"))[-1].read_text())
        assert result["clean"], (tid, [c for c in result["counts"] if c["delta"]])


def test_default_map_still_loads_cohort_2(legacy_fixture):
    from common.tenants import Tenants

    assert Tenants.load().founder.hrms_only is True
