"""Employees (merge + 3c). Invented people only.

HRMS: 20 employees E001..E020, all in the founder tenant. E001 manages everyone; E002 is
indirect manager and first-level approver for E011..E020; E003 is a third-level approver
for E020 (no target kind). E019 left on 2024-01-31. E007's employment type is unmappable.
Payroll: org 148 has 10 rows matching E001..E010 (8 by work email, 1 by personal email,
1 by PAN; E005's Payroll joining date differs and HRMS wins; E008 is INACTIVE); org 201 has
4 Payroll-only people, 1 duplicate of one of them, and 1 with no joining date.
Expected cohorts: 10 both, 10 HRMS-only, 4 Payroll-only; quarantine 2; 24 employees.
"""
from . import CUSTOMER_ORG, FOUNDER_ORG

N_HRMS = 20


def _pan(i: int) -> str:
    return f"ABCPE{1000 + i:04d}F"


def _hrms():
    emp, personal, contact, ident, work, report = [], [], [], [], [], []
    for i in range(1, N_HRMS + 1):
        code = f"E{i:03d}"
        emp.append({"id": i, "personal_id": 100 + i, "contact_id": 200 + i, "identification_id": 300 + i,
                    "work_id": 400 + i, "report_id": 500 + i if i > 1 else None,
                    "created_at": "2024-04-01T10:00:00", "updated_at": None})
        personal.append({"id": 100 + i, "emp_id": code, "first_name": f"First{i}", "middle_name": None,
                         "last_name": f"Last{i}", "gender": "Female" if i % 2 else "Male",
                         "date_of_birth": "1990-05-0%d" % (i % 9 + 1), "marital_status": "Single",
                         "nationality": "Indian", "ethnicity": None,
                         "employment_status": "Full Time" if i != 7 else "Freelancer"})
        contact.append({"id": 200 + i, "work_email": f"e{i}@founder.test", "personal_email": f"p{i}@mail.test",
                        "mobile_number": f"98000000{i:02d}", "residential_address": f"{i} Test Lane",
                        "city": "Pune", "state": "Maharashtra", "country": "India", "postal_code": "411001",
                        "permanent_address": None, "primary_emergency_contact_name": "Kin",
                        "primary_emergency_contact_number": "9000000000",
                        "relationship_to_primary_emergency_contact": "Parent",
                        "secondary_emergency_contact_name": None, "secondary_emergency_contact_number": None,
                        "relationship_to_secondary_emergency_contact": None,
                        "family_doctor_name": None, "family_doctor_contact_number": None})
        ident.append({"id": 300 + i, "pan_card_number": _pan(i), "aadhar_card_number": f"9999000{i:05d}",
                      "immigration_status": None, "personal_tax_id": None, "social_insurance": None,
                      "id_proof": "Passport", "document_name": "Passport", "document_number": f"P{i:07d}",
                      "address_proof": None, "address_document_name": None, "address_document_number": None})
        work.append({"id": 400 + i, "doj": "2022-06-01", "termination_date": "2024-01-31" if i == 19 else None,
                     "department": "Engineering" if i <= 10 else "Design Studio",
                     "job_title": "Software Engineer" if i <= 10 else "Designer",
                     "pay_grade": "G1", "workstation_id": f"WS{i}", "time_zone": "Asia/Kolkata",
                     "shift_start_time": "09:30:00", "shift_end_time": "18:30:00"})
        if i > 1:
            report.append({"id": 500 + i, "reporting_manager_id": "E001", "reporting_manager_name": "First1 Last1",
                           "indirect_manager": "E002" if i > 10 else None,
                           "first_level_approver": "E002" if i > 10 else None,
                           "second_level_approver": None,
                           "third_level_approver": "E003" if i == 20 else None,
                           "note": "migrated" if i == 2 else None})
    return emp, personal, contact, ident, work, report


def _payroll_employee(eid, org, num, email, doj="2022-06-01", status="ACTIVE", **kw):
    row = {"employee_id": eid, "employeeId": f"pay-uuid-{eid}", "organizationId": int(org),
           "employee_number": num, "first_name": f"PFirst{eid}", "middle_name": None, "last_name": f"PLast{eid}",
           "gender": "Male", "date_of_joining": doj, "work_mail": email, "mobile": f"97000000{eid % 100:02d}",
           "employee_status": status, "is_portal_enabled": True, "is_deleted": False,
           "department_id": 1, "designation_id": 1, "work_location_id": 1, "hr_user": None,
           "is_eligible_for_pf": True, "is_eligible_for_pt": True, "is_eligible_for_lwf": False,
           "is_eligible_for_esi": False, "is_eligible_for_eps": True, "can_contribute_to_eps_on_higher_wages": False,
           "is_director": False, "pf_account_number": f"MH/PUN/{eid}", "uan": f"1000000000{eid % 100:02d}",
           "esi_number": None, "employee_unique_id": None, "amount_in_percentage": None}
    row.update(kw)
    return row


def _payroll():
    rows, pdetail, bank = [], [], []
    for i in range(1, 11):  # founder org, matching HRMS E001..E010
        email = f"e{i}@founder.test"
        if i == 9:
            email = "p9@mail.test"  # matches HRMS personal email
        if i == 10:
            email = "e10-other@founder.test"  # matches by PAN only
        rows.append(_payroll_employee(1000 + i, FOUNDER_ORG, f"P{i:03d}", email,
                                      doj="2022-07-01" if i == 5 else "2022-06-01",
                                      status="INACTIVE" if i == 8 else "ACTIVE"))
        pdetail.append({"id": 2000 + i, "employee_id": 1000 + i, "personal_mail": f"pp{i}@mail.test",
                        "date_of_birth": "1991-01-01", "father_name": f"Father{i}", "pan": _pan(i),
                        "differently_abled_type": None, "is_eligible_for_full_income_tax_exemption": False,
                        "addressLine1": "Payroll addr", "addressLine2": None, "city": "Mumbai", "state": "Maharashtra",
                        "stateCode": "MH", "zipCode": "400001", "custom_fields": None,
                        "organizationId": int(FOUNDER_ORG)})
        bank.append({"id": 3000 + i, "employee_id": 1000 + i, "payment_mode": "Bank Transfer",
                     "account_holder_name": f"PFirst{1000 + i}", "bank_name": "Test Bank", "ifsc_code": "TEST0001234",
                     "bank_account_number": f"000111222{i:03d}", "bank_account_type": "Savings",
                     "organizationId": int(FOUNDER_ORG)})
    for i in range(1, 5):  # customer org, Payroll-only
        rows.append(_payroll_employee(1100 + i, CUSTOMER_ORG, f"C{i:03d}", f"c{i}@acme.test",
                                      department_id=4, designation_id=3, work_location_id=3))
        bank.append({"id": 3100 + i, "employee_id": 1100 + i, "payment_mode": "Cash", "account_holder_name": None,
                     "bank_name": None, "ifsc_code": None, "bank_account_number": None, "bank_account_type": None,
                     "organizationId": int(CUSTOMER_ORG)})
    rows.append(_payroll_employee(1199, CUSTOMER_ORG, "C001-dup", "c1@acme.test"))  # duplicate in org
    rows.append(_payroll_employee(1198, CUSTOMER_ORG, "C099", "c99@acme.test", doj=None))  # NO_DOJ
    return rows, pdetail, bank


def rows():
    emp, personal, contact, ident, work, report = _hrms()
    pay, pdetail, bank = _payroll()
    return {
        "stg_hrms_employee.json": emp, "stg_hrms_personal.json": personal, "stg_hrms_contact.json": contact,
        "stg_hrms_identification.json": ident, "stg_hrms_work.json": work, "stg_hrms_report.json": report,
        "stg_payroll_employee.json": pay, "stg_payroll_employee_personal_detail.json": pdetail,
        "stg_payroll_employee_bank_detail.json": bank,
    }
