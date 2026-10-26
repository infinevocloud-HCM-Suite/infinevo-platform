"""W-67 R1 (2026-10-26) — the founder's map row says hrms_only=false.

HRMS-only employees (cohort 2: E011..E020 in fx_employees.py) are quarantined by the merge,
and no Keycloak user or account is made for their logins; matched and Payroll-only employees
still load. The fixture holds no leave, attendance, timesheet or document row for E011..E020,
so those loaders' employee guards are proven by their own tests (the E999 cases), not here.
Reconciliation is asserted clean only to show the exclusion is quarantined with a reason: a
cohort-2 row that leaked would count on both sides, so "clean" is not evidence of scope.
"""
import json

import pytest

from tests.fixtures import FOUNDER_TENANT, LIVE_TENANTS, ORG_MAPPING
from tests.pipeline import ORDER, run_steps
from tests.test_load_users import FakeKeycloak

HRMS_ONLY_EMAILS = {f"e{n}@founder.test" for n in range(11, 21)}


def _write_map(tmp_path, monkeypatch, hrms_only):
    mapping = json.loads(json.dumps(ORG_MAPPING))
    next(e for e in mapping.values() if e.get("founder"))["hrms_only"] = hrms_only
    p = tmp_path / "org_mapping_hrms_only.json"
    p.write_text(json.dumps(mapping), encoding="utf-8")
    monkeypatch.setenv("MIGRATION_ORG_MAPPING", str(p))


@pytest.fixture
def hrms_only_off(legacy_fixture, tmp_path, monkeypatch):
    from loaders import load_users

    _write_map(tmp_path, monkeypatch, False)
    kc = FakeKeycloak()
    monkeypatch.delenv("MIGRATION_KEYCLOAK", raising=False)
    monkeypatch.setattr(load_users, "keycloak_client", lambda: kc)
    monkeypatch.setenv("MIGRATION_FY", "2025-26")  # the tax fixture's financial year (fx_tax.py)
    return legacy_fixture, kc


def test_cohort_2_is_quarantined_not_merged(hrms_only_off):
    from transformers import merge_employees

    staging_dir, _ = hrms_only_off
    merged = merge_employees.run()
    cohorts = {}
    for m in merged:
        cohorts[m["cohort"]] = cohorts.get(m["cohort"], 0) + 1
    assert cohorts == {"COHORT_1_BOTH": 10, "COHORT_3_PAYROLL_ONLY": 4}
    q = json.loads((staging_dir / "stg_quarantined_duplicates.json").read_text())
    assert sum(1 for i in q if i["reason"] == "HRMS_ONLY_NOT_MIGRATED") == 10


def test_hrms_only_people_and_logins_stay_behind(hrms_only_off, query):
    staging_dir, kc = hrms_only_off
    run_steps(ORDER + ["reconciliation.lop_mismatch_generator"])

    emails = {r[0] for r in query("SELECT lower(work_email) FROM core.employee WHERE tenant_id = %s",
                                  (FOUNDER_TENANT,))}
    assert not emails & HRMS_ONLY_EMAILS
    assert "e1@founder.test" in emails  # matched (cohort 1) still loads

    # e15's login belongs to an HRMS-only employee: no Keycloak user, no account.
    # ops@ has no employee link, so it is not an HRMS-only person's login: kept.
    assert "e15@founder.test" not in kc.created
    assert {"e1@founder.test", "ops@founder.test"} <= set(kc.created)
    accounts = {r[0] for r in query("SELECT email FROM core.user_account WHERE tenant_id = %s", (FOUNDER_TENANT,))}
    assert "e15@founder.test" not in accounts and "ops@founder.test" in accounts

    from reconciliation import financial_reconciliation as fr

    assert fr.run() is True
    for tid in LIVE_TENANTS:
        result = json.loads(sorted(staging_dir.glob(f"reconciliation_{tid}_*.json"))[-1].read_text())
        assert result["clean"], (tid, [c for c in result["counts"] if c["delta"]])


def test_stale_id_map_does_not_bring_a_login_back(hrms_only_off, query):
    """id_map.json only grows: an entry left by an earlier run with cohort 2 loaded must not
    count as a migrated employee."""
    from transformers.merge_employees import employee_uuid

    staging_dir, kc = hrms_only_off
    stale = employee_uuid(FOUNDER_TENANT, "e15@founder.test")
    (staging_dir / "id_map.json").write_text(json.dumps({"hrms.employee": {"15": stale}}), encoding="utf-8")

    run_steps(ORDER[:5])  # merge, tenant, org, employees, users
    assert "e15@founder.test" not in kc.created
    assert query("SELECT count(*) FROM core.user_account WHERE email = 'e15@founder.test'")[0][0] == 0


def test_default_map_still_loads_cohort_2(legacy_fixture):
    from common.tenants import Tenants

    assert Tenants.load().founder.hrms_only is True


def test_quoted_flag_is_refused(legacy_fixture, tmp_path, monkeypatch):
    from common.tenants import Tenants

    _write_map(tmp_path, monkeypatch, "false")
    with pytest.raises(ValueError, match="hrms_only must be true or false"):
        Tenants.load()
