"""3m load_notifications against a real migrated Postgres (W-68 Task 4)."""
import json

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and

STEP = "loaders.load_notifications"


def test_notifications_loaded_and_mapped(legacy_fixture, query):
    run_base_and(STEP)
    rows = {r[0]: r[1:] for r in query(
        "SELECT e.employee_number, n.event, n.channel, n.status, n.read_at IS NOT NULL, "
        "n.queued_at = '2024-06-03T09:00:00+05:30'::timestamptz, n.body, n.created_by "
        "FROM core.notification n JOIN core.employee e ON e.id = n.recipient_employee_id")}
    assert set(rows) == {"E001", "E002", "E004", "E005", "E006"}
    assert rows["E001"] == ("APPROVAL_PENDING", "IN_APP", "SENT", True, True,
                            "Timesheet TS-1 needs attention", "migration")
    assert rows["E002"][:4] == ("TIMESHEET_REMINDER", "IN_APP", "SENT", False)
    assert rows["E004"][0] == "APPROVAL_DECIDED" and rows["E004"][3] is True
    assert rows["E005"][0] == "APPROVAL_DECIDED"
    assert rows["E006"][0] == "TIMESHEET_ESCALATION" and rows["E006"][4] is False
    # every event is one the app's NotificationEvent enum can read back
    assert {r[0] for r in query("SELECT DISTINCT event FROM core.notification")} <= {
        "APPROVAL_PENDING", "APPROVAL_DECIDED", "TIMESHEET_REMINDER", "TIMESHEET_ESCALATION"}
    q = json.loads((legacy_fixture / "stg_quarantined_load_notifications.json").read_text(encoding="utf-8"))
    assert {(i["legacy_table"], i["legacy_id"]): i["reason"] for i in q} == {
        ("notifications", 2): "ADMIN_RECIPIENT", ("notifications", 4): "EMPLOYEE_NOT_MIGRATED",
        ("notifications", 5): "EVENT_UNMAPPED"}
    assert "Timesheet" not in json.dumps(q)   # no free text in quarantine


def test_tenant_isolation_and_ledger(legacy_fixture, query):
    run_base_and(STEP)
    assert dict(query("SELECT tenant_id::text, count(*) FROM core.notification GROUP BY 1")) == {FOUNDER_TENANT: 5}
    assert query("SELECT count(*) FROM core.notification n JOIN core.employee e ON e.id = n.recipient_employee_id "
                 "WHERE e.tenant_id <> n.tenant_id")[0][0] == 0
    ledger = {r[0]: r[1:] for r in query("SELECT tenant_id::text, status, records_processed, records_skipped "
                                         "FROM core.migration_ledger WHERE step_name = 'load_notifications'")}
    assert ledger == {FOUNDER_TENANT: ("DONE", 5, 3), CUSTOMER_TENANT: ("DONE", 0, 0)}


def test_second_run_is_a_no_op(legacy_fixture, query):
    run_base_and(STEP)
    before = query("SELECT count(*), max(updated_at) FROM core.notification")[0]
    run_base_and(STEP)
    assert query("SELECT count(*), max(updated_at) FROM core.notification")[0] == before
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_notifications'")
    run_base_and(STEP)
    assert query("SELECT count(*), max(updated_at) FROM core.notification")[0] == before
    assert query("SELECT count(*) FROM core.migration_ledger WHERE step_name = 'load_notifications' "
                 "AND status = 'DONE'")[0][0] == 2
