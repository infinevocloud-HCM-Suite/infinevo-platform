"""Logins, roles and invitations (3d). Invented people only; column names are the legacy
MySQL ones (HRMS: Spring snake_case; Payroll: the Java field names verbatim).

Founder tenant (org 148, also every HRMS login):
  e1  HRMS user 1 (E001, admin + user) and Payroll owner kc-old-1 (admin)  -> one account
  e2  HRMS user 2, linked by emp_id E002 only (hr + reporting manager); already in Keycloak
  e3  HRMS user 3 (E003) with role 7 "Intern Lead"                    -> ROLE_UNMAPPED
  e15 HRMS user 4 (E015, HRMS-only employee, user)
  ops HRMS user 5, no employee (admin)
  multi  Payroll kc-old-7 in org 148 (payroll_admin) and in org 201 (HR)
  HRMS user 6 has no email -> NO_EMAIL; user_roles row for user 99 -> USER_NOT_FOUND
Customer tenant (org 201):
  c1  kc-old-2, employee C001 by work email, portal flag false
  owner  kc-old-3, the organisation's Super Admin (admin)
  c2  kc-old-4, role r-201-custom "Auditor" via organizationRole -> ROLE_UNMAPPED, portal true
  kc-old-5 maps to deleted org 202 -> ORG_NOT_MIGRATED; kc-old-6 has no org -> USER_NO_ORG
Invitations: 2 user invitations carried (newhire@acme hr, fin@founder Finance), 2 employee
invitations carried (pay-uuid-1103, pay-uuid-1004); the rest are refused with a reason.
Organisation codes are the numeric ids: fx_org's organization rows carry no organizationId
string column, so the loader's fallback to the numeric id is what resolves them here.
"""
from .fx_org import org_code
from . import CUSTOMER_ORG, DELETED_ORG, FOUNDER_ORG

LEGACY_TOKEN = "legacytoken0000000001"


def _ouser(uid, email, name, emp_id=None, employee_id=None):
    return {"id": uid, "email": email, "name": name, "password": "$2a$10$legacyhashlegacyhash",
            "city": "Pune", "emp_id": emp_id, "blacklisted_token": None, "employee_id": employee_id,
            "is_non_blocked": True}


def _cuser(cid, kc, email, first, last):
    return {"id": cid, "userId": kc, "companyName": "Test Co", "userEmail": email, "firstName": first,
            "lastName": last, "phoneNumber": f"91000000{cid:02d}", "country": "India", "states": "MH",
            "password": "legacy-plain-password", "toc": True}


def _rm(rid, kc, org, role_id, role_name, portal):
    return {"id": rid, "userId": kc, "organizationId": org_code(org), "roleId": role_id, "roleName": role_name,
            "isEmployeePortalEnable": portal}


def _uinv(iid, kc, org, email, role_id=None, user_role=None, accepted=False, super_admin=False,
          deleted=False, created="2024-05-01T10:00:00"):
    return {"id": iid, "userId": kc, "roleId": role_id, "name": "Invitee", "email": email, "mobile": None,
            "invitationType": "INVITE", "isSuperAdmin": super_admin, "status": "active", "userRole": user_role,
            "organizationId": int(org), "createdAt": created, "isDeleted": deleted, "isEditable": True,
            "isInvitationAccepted": accepted, "acceptance_token": f"{LEGACY_TOKEN[:-2]}{iid:02d}",
            "rejection_reason": None, "rejection_date": None}


def _einv(iid, org, emp_uuid, email, accepted=False, expiry="2099-12-31T00:00:00"):
    return {"id": iid, "invitationId": f"INV{iid:05d}", "email": email, "employeeId": emp_uuid,
            "isPortalEnabled": True, "isInvitationAccepted": accepted,
            "acceptance_token": f"emptoken{iid:012d}", "rejection_reason": None, "rejection_date": None,
            "expiry_date": expiry, "organizationId": int(org)}


def rows():
    return {
        "stg_hrms_role.json": [
            {"role_id": 1, "role_name": "admin", "description": None},
            {"role_id": 2, "role_name": "hr", "description": None},
            {"role_id": 3, "role_name": "reporting manager", "description": None},
            {"role_id": 4, "role_name": "user", "description": None},
            {"role_id": 5, "role_name": "supervisor", "description": None},
            {"role_id": 6, "role_name": "manager", "description": None},
            {"role_id": 7, "role_name": "Intern Lead", "description": None},
        ],
        "stg_hrms_ourusers.json": [
            _ouser(1, "E1@Founder.test", "First1 Last1", emp_id="E001", employee_id=1),
            _ouser(2, "e2@founder.test", "First2 Last2", emp_id="E002"),
            _ouser(3, "e3@founder.test", "First3 Last3", employee_id=3),
            _ouser(4, "e15@founder.test", "First15 Last15", employee_id=15),
            _ouser(5, "ops@founder.test", "Ops Desk"),
            _ouser(6, None, "No Mail"),
        ],
        "stg_hrms_user_roles.json": [
            {"user_id": 1, "role_id": 1}, {"user_id": 1, "role_id": 4},
            {"user_id": 2, "role_id": 2}, {"user_id": 2, "role_id": 3},
            {"user_id": 3, "role_id": 7},
            {"user_id": 4, "role_id": 4},
            {"user_id": 5, "role_id": 1},
            {"user_id": 99, "role_id": 1},
        ],
        "stg_payroll_companyUser.json": [
            _cuser(10, "kc-old-1", "e1@founder.test", "PFirst1001", "PLast1001"),
            _cuser(11, "kc-old-2", "c1@acme.test", "CFirst1", "CLast1"),
            _cuser(12, "kc-old-3", "owner@acme.test", "Owner", "Acme"),
            _cuser(13, "kc-old-4", "c2@acme.test", "CFirst2", "CLast2"),
            _cuser(14, "kc-old-5", "gone@gone.test", "Gone", "User"),
            _cuser(15, "kc-old-6", "lonely@acme.test", "Lonely", "User"),
            _cuser(16, "kc-old-7", "multi@acme.test", "Multi", "Tenant"),
        ],
        "stg_payroll_organizationUserMapping.json": [
            {"id": 1, "userId": "kc-old-1", "organizationId": org_code(FOUNDER_ORG)},
            {"id": 2, "userId": "kc-old-2", "organizationId": org_code(CUSTOMER_ORG)},
            {"id": 3, "userId": "kc-old-3", "organizationId": org_code(CUSTOMER_ORG)},
            {"id": 4, "userId": "kc-old-4", "organizationId": org_code(CUSTOMER_ORG)},
            {"id": 5, "userId": "kc-old-5", "organizationId": org_code(DELETED_ORG)},
            {"id": 6, "userId": "kc-old-7", "organizationId": org_code(FOUNDER_ORG)},
            {"id": 7, "userId": "kc-old-7", "organizationId": org_code(CUSTOMER_ORG)},
        ],
        "stg_payroll_organizationRole.json": [
            {"id": 1, "roleId": "r-148-admin", "organizationId": int(FOUNDER_ORG), "roleName": "admin",
             "accessType": "FULL", "userActionRequired": False, "isDefault": True, "roleDescription": None,
             "status": "active", "isDeleted": False},
            {"id": 2, "roleId": "r-201-admin", "organizationId": int(CUSTOMER_ORG), "roleName": "admin",
             "accessType": "FULL", "userActionRequired": False, "isDefault": True, "roleDescription": None,
             "status": "active", "isDeleted": False},
            {"id": 3, "roleId": "r-201-hr", "organizationId": int(CUSTOMER_ORG), "roleName": "HR",
             "accessType": "CUSTOM", "userActionRequired": False, "isDefault": False, "roleDescription": None,
             "status": "active", "isDeleted": False},
            {"id": 4, "roleId": "r-201-custom", "organizationId": int(CUSTOMER_ORG), "roleName": "Auditor",
             "accessType": "CUSTOM", "userActionRequired": False, "isDefault": False, "roleDescription": None,
             "status": "active", "isDeleted": False},
        ],
        "stg_payroll_organizationUserRoleMapping.json": [
            _rm(1, "kc-old-1", FOUNDER_ORG, "r-148-admin", "admin", False),
            _rm(2, "kc-old-2", CUSTOMER_ORG, None, None, False),
            _rm(3, "kc-old-3", CUSTOMER_ORG, "r-201-admin", "admin", False),
            _rm(4, "kc-old-4", CUSTOMER_ORG, "r-201-custom", None, True),
            _rm(5, "kc-old-7", CUSTOMER_ORG, "r-201-hr", "HR", False),
            _rm(6, "kc-old-7", FOUNDER_ORG, None, "payroll_admin", False),
            _rm(7, "kc-old-missing", CUSTOMER_ORG, "r-201-hr", "HR", False),
        ],
        "stg_payroll_userInvitations.json": [
            _uinv(1, "kc-old-1", FOUNDER_ORG, "e1@founder.test", "r-148-admin", "Super Admin",
                  accepted=True, super_admin=True),
            _uinv(2, "kc-old-3", CUSTOMER_ORG, "owner@acme.test", "r-201-admin", "Super Admin",
                  accepted=True, super_admin=True),
            _uinv(3, "kc-new-1", CUSTOMER_ORG, "newhire@acme.test", "r-201-hr"),
            _uinv(4, "kc-old-3", CUSTOMER_ORG, "OWNER@acme.test", "r-201-hr"),   # already has an account
            _uinv(5, "kc-new-2", CUSTOMER_ORG, "auditor@acme.test", "r-201-custom"),  # ROLE_UNMAPPED
            _uinv(6, "kc-new-3", CUSTOMER_ORG, "deleted@acme.test", "r-201-hr", deleted=True),
            _uinv(7, "kc-new-4", FOUNDER_ORG, "fin@founder.test", None, "Finance"),
            _uinv(8, "kc-new-5", CUSTOMER_ORG, "accepted@acme.test", "r-201-hr", accepted=True),
        ],
        "stg_payroll_employeeInvitation.json": [
            _einv(1, CUSTOMER_ORG, "pay-uuid-1103", "c3@acme.test"),
            _einv(2, CUSTOMER_ORG, "pay-uuid-1101", "c1@acme.test"),             # C001 has an account
            _einv(3, CUSTOMER_ORG, "pay-uuid-1104", "c4@acme.test", expiry="2020-01-01T00:00:00"),
            _einv(4, CUSTOMER_ORG, "pay-uuid-9999", "ghost@acme.test"),           # EMPLOYEE_NOT_MIGRATED
            _einv(5, CUSTOMER_ORG, "pay-uuid-1102", "c2@acme.test", accepted=True),
            _einv(6, FOUNDER_ORG, "pay-uuid-1004", "e4@founder.test"),
        ],
    }
