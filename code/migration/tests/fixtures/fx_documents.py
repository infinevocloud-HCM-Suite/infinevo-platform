"""Legacy file references for W-69 (load_documents). Column names are the legacy MySQL ones
(EmployeeDocument.java, EmployeePOIDocument.java, LeaveDocument.java). URLs are invented;
tests/test_load_documents.py serves their bytes from a fake HTTP layer.

HRMS employee_document: 1-3 load (3 answers 503 once first); 4 is a 404, 5 is 11 MB, 6 an
.exe, 7 an unknown employee, 8 a ".pdf" that is not a PDF.
POI: 1 has an unloaded proof item (loaded without the link), 2's item the test inserts,
3's URL names no employee. leave_documents: 1's request the test inserts, 2's is never loaded.
Claim receipts and the deduction proof come from fx_claims; the logo is patched in by the test.
"""
URL = "https://res.cloudinary.com/demo/image/upload/v1700000000"


def _hrms(did, emp, dtype, name):
    return {"id": did, "employee_id": emp, "document_type": dtype, "file_name": name,
            "file_type": "application/octet-stream", "file_url": f"{URL}/hrms/{did}/{name}",
            "public_id": f"hrms/{did}", "company_index": None}


def _poi(did, item, url, name):
    return {"id": did, "poi_item_id": item, "document_name": name, "document_url": url,
            "uploaded_by": "employee", "uploaded_time": "2024-12-01T10:00:00"}


def rows():
    return {
        "stg_hrms_employee_document.json": [
            _hrms(1, 1, "AADHAR_CARD", "aadhar.pdf"),
            _hrms(2, 2, "DEGREE_CERTIFICATE", "degree.png"),
            _hrms(3, 3, "APPOINTMENT_LETTER", "offer.pdf"),
            _hrms(4, 4, "PHOTO", "photo.jpg"),
            _hrms(5, 5, "OTHER_IDENTIFICATION", "huge.pdf"),
            _hrms(6, 6, "OTHER_QUALIFICATION", "run.exe"),
            _hrms(7, 99, "PAN_CARD", "pan.pdf"),
            _hrms(8, 8, "PAN_CARD", "fake.pdf"),
        ],
        "stg_payroll_employee_poi_document.json": [
            _poi(1, 501, f"{URL}/poi/pay-uuid-1001/rent.jpg", "rent.jpg"),
            _poi(2, 502, f"{URL}/poi/pay-uuid-1101/lic.pdf", "lic.pdf"),
            _poi(3, 503, "https://files.example.test/x.pdf", "x.pdf"),
        ],
        "stg_hrms_leave_documents.json": [
            {"id": 1, "leave_request_id": 1, "file_name": "medical.pdf", "file_type": "application/pdf",
             "file_url": f"{URL}/leave/1/medical.pdf", "public_id": "leave/1", "uploaded_at": "2024-06-09T08:00:00"},
            {"id": 2, "leave_request_id": 2, "file_name": "medical2.pdf", "file_type": "application/pdf",
             "file_url": f"{URL}/leave/2/medical2.pdf", "public_id": "leave/2", "uploaded_at": "2024-06-09T08:00:00"},
        ],
    }
