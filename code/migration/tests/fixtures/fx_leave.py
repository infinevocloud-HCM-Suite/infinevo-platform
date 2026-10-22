"""Leave, holidays and attendance preferences (3i). Invented values only.

Founder tenant (org 148) from HRMS: leave_types, leave_balances (five fixed columns),
leave_requests (no type column), holidays. Customer tenant (org 201) from Payroll:
leave_type (+ genders / departments / designations / work_locations element tables),
employee_leave_allocation, employee_leave_balance_consumption, holidays (+ holiday_locations),
attendance_preferences. Payroll rows of org 148 are not migrated (HRMS is the founder's
leave authority, W-67 R2); org 203 is not in the org map.

Expected, founder: leave types 5 seeded + LEGACY + 1 extra HRMS type (Work From Home) = 7;
allocations 9; consumption 4; requests 4 (+4 approval instances, 8 steps); holidays 2.
Customer: leave types 2; policies 2; eligibility 2; allocations 3; consumption 2;
holidays 3 (default calendar 1 + Chennai calendar 2), calendar locations 1.
Attendance preferences: 2 (one per tenant).
"""
from . import CUSTOMER_ORG, FOUNDER_ORG, UNMAPPED_ORG


def _bal(i, emp, year, annual=(24, 0), sick=(12, 0), casual=(8, 0), maternity=(0, 0), paternity=(0, 0)):
    return {"id": i, "employee_id": emp, "year": year,
            "annual_allotted": annual[0], "annual_used": annual[1],
            "sick_allotted": sick[0], "sick_used": sick[1],
            "casual_allotted": casual[0], "casual_used": casual[1],
            "maternity_allotted": maternity[0], "maternity_used": maternity[1],
            "paternity_allotted": paternity[0], "paternity_used": paternity[1]}


def _req(i, emp, frm, to, total, mgr, hr, half=False, period=None, **kw):
    row = {"id": i, "employee_id": emp, "employee_name": f"Name{i}", "from_date": frm, "to_date": to,
           "is_half_day": half, "half_day_period": period, "reason": f"reason {i}", "total_days": total,
           "reporting_manager_status": mgr, "hr_status": hr,
           "reporting_manager_comment": None, "hr_comment": None,
           "reporting_manager_updated_at": None, "hr_updated_at": None,
           "created_date": "2024-04-20T09:15:00", "late_reason": None,
           "leave_balance_deducted": False, "leave_balance_recredited": False, "lop_generated": False}
    row.update(kw)
    return row


def _hrms():
    types = [
        {"id": 1, "name": "Annual Leave", "description": "Yearly", "default_days": 18.0, "start_date": "2024-04-01",
         "end_date": None, "leave_carried_forward": True, "apply_to_all_employees": True,
         "created_at": "2024-03-01T10:00:00", "updated_at": None},
        {"id": 2, "name": "Sick Leave", "description": None, "default_days": 10.0, "start_date": None,
         "end_date": None, "leave_carried_forward": False, "apply_to_all_employees": True,
         "created_at": "2024-03-01T10:00:00", "updated_at": None},
        {"id": 3, "name": "Work From Home", "description": None, "default_days": 5.5, "start_date": "2024-04-01",
         "end_date": "2025-03-31", "leave_carried_forward": False, "apply_to_all_employees": False,
         "created_at": "2024-03-01T10:00:00", "updated_at": None},
    ]
    balances = [
        _bal(1, "E001", 2024, annual=(24, 3), sick=(12, 1), casual=(8, 0), paternity=(10, 2)),  # 4 alloc, 3 used
        _bal(2, "E002", 2024, maternity=(90, 0)),                                               # 4 alloc
        _bal(3, "2", 2025, annual=(24, 5), sick=(0, 0), casual=(0, 0)),                         # HRMS id, 1 alloc
        _bal(4, "E999", 2024),                                                                  # EMPLOYEE_NOT_FOUND
        _bal(5, "E001", 2024),                                                                  # DUPLICATE
    ]
    requests = [
        _req(1, "E003", "2024-05-06", "2024-05-07", 2.0, "APPROVED", "APPROVED",
             reporting_manager_comment="ok", reporting_manager_updated_at="2024-05-02T10:00:00",
             hr_comment="approved by hr", hr_updated_at="2024-05-03T11:00:00"),
        _req(2, "E004", "2024-06-10", "2024-06-10", 0.5, "APPROVED", "REJECTED", half=True, period="first",
             reporting_manager_updated_at="2024-06-01T10:00:00", hr_updated_at="2024-06-02T10:00:00"),
        _req(3, "E005", "2024-07-01", "2024-07-03", 3.0, "PENDING", "PENDING"),
        _req(4, "E003", "2024-05-07", "2024-05-08", 2.0, "APPROVED", "APPROVED",
             hr_updated_at="2024-05-04T11:00:00"),                                              # APPROVED_OVERLAP
        _req(5, "E999", "2024-05-06", "2024-05-06", 1.0, "PENDING", "PENDING"),                 # EMPLOYEE_NOT_FOUND
        _req(6, "E006", "2024-08-01", "2024-08-01", 1.0, "REJECTED", "PENDING",
             reporting_manager_updated_at="2024-07-30T10:00:00"),                               # hr wins: PENDING
    ]
    holidays = [
        {"id": 1, "name": "Republic Day", "date": "2024-01-26", "day": "Friday"},
        {"id": 2, "name": "Diwali", "date": "2024-11-01", "day": "Friday"},
        {"id": 3, "name": None, "date": "2024-12-25", "day": "Wednesday"},                     # NO_NAME
    ]
    return types, balances, requests, holidays


def _leave_type(i, org, name, code, kind="paid", **kw):
    row = {"id": i, "organizationId": int(org), "name": name, "code": code, "description": None,
           "type": kind, "unit": "day_based", "status": "active", "allow_half_day": True,
           "validity_from": "2024-01-01", "validity_to": None,
           "accrual_is_enabled": False, "accrual_frequency": None, "accrual_units": None,
           "accrual_regular_hours": None, "reset_is_enabled": False, "reset_frequency": None,
           "carry_forward_is_enabled": False, "carry_forward_units": None,
           "encashment_is_enabled": False, "encashment_units": None,
           "past_booking_is_enabled": False, "past_booking_limit_days": None,
           "future_booking_is_enabled": False, "future_booking_limit_days": None,
           "include_weekend_is_enabled": False, "include_weekend_min_days": None,
           "include_holiday_is_enabled": False, "include_holiday_min_days": None,
           "exceed_balance_is_enabled": False, "exceed_balance_mode": None,
           "effective_after_period": None, "effective_after_units": None,
           "pro_rate_is_enabled": False, "max_leave_per_application": None}
    row.update(kw)
    return row


def _alloc(i, emp, lt, year, annual, carried=0, consumed=0, org="org-uuid-201"):
    return {"id": i, "organization_id": org, "employee_id": emp, "leave_type": lt, "year": year,
            "annual_days": annual, "carried_forward_days": carried, "consumed_days": consumed,
            "monthly_lwp_breakdown": None, "monthly_lop_breakdown": None, "monthly_breakdown": None,
            "leave_month": None, "lwp": 0, "lop_days": 0, "expiration_date": f"{year}-12-31",
            "carry_forward": carried > 0, "created_by": "seed", "created_at": "2024-04-02T10:00:00",
            "updated_at": None}


def _cons(i, alloc, emp, lt, year, days, month, lwp=0):
    return {"id": i, "leave_id": f"LV-{year}-{i}", "allocation_id": alloc, "organization_id": "org-uuid-201",
            "employee_id": emp, "leave_type": lt, "year": year, "consumed_days": days, "balance_days": 0,
            "balance_after": None, "running_ytd": None, "reason": f"reason {i}", "leave_month": month,
            "monthly_breakdown": None, "monthly_lop_breakdown": None, "monthly_lwp_breakdown": None,
            "monthly_entries": None, "lwp": lwp, "lop_days": 0, "created_by": "seed",
            "created_at": "2024-06-01T10:00:00", "updated_at": None}


def _holiday(i, org, name, frm, to, status=True, restricted=False):
    return {"id": i, "holidayId": f"hol-{i}", "name": name, "from_date": frm, "to_date": to,
            "description": f"desc {i}", "is_restricted_holiday": restricted, "organizationId": int(org),
            "status": status}


def _pref(i, org, firstin, full, half, holidays, weekends, max_req, buffer):
    return {"id": i, "organizationId": int(org), "calculation_on_firstin_lastout": firstin,
            "is_minimum_hours_required": True, "is_maximum_hours_required": False,
            "can_include_holidays_for_pay": holidays, "can_include_leaves_for_pay": True,
            "can_include_weekends_for_pay": weekends, "full_day_minimum_hours": full,
            "full_day_maximum_hours": None, "half_day_minimum_hours": half, "half_day_maximum_hours": None,
            "minimum_hours_for_overtime": None, "can_create_new_entries": True,
            "allow_future_regularization": False, "maximum_requests_allowed": max_req,
            "period_type": "monthly", "request_days_buffer": buffer}


def _payroll():
    types = [
        _leave_type(11, CUSTOMER_ORG, "Casual Leave", "CL", accrual_is_enabled=True, accrual_frequency="monthly",
                    accrual_units=1.5, reset_is_enabled=True, reset_frequency="yearly",
                    carry_forward_is_enabled=True, carry_forward_units=5, pro_rate_is_enabled=True,
                    max_leave_per_application=3, past_booking_limit_days=7),
        _leave_type(12, CUSTOMER_ORG, "Loss Of Pay", "LOP", kind="unpaid", validity_from=None,
                    exceed_balance_is_enabled=True, exceed_balance_mode="markAsLOP",
                    max_leave_per_application=-1),
        _leave_type(13, CUSTOMER_ORG, "Bereavement", "BL", kind="special"),       # LEAVE_KIND_UNMAPPED
        _leave_type(14, FOUNDER_ORG, "Payroll Founder Leave", "PFL"),             # founder: HRMS authority
        _leave_type(15, UNMAPPED_ORG, "Elsewhere", "EW"),                         # ORG_NOT_MIGRATED
    ]
    genders = [{"leave_type_id": 11, "gender": "Female"},
               {"leave_type_id": 12, "gender": "male"}, {"leave_type_id": 12, "gender": "female"}]
    departments = [{"leave_type_id": 11, "department_id": "d-4"},
                   {"leave_type_id": 11, "department_id": "d-zzz"}]               # DEPARTMENT_NOT_FOUND
    designations = [{"leave_type_id": 11, "designation_id": "g-3"}]
    work_locations = [{"leave_type_id": 11, "work_location_id": "wl-3"}]          # resolved only with workLocationId
    allocations = [
        _alloc(1, "pay-uuid-1101", "Casual Leave", "2024", 18, carried=2, consumed=3),
        _alloc(2, "pay-uuid-1102", "casual leave", "2024", 18),
        _alloc(3, "pay-uuid-1101", "Loss Of Pay", "2024", 0),
        _alloc(4, "pay-uuid-1103", "Unknown Type", "2024", 5),                    # LEAVE_TYPE_NOT_FOUND
        _alloc(5, "pay-uuid-9999", "Casual Leave", "2024", 5),                    # EMPLOYEE_NOT_FOUND
        _alloc(6, "pay-uuid-1001", "Annual", "2024", 12, org="org-uuid-148"),     # founder: HRMS authority
    ]
    consumption = [
        _cons(1, 1, "pay-uuid-1101", "Casual Leave", "2024", 2, "May 2024"),
        _cons(2, None, "pay-uuid-1101", "Casual Leave", "2024", 1, "2024-07"),    # allocation found by key
        _cons(3, 4, "pay-uuid-1103", "Unknown Type", "2024", 1, "May 2024"),      # ALLOCATION_NOT_FOUND
        _cons(4, 1, "pay-uuid-1101", "Casual Leave", "2024", 0, "June 2024", lwp=1),  # ZERO_DAYS
        _cons(5, 1, "pay-uuid-1101", "Casual Leave", "2024", 1, "Someday"),       # BAD_LEAVE_MONTH
    ]
    holidays = [
        _holiday(1, CUSTOMER_ORG, "New Year", "2024-01-01", "2024-01-01"),
        _holiday(2, CUSTOMER_ORG, "Pongal", "2024-01-15", "2024-01-16", restricted=True),
        _holiday(3, CUSTOMER_ORG, "Old Holiday", "2023-01-01", "2023-01-01", status=False),  # HOLIDAY_INACTIVE
        _holiday(4, FOUNDER_ORG, "Founder Payroll Holiday", "2024-03-01", "2024-03-01"),     # founder: HRMS
        _holiday(5, CUSTOMER_ORG, "Somewhere Day", "2024-02-01", "2024-02-01"),              # LOCATION_NOT_FOUND
    ]
    holiday_locations = [{"holiday_id": 2, "location": "Chennai"},
                         {"holiday_id": 5, "location": "wl-unknown"}]
    prefs = [
        _pref(1, CUSTOMER_ORG, True, "08:30", "04:00", True, True, "3", "5"),
        _pref(2, FOUNDER_ORG, False, "09:00", "04:30", True, True, None, None),
    ]
    return {
        "stg_payroll_leave_type.json": types,
        "stg_payroll_leave_type_genders.json": genders,
        "stg_payroll_leave_type_departments.json": departments,
        "stg_payroll_leave_type_designations.json": designations,
        "stg_payroll_leave_type_work_locations.json": work_locations,
        "stg_payroll_employee_leave_allocation.json": allocations,
        "stg_payroll_employee_leave_balance_consumption.json": consumption,
        "stg_payroll_holidays.json": holidays,
        "stg_payroll_holiday_locations.json": holiday_locations,
        "stg_payroll_attendance_preferences.json": prefs,
    }


def rows():
    types, balances, requests, holidays = _hrms()
    return {
        "stg_hrms_leave_types.json": types,
        "stg_hrms_leave_balances.json": balances,
        "stg_hrms_leave_requests.json": requests,
        "stg_hrms_holidays.json": holidays,
        **_payroll(),
    }
