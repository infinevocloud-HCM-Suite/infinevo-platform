"""Claims and salary deductions (3l). Column names are the legacy MySQL ones
(EmployeeReimbursementRequest.java and SalaryDeduction.java set @Column names).

employee_id is the Payroll employee UUID (employee.employeeId, "pay-uuid-<id>" in the
fixture); organization_id is the organisation reference the legacy app stores.

Claims: 1, 2 (org 148) and 4 (org 201) load. 3 has no matching component master, 5 an
unknown employee, 6 a zero amount, 7 an unknown status, 8 an organisation that is not the
employee's, 9 a master the salary step did not load. Claims 1 and 4 carry an attachment
(load_documents, W-69).
Deductions: 11, 12 (org 148) and 13 (org 201) load. 14 was already taken in a month the
test locks, 15 has a negative amount, 16 no month. Deduction 11 carries a proof.

The reimbursement masters the claims match against (stg_payroll_reimbursements.json) belong
to the salary fixture; tests/test_load_claims.py writes its own.
"""
from . import CUSTOMER_ORG, FOUNDER_ORG

URL = "https://res.cloudinary.com/demo/image/upload/v1700000000"


def _claim(cid, emp, org, rtype, amount, status, approved=None, payment="UNPAID", attachment=None, **kw):
    row = {
        "id": cid, "employee_id": f"pay-uuid-{emp}", "organization_id": str(org),
        "reimbursement_type": rtype, "requested_amount": amount, "approved_amount": approved,
        "bill_date": "2024-05-10", "description": f"Claim {cid}",
        "attachment_url": f"{URL}/reimbursements/{attachment}" if attachment else None,
        "attachment_public_id": f"reimbursements/{cid}" if attachment else None,
        "attachment_file_name": attachment,
        "status": status, "remarks": None, "payment_status": payment, "reimbursement_month": "2024-05",
        "payrun_id": None, "created_at": "2024-05-11T09:15:00", "updated_at": "2024-05-12T10:00:00",
        "approved_by": None, "approved_at": None,
    }
    row.update(kw)
    return row


def _deduction(did, emp, org, amount, month, dtype, status, reason="Recovery", proof=None):
    return {
        "id": did, "employee_id": f"pay-uuid-{emp}", "organization_id": str(org),
        "deduction_amount": amount, "deduction_month": month, "deduction_type": dtype,
        "reason": reason, "remarks": None, "status": status, "created_by": "admin-1",
        "created_at": "2024-05-20T11:00:00", "updated_at": None,
        "proof_url": f"{URL}/deductions/{proof}" if proof else None,
        "proof_public_id": f"deductions/{did}" if proof else None,
    }


def rows():
    return {
        "stg_payroll_employee_reimbursement_request.json": [
            _claim(1, 1001, FOUNDER_ORG, "INTERNET", "1500.50", "PENDING", attachment="bill-1.pdf"),
            _claim(2, 1002, FOUNDER_ORG, "FUEL", "2000.00", "APPROVED", approved="1999.99", payment="PAID",
                   approved_by="pay-uuid-1001", approved_at="2024-05-13T16:30:00", remarks="ok"),
            _claim(3, 1003, FOUNDER_ORG, "MEDICAL", "300.00", "REJECTED"),
            _claim(4, 1101, CUSTOMER_ORG, "INTERNET", "800.25", "APPROVED", approved="800.25",
                   attachment="bill-4.png"),
            _claim(5, 9999, FOUNDER_ORG, "INTERNET", "10.00", "PENDING"),
            _claim(6, 1004, FOUNDER_ORG, "INTERNET", "0", "PENDING"),
            _claim(7, 1005, FOUNDER_ORG, "INTERNET", "100.00", "ON_HOLD"),
            _claim(8, 1001, CUSTOMER_ORG, "INTERNET", "100.00", "PENDING"),
            _claim(9, 1006, FOUNDER_ORG, "FOOD", "50.00", "PENDING"),
        ],
        "stg_payroll_employee_deduction.json": [
            _deduction(11, 1001, FOUNDER_ORG, "2500.75", "2024-06-01", "Salary Advance", "ACTIVE",
                       reason="Advance recovery", proof="proof-11.pdf"),
            _deduction(12, 1002, FOUNDER_ORG, "1000.00", "2024-05-01", "late fine", "PROCESSED"),
            _deduction(13, 1102, CUSTOMER_ORG, "499.99", "2024-07-01", None, "INPAYRUN"),
            _deduction(14, 1003, FOUNDER_ORG, "700.00", "2024-03-01", "LOAN", "PROCESSED"),
            _deduction(15, 1004, FOUNDER_ORG, "-5.00", "2024-06-01", "OTHER", "ACTIVE"),
            _deduction(16, 1005, FOUNDER_ORG, "100.00", None, "OTHER", "ACTIVE"),
        ],
    }
