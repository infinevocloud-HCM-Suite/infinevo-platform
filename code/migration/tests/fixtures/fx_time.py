"""Timesheets, projects (3j) and attendance, overtime (3k). HRMS only, so founder tenant only.
Invented values. Employees are HRMS emp codes E001..E020 (E999 does not exist).

Expected 3j: projects 2, tasks 2, assignments 2, timesheets 2, project entries 3,
task entries 3, day entries 3.
Expected 3k: attendance 4, clock sessions 5 (one voided NOT_CLOCKED_OUT, one open),
overtime 3 (+3 approval instances, 6 steps).
"""


def _timesheets():
    projects = [
        {"id": 1, "name": "Apollo", "category": "Internal", "description": "first", "start_date": "2024-01-01",
         "end_date": None, "priority": "HIGH", "budget": 150000.75, "status": "STARTED", "progress": 40,
         "manager_id": "E001", "manager_name": "First1 Last1"},
        {"id": 2, "name": "Zeus", "category": "Client", "description": None, "start_date": "2024-01-01 00:00:00",
         "end_date": "2024-06-30 00:00:00", "priority": "medium", "budget": None, "status": "COMPLETED",
         "progress": None, "manager_id": "E999", "manager_name": "Nobody"},              # manager not found: none
        {"id": 3, "name": "apollo", "category": "Internal", "description": None, "start_date": None,
         "end_date": None, "priority": "LOW", "budget": None, "status": "STARTED", "progress": 0,
         "manager_id": None, "manager_name": None},                                        # DUPLICATE_PROJECT_NAME
        {"id": 4, "name": "Hermes", "category": "Internal", "description": None, "start_date": None,
         "end_date": None, "priority": "URGENT", "budget": None, "status": "STARTED", "progress": 0,
         "manager_id": None, "manager_name": None},                                        # PRIORITY_UNMAPPED
    ]
    tasks = [
        {"id": 1, "title": "Design", "description": "d", "project_id": 1, "assignee_id": "E002",
         "due_date": "2024-02-01", "priority": "LOW", "status": "In Progress", "estimated_hours": 10},
        {"id": 2, "title": "Build", "description": None, "project_id": 1, "assignee_id": None,
         "due_date": None, "priority": "MEDIUM", "status": "TODO", "estimated_hours": None},
        {"id": 3, "title": "Orphan", "description": None, "project_id": 3, "assignee_id": None,
         "due_date": None, "priority": "LOW", "status": "TODO", "estimated_hours": None},  # PROJECT_NOT_LOADED
        {"id": 4, "title": "Ship", "description": None, "project_id": 2, "assignee_id": None,
         "due_date": None, "priority": "LOW", "status": "DONE", "estimated_hours": None},  # STATUS_UNMAPPED
    ]
    assignments = [
        {"id": 1, "emp_id": "E002", "emp_name": "First2 Last2", "project_id": 1},
        {"id": 2, "emp_id": "E003", "emp_name": "First3 Last3", "project_id": 1},
        {"id": 3, "emp_id": "E002", "emp_name": "First2 Last2", "project_id": 1},          # DUPLICATE_ASSIGNMENT
        {"id": 4, "emp_id": "E999", "emp_name": "Nobody", "project_id": 2},                # EMPLOYEE_NOT_FOUND
    ]
    ts = lambda i, emp, start, end, status: {  # noqa: E731
        "id": i, "timesheet_id": f"TS-{i}", "employee_id": emp, "employee_name": f"Name {emp}",
        "week_start_date": start, "week_end_date": end, "status": status,
        "submitted_at": "2024-02-12T10:00:00" if status != "DRAFT" else None,
        "created_at": "2024-02-05T09:00:00", "updated_at": None}
    timesheets = [
        ts(1, "E002", "2024-02-05", "2024-02-11", "APPROVED"),
        ts(2, "E003", "2024-02-05", "2024-02-11", "SUBMITTED"),
        ts(3, "E002", "2024-02-05", "2024-02-11", "DRAFT"),                                # MERGED_INTO_WEEK 1
        ts(4, "E004", "2024-02-06", "2024-02-12", "DRAFT"),                                # WEEK_NOT_MONDAY
    ]
    project_entry = [
        {"id": 1, "project_id": 1, "project_name": "Apollo", "rejection_reason": None, "status": "APPROVED", "timesheet_id": 1},
        {"id": 2, "project_id": 1, "project_name": "Apollo", "rejection_reason": "fix hours", "status": "REJECTED",
         "timesheet_id": 2},
        {"id": 3, "project_id": 1, "project_name": "Apollo", "rejection_reason": None, "status": "DRAFT", "timesheet_id": 3},
        {"id": 4, "project_id": 2, "project_name": "Zeus", "rejection_reason": None, "status": "APPROVED", "timesheet_id": 1},
    ]
    task_entry = [
        {"id": 1, "task_id": 1, "task_name": "Design", "project_entry_id": 1},
        {"id": 2, "task_id": 2, "task_name": "Build", "project_entry_id": 2},
        {"id": 3, "task_id": 4, "task_name": "Ship", "project_entry_id": 4},              # TASK_NOT_LOADED
        {"id": 4, "task_id": 2, "task_name": "Build", "project_entry_id": 1},
    ]
    de = lambda i, te, d, h: {"id": i, "date": d, "day_name": "MONDAY", "hours": h,  # noqa: E731
                              "description": f"work {i}", "task_entry_id": te}
    day_entry = [
        de(1, 1, "2024-02-05", 8.0), de(2, 1, "2024-02-06", 7.5), de(3, 1, "2024-02-07", 0),  # 3: zero hours
        de(4, 2, "2024-02-05", 4.25), de(5, 4, "2024-02-05", 25),                           # 5: HOURS_OUT_OF_RANGE
        de(6, 1, "2024-02-05", 1.0),                                                         # DUPLICATE_DAY
    ]
    return {"stg_hrms_projects.json": projects, "stg_hrms_tasks.json": tasks,
            "stg_hrms_assignments.json": assignments, "stg_hrms_timesheets.json": timesheets,
            "stg_hrms_project_entry.json": project_entry, "stg_hrms_task_entry.json": task_entry,
            "stg_hrms_day_entry.json": day_entry}


def _attendance():
    att = lambda i, emp, day, status, out="18:00:00": {  # noqa: E731
        "id": i, "employee_id": emp, "employee_name": f"Name {emp}", "in_time": f"{day}T09:00:00",
        "out_time": f"{day}T{out}" if out else None, "status": status, "end_day": out is not None,
        "created_at": f"{day}T09:00:00", "updated_at": None}
    attendance = [
        att(1, "E002", "2024-03-04", "PRESENT"),
        att(2, "E002", "2024-03-05", "HALF_DAY", out=None),
        att(3, "E003", "2024-03-04", "ABSENT"),
        att(4, "E002", "2024-03-04", "PRESENT"),           # DUPLICATE_ATTENDANCE_DAY (its session still loads)
        att(5, "E999", "2024-03-04", "PRESENT"),           # EMPLOYEE_NOT_FOUND
        att(6, "E004", "2024-03-04", "LATE"),              # STATUS_UNMAPPED
        att(7, "E002", "2024-03-06", "PRESENT", out=None),
    ]
    cs = lambda i, a, t_in, t_out: {"id": i, "attendance_id": a, "in_time": t_in, "out_time": t_out}  # noqa: E731
    sessions = [
        cs(1, 1, "2024-03-04T09:00:00", "2024-03-04T13:00:00"),
        cs(2, 1, "2024-03-04T14:00:00", "2024-03-04T18:00:00"),
        cs(3, 2, "2024-03-05T09:00:00", None),             # open, followed by a later session: voided
        cs(4, 3, "2024-03-04T10:00:00", "2024-03-04T09:00:00"),  # BAD_SESSION_TIMES
        cs(5, 5, "2024-03-04T09:00:00", None),             # ATTENDANCE_NOT_LOADED
        cs(6, 7, "2024-03-06T09:00:00", None),             # the last open session: stays open
        cs(7, 4, "2024-03-04T19:00:00", "2024-03-04T20:00:00"),
    ]
    ot = lambda i, emp, start, end, mgr, hr: {  # noqa: E731
        "id": i, "employee_id": emp, "employee_name": f"Name {emp}", "category": "Release", "start_time": start,
        "end_time": end, "project": "Apollo", "notes": f"note {i}", "manager_status": mgr, "hr_status": hr,
        "manager_updated_at": "2024-03-05T10:00:00" if mgr != "PENDING" else None,
        "hr_updated_at": "2024-03-06T10:00:00" if hr != "PENDING" else None,
        "created_at": "2024-03-04T21:00:00", "comp_off_created": mgr == hr == "APPROVED",
        "manager_comment": "fine" if mgr != "PENDING" else None, "hr_comment": None, "manager_employee_id": "E001"}
    overtime = [
        ot(1, "E002", "2024-03-04T18:00:00", "2024-03-04T20:30:00", "APPROVED", "APPROVED"),
        ot(2, "E003", "2024-03-04T19:00:00", "2024-03-04T21:00:00", "PENDING", "PENDING"),
        ot(3, "E004", "2024-03-04T19:00:00", "2024-03-04T20:00:00", "REJECTED", "PENDING"),
        ot(4, "E005", "2024-03-04T19:00:00", "2024-03-04T18:00:00", "PENDING", "PENDING"),  # BAD_OVERTIME_HOURS
        ot(5, "E999", "2024-03-04T19:00:00", "2024-03-04T20:00:00", "PENDING", "PENDING"),  # EMPLOYEE_NOT_FOUND
    ]
    return {"stg_hrms_attendance.json": attendance, "stg_hrms_clock_sessions.json": sessions,
            "stg_hrms_overtime_requests.json": overtime}


def rows():
    return {**_timesheets(), **_attendance()}
