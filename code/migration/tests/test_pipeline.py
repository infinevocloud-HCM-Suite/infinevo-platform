"""W-68 Task 4 — the whole pipeline, twice.

Runs every step in W-68 §3 order over the full fixture, with Keycloak faked, and asserts:
- every target table the migration carries has rows (one list here, so a loader added
  without a fixture fails this test);
- a second run of the whole pipeline skips every step for every tenant by ledger, and
  changes no row anywhere: same counts, same max(updated_at).
load_documents is not in tests/pipeline.ORDER: it needs Cloudinary and Blob storage and
has its own tests (tests/test_load_documents.py).
"""
import uuid

import pytest

from tests.fixtures import FOUNDER_TENANT, LIVE_TENANTS
from tests.pipeline import ORDER, run_steps

# Every table a loader in ORDER writes. Documents (W-69) and prior_payroll_month are left
# out on purpose: documents need storage; prior months are written only for a period with
# no migrated run, which the fixture does not have (load_payruns).
CARRIED = [
    "core.tenant", "core.subscription", "core.subscription_module",
    "core.department", "core.designation", "core.work_location", "payroll.pay_schedule", "core.lop_policy",
    "payroll.tax_deductor",
    "core.employee", "core.employee_personal", "core.employee_contact", "core.employee_identification",
    "core.employee_employment", "core.employee_bank", "payroll.employee_statutory_profile", "core.reporting_line",
    "core.user_account", "core.user_tenant", "core.user_role", "core.user_invitation", "core.user_invitation_role",
    "core.employee_invitation",
    "payroll.earning", "payroll.deduction", "payroll.benefit", "payroll.reimbursement", "payroll.fbp",
    "payroll.ctc_structure", "payroll.employee_earning", "payroll.employee_benefit",
    "payroll.employee_reimbursement", "payroll.employee_fbp_component", "payroll.ctc_epf_component",
    "payroll.ctc_esi_component",
    "payroll.epf_setting", "payroll.esi_setting", "payroll.org_pt_override", "payroll.org_pt_override_slab",
    "payroll.payrun", "payroll.employee_payrun", "payroll.employee_payrun_line", "payroll.scheduled_earning",
    "payroll.income_tax_declaration", "payroll.employee_investment_declaration", "payroll.employee_inv_house_rent",
    "payroll.employee_inv_home_loan", "payroll.employee_inv_let_out_property",
    "payroll.employee_inv_let_out_property_line", "payroll.employee_inv_other_income",
    "payroll.employee_inv_pre_tax_deduction", "payroll.employee_inv_prev_employment",
    "payroll.employee_inv_section6a", "payroll.employee_inv_tax_summary", "payroll.employee_tds",
    "payroll.employee_proof_of_investment", "payroll.employee_proof_item", "payroll.employee_proof_item_comment",
    "core.leave_type", "core.leave_policy", "core.leave_policy_eligibility", "core.leave_allocation",
    "core.leave_consumption", "core.leave_request", "core.approval_instance", "core.approval_step",
    "core.holiday", "core.holiday_calendar", "core.holiday_calendar_location", "hrms.attendance_preference",
    "hrms.project", "hrms.task", "hrms.assignment", "hrms.timesheet", "hrms.timesheet_project_entry",
    "hrms.timesheet_task_entry", "hrms.timesheet_day_entry",
    "core.attendance", "hrms.clock_session", "core.overtime_request",
    "payroll.employee_reimbursement_request", "payroll.employee_deduction", "core.pay_input",
    "core.notification",
]
LEDGER_STEPS = [m.rsplit(".", 1)[1] for m in ORDER if m.startswith("loaders.")]
# HRMS-only data belongs to the founder tenant (W-67 R5); these steps run no other tenant.
FOUNDER_ONLY = {"load_timesheets", "load_attendance"}


class _FakeKeycloak:
    def __init__(self):
        self.users = {}

    def find_user_by_email(self, email):
        return self.users.get(email)

    def create_user(self, email, first_name, last_name):
        self.users[email] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"kc:{email}"))
        return self.users[email]


@pytest.fixture
def full_run(legacy_fixture, monkeypatch):
    from loaders import load_users

    kc = _FakeKeycloak()
    monkeypatch.delenv("MIGRATION_KEYCLOAK", raising=False)
    monkeypatch.setattr(load_users, "keycloak_client", lambda: kc)
    # the tax fixture's financial year (tests/fixtures/fx_tax.py)
    monkeypatch.setenv("MIGRATION_FY", "2025-26")
    return lambda: run_steps(ORDER)


def test_every_carried_table_has_rows(full_run, query):
    full_run()
    empty = [t for t in CARRIED if query(f"SELECT count(*) FROM {t}")[0][0] == 0]
    assert empty == []


def test_second_full_run_skips_every_step_and_changes_nothing(full_run, query):
    full_run()
    snapshot = lambda: {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0]  # noqa: E731
                        if t != "payroll.employee_proof_item_comment" else query(f"SELECT count(*) FROM {t}")[0]
                        for t in CARRIED}
    before = snapshot()
    ledger_before = query("SELECT step_name, tenant_id::text, status, completed_at FROM core.migration_ledger "
                          "ORDER BY 1, 2")
    full_run()
    assert snapshot() == before
    # same ledger rows, untouched: every step skipped every tenant on the second run
    assert query("SELECT step_name, tenant_id::text, status, completed_at FROM core.migration_ledger "
                 "ORDER BY 1, 2") == ledger_before
    done = {(s, t) for s, t, st, _ in ledger_before if st == "DONE"}
    expected = {(s, t) for s in LEDGER_STEPS for t in LIVE_TENANTS
                if not (s in FOUNDER_ONLY and t != FOUNDER_TENANT)}
    assert done == expected
