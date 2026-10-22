"""
HRMS MySQL extractor (W-68.1).

Copies every carried HRMS table (mapping .claude/outputs/2026-10-16-legacy-target-field-mapping.md
§3, rows marked H) from the legacy MySQL database into staging/stg_hrms_<table>.json, in a
read-only session. Configuration: LEGACY_HRMS_DB_HOST, _PORT (default 3306), _NAME, _USER,
_PASSWORD. A missing variable exits 2.

Table names follow Spring's default naming: @Table(name) if present, else the class name
in snake_case. Entities are under legacy/HRMS_Backend/src/main/java/com/phegondev/
usersmanagementsystem/entity/.
"""
from __future__ import annotations

import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from extractors.common_extract import run as _run  # noqa: E402

SOURCE = "hrms"

# Every carried HRMS table plus the element-collection / join tables of carried entities.
TABLES_TO_EXTRACT: tuple[str, ...] = (
    # §3.2 Users and access
    "ourusers",                  # OurUsers.java
    "user_roles",                # OurUsers.java @JoinTable
    "role",                      # useraccess/Role.java (role names for role_map.json)
    # §3.3 Employee master (employee is the link table; report = reporting managers)
    "employee",                  # Employee.java
    "personal",                  # personal.java
    "contact",                   # Contact.java
    "identification",            # Identification.java
    "work",                      # Work.java
    "report",                    # Report.java
    # §3.8 Leave, holidays, attendance
    "leave_types",               # LeaveType.java
    "leave_type_employee_ids",   # LeaveType.java @CollectionTable
    "leave_balances",            # LeaveBalance.java
    "leave_requests",            # LeaveRequests.java
    "leave_manual_days",         # LeaveRequests.java @CollectionTable
    "leave_selected_dates",      # LeaveRequests.java @CollectionTable
    "leave_request_lop",         # LeaveRequests.java @CollectionTable
    "leave_documents",           # LeaveDocument.java (file_data not extracted)
    "employee_monthly_lop",      # EmployeeMonthlyLop.java
    "holidays",                  # Holiday.java
    "attendance",                # Attendance.java
    "clock_sessions",            # ClockSession.java
    "overtime_requests",         # OvertimeRequest.java
    # §3.9 Timesheets, projects, tasks
    "projects",                  # Project.java
    "tasks",                     # Task.java
    "assignments",               # Assignment.java
    "timesheets",                # timesheet/Timesheets.java
    "project_entry",             # timesheet/ProjectEntry.java
    "task_entry",                # timesheet/TaskEntry.java
    "day_entry",                 # timesheet/DayEntry.java
    # §3.10 Documents
    "employee_document",         # EmployeeDocument.java (document_content not extracted)
    # §3.11 Notifications (W-68 Task 3m)
    "notifications",             # Notification.java
    "timesheet_notifications",   # timesheet/TimesheetsNotification.java
)

# §1 duplicate twins: counted in the summary, no file written (W-67 R2).
COUNT_ONLY: tuple[str, ...] = (
    "leave_request",             # LeaveRequest.java
    "employee_leave_balances",   # EmployeeLeaveBalance.java
    "timesheet",                 # Timesheet.java
)

# Binary columns never extracted; the file URL is enough for W-69.
EXCLUDED_COLUMNS: dict[str, tuple[str, ...]] = {
    "employee_document": ("document_content",),
    "leave_documents": ("file_data",),
    # Secrets never reach staging (mapping §3.2: passwords dropped, Keycloak owns logins).
    "ourusers": ("password", "blacklisted_token"),
}


def run() -> dict:
    return _run(SOURCE, "HRMS", TABLES_TO_EXTRACT, COUNT_ONLY, EXCLUDED_COLUMNS)


def main() -> int:
    run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
