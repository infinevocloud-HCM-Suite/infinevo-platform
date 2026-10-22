"""Organisations and organisation masters (3a, 3b). Column names are the legacy
Payroll MySQL ones (PhysicalNamingStrategyStandardImpl keeps the Java field names)."""
from . import CUSTOMER_ORG, DELETED_ORG, FOUNDER_ORG, UNMAPPED_ORG


def org_code(oid) -> str:
    """Organization.organizationId for a fixture org: '148' -> '100148'."""
    return f"100{int(oid):03d}"


def _org(oid, name, deleted=False):
    return {
        # organizationId is the public 6-digit string other tables key on (OrganizationServiceImpl.java:69-72)
        "id": int(oid), "organizationId": org_code(oid), "organizationName": name, "addressLine1": "1 Test Road", "addressLine2": None,
        "city": "Pune", "state": "Maharashtra", "pinCode": "411001", "email": f"admin{oid}@example.test",
        "timezone": "Asia/Kolkata", "industry": "IT", "businessLocation": "India",
        "isOrgActive": True, "isDeleted": deleted, "hasRunPayroll": True,
        "fileUrl": None, "fileName": None, "filePublicId": None,
        "createdBy": "seed", "createdDate": "2024-04-01T10:00:00", "updatedBy": None, "updatedDate": None,
    }


def rows():
    return {
        "stg_payroll_organization.json": [
            _org(FOUNDER_ORG, "Infinevo Founder Co"),
            _org(CUSTOMER_ORG, "Acme Customer Pvt Ltd"),
            _org(DELETED_ORG, "Gone Ltd", deleted=True),
            _org(UNMAPPED_ORG, "Not In Map Ltd"),
        ],
        "stg_payroll_department.json": [
            {"id": 1, "departmentId": "d-1", "name": "Engineering", "departmentCode": "ENG", "description": None,
             "status": "Active", "organizationId": int(FOUNDER_ORG)},
            {"id": 2, "departmentId": "d-2", "name": "Human Resources", "departmentCode": None, "description": None,
             "status": "Active", "organizationId": int(FOUNDER_ORG)},
            {"id": 3, "departmentId": "d-3", "name": "Finance", "departmentCode": None, "description": None,
             "status": "Inactive", "organizationId": int(CUSTOMER_ORG)},
            {"id": 4, "departmentId": "d-4", "name": "Finance", "departmentCode": None, "description": None,
             "status": "Active", "organizationId": int(CUSTOMER_ORG)},
        ],
        "stg_payroll_designation.json": [
            {"id": 1, "designationId": "g-1", "name": "Software Engineer", "status": "Active", "organizationId": int(FOUNDER_ORG)},
            {"id": 2, "designationId": "g-2", "name": "HR Manager", "status": "Active", "organizationId": int(FOUNDER_ORG)},
            {"id": 3, "designationId": "g-3", "name": "Accountant", "status": "Active", "organizationId": int(CUSTOMER_ORG)},
        ],
        "stg_payroll_workLocations.json": [
            {"id": 1, "workLocationName": "Pune HQ", "streetAddress1": "1 Test Road", "streetAddress2": None,
             "city": "Pune", "state": "Maharashtra", "zipCode": "411001", "country": "India",
             "isFilingAddress": True, "status": "Active", "organizationId": int(FOUNDER_ORG)},
            {"id": 2, "workLocationName": "Bengaluru", "streetAddress1": "2 Test Road", "streetAddress2": None,
             "city": "Bengaluru", "state": "Karnataka", "zipCode": "560001", "country": "India",
             "isFilingAddress": True, "status": "Active", "organizationId": int(FOUNDER_ORG)},
            {"id": 3, "workLocationName": "Chennai", "streetAddress1": "3 Test Road", "streetAddress2": None,
             "city": "Chennai", "state": "Tamil Nadu", "zipCode": "600001", "country": "India",
             "isFilingAddress": True, "status": "Active", "organizationId": int(CUSTOMER_ORG)},
        ],
        "stg_payroll_paySchedule.json": [
            {"id": 10, "payScheduleId": "ps-10", "payScheduleType": "monthly", "payDay": "last_working_day",
             "payPeriodStartDate": "2024-04-01", "payPeriodEndDate": "2024-04-30", "payDate": "2024-04-30",
             "noOfWorkingDays": 22, "includeHolidays": True, "includeWeekends": False,
             "workingDaysCalculationType": "actual_days", "organizationId": int(FOUNDER_ORG)},
            {"id": 11, "payScheduleId": "ps-11", "payScheduleType": "monthly", "payDay": "7",
             "payPeriodStartDate": "2024-04-01", "payPeriodEndDate": "2024-04-30", "payDate": "2024-05-07",
             "noOfWorkingDays": 26, "includeHolidays": True, "includeWeekends": True,
             "workingDaysCalculationType": "org_days", "organizationId": int(CUSTOMER_ORG)},
        ],
        "stg_payroll_payScheduleWorkingDays.json": [
            *({"payScheduleId": 10, "workingDays": d} for d in ("mon", "tue", "wed", "thu", "fri")),
            *({"payScheduleId": 11, "workingDays": d} for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT")),
        ],
        "stg_payroll_incomeTaxDetails.json": [
            {"id": 1, "tanNumber": "PNEA12345B", "panNumber": "AAACI1234F", "tdsCircle": "Pune-1",
             "authorizedPersonName": "Test Signatory", "authorizedPersonParent": "Test Parent",
             "authorizedPersonDesignation": "Director", "depositSchedule": "Monthly", "employeeId": "pay-uuid-1001",
             "organizationId": int(FOUNDER_ORG)},
            {"id": 2, "tanNumber": "BAD", "panNumber": "AAACA1234F", "tdsCircle": None,
             "authorizedPersonName": "X", "authorizedPersonParent": None,
             "authorizedPersonDesignation": "Director", "depositSchedule": None, "employeeId": None,
             "organizationId": int(CUSTOMER_ORG)},
        ],
    }
