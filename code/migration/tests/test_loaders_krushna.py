"""
Unit tests for Krushna's Loaders:
  - load_leave.py (Rule R2, 5 types + LEGACY, approval steps)
  - load_timesheets.py (projects, tasks, timesheets, day entries with hours > 0)
  - load_attendance.py (attendance, clock sessions)
"""

import os
import sys
import io
import json
import uuid
import pytest
from unittest.mock import MagicMock, patch

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from loaders.load_leave import run_loader as run_leave_loader
from loaders.load_timesheets import run_loader as run_timesheets_loader
from loaders.load_attendance import run_loader as run_attendance_loader

FIXTURES = {
    "stg_merged_employees.json": [
        {
            "id": "e0000000-0000-0000-0000-000000000001",
            "target_employee_id": "e0000000-0000-0000-0000-000000000001",
            "tenant_id": "a0000000-0000-0000-0000-000000000001",
            "source_legacy_hrms_id": "emp1",
            "employee_number": "EMP001"
        }
    ],
    "stg_hrms_leave_types.json": [
        {"id": "1", "name": "Casual Leave", "code": "CL"}
    ],
    "stg_hrms_leave_requests.json": [
        {
            "id": "10",
            "employee_id": "emp1",
            "leave_type_id": "1",
            "from_date": "2026-01-01",
            "to_date": "2026-01-02",
            "total_days": "2.00",
            "status": "APPROVED",
            "reason": "Family",
            "applied_at": "2026-01-01 10:00:00"
        }
    ],
    "stg_hrms_leave_balances.json": [
        {
            "id": "20",
            "employee_id": "emp1",
            "leave_type_id": "1",
            "balance": "10",
            "consumed": "2"
        }
    ],
    "stg_hrms_holidays.json": [
        {
            "id": "30",
            "name": "New Year",
            "holiday_date": "2026-01-01",
            "is_optional": False
        }
    ],
    "stg_hrms_projects.json": [
        {"id": "p1", "name": "HCM Core", "code": "HCM", "status": "ACTIVE"}
    ],
    "stg_hrms_tasks.json": [
        {"id": "t1", "project_id": "p1", "name": "Migration", "status": "ACTIVE"}
    ],
    "stg_hrms_assignments.json": [
        {"id": "a1", "project_id": "p1", "emp_id": "emp1", "role": "DEV"}
    ],
    "stg_hrms_timesheets.json": [
        {
            "id": "ts1",
            "employee_id": "emp1",
            "week_start_date": "2026-01-01",
            "week_end_date": "2026-01-07",
            "status": "APPROVED"
        }
    ],
    "stg_hrms_task_entry.json": [
        {
            "id": "te1",
            "project_entry_id": "ts1",
            "task_id": "t1"
        }
    ],
    "stg_hrms_day_entry.json": [
        {
            "id": "de1",
            "task_entry_id": "te1",
            "date": "2026-01-02",
            "hours": 8.0,
            "status": "APPROVED"
        },
        {
            "id": "de2",
            "task_entry_id": "te1",
            "date": "2026-01-03",
            "hours": 0.0,
            "status": "REJECTED"
        }
    ],
    "stg_hrms_attendance.json": [
        {
            "id": "att1",
            "employee_id": "emp1",
            "in_time": "2026-01-02 09:00:00",
            "created_at": "2026-01-02 09:00:00",
            "status": "PRESENT"
        }
    ],
    "stg_hrms_clock_sessions.json": [
        {
            "id": "cs1",
            "employee_id": "emp1",
            "session_date": "2026-01-02",
            "clock_in": "2026-01-02T09:00:00Z",
            "clock_out": "2026-01-02T18:00:00Z"
        }
    ]
}

def mock_open_staging(fpath, *args, **kwargs):
    basename = os.path.basename(fpath)
    if basename in FIXTURES:
        return io.StringIO(json.dumps(FIXTURES[basename]))
    return io.StringIO("[]")

def mock_exists_staging(fpath):
    basename = os.path.basename(fpath)
    return basename in FIXTURES

@pytest.fixture
def mock_db():
    conn = MagicMock()
    cur = MagicMock()
    conn.cursor.return_value = cur
    return conn, cur

def test_load_leave_seeds_types_and_approvals(mock_db):
    conn, cur = mock_db
    captured_inserts = []

    def mock_execute_batch(cursor, sql, batch, page_size=100):
        captured_inserts.append((sql, batch))

    cur.fetchone.return_value = ("def-uuid-leave",)

    with patch("loaders.load_leave.get_conn", return_value=conn), \
         patch("loaders.load_leave.execute_batch", side_effect=mock_execute_batch), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging):
        run_leave_loader()

    # Verify leave_type batch contains LEGACY
    lt_batches = [b for sql, b in captured_inserts if "core.leave_type" in sql]
    assert len(lt_batches) > 0
    codes = [row[2] for row in lt_batches[0]]
    assert "LEGACY" in codes
    assert "ANNUAL" in codes
    assert "SICK" in codes

    # Verify approval_instance and approval_step batches
    appr_batches = [b for sql, b in captured_inserts if "core.approval_instance" in sql]
    assert len(appr_batches) > 0

    step_batches = [b for sql, b in captured_inserts if "core.approval_step" in sql]
    assert len(step_batches) > 0

def test_load_timesheets_filters_zero_hours(mock_db):
    conn, cur = mock_db
    captured_inserts = []

    def mock_execute_batch(cursor, sql, batch, page_size=100):
        captured_inserts.append((sql, batch))

    with patch("loaders.load_timesheets.get_conn", return_value=conn), \
         patch("loaders.load_timesheets.execute_batch", side_effect=mock_execute_batch), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging):
        run_timesheets_loader()

    # Verify day entries only have hours > 0
    day_batches = [b for sql, b in captured_inserts if "hrms.timesheet_day_entry" in sql]
    assert len(day_batches) > 0
    for row in day_batches[0]:
        hours = row[4]
        assert hours > 0
        assert hours <= 24

def test_load_attendance_sets_migrated(mock_db):
    conn, cur = mock_db
    captured_inserts = []

    def mock_execute_batch(cursor, sql, batch, page_size=100):
        captured_inserts.append((sql, batch))

    with patch("loaders.load_attendance.get_conn", return_value=conn), \
         patch("loaders.load_attendance.execute_batch", side_effect=mock_execute_batch), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging):
        run_attendance_loader()

    # Verify attendance has remarks='MIGRATED'
    att_batches = [b for sql, b in captured_inserts if "core.attendance" in sql]
    assert len(att_batches) > 0
    for row in att_batches[0]:
        remarks = row[6]
        assert remarks == "MIGRATED"
