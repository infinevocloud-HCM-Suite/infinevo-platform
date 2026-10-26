"""3d - logins, roles and pending invitations (W-68 Task 3, mapping §3.2).

  HRMS ourusers, Payroll companyUser (+ organizationUserMapping)
      -> Keycloak user + core.user_account + core.user_tenant, core.employee.user_account_id
  HRMS user_roles (+ role), Payroll organizationUserRoleMapping (+ organizationRole)
      -> core.user_role by role code (config/role_map.json); isEmployeePortalEnable
      -> core.employee.is_portal_enabled
  Payroll userInvitations, employeeInvitation, pending only
      -> core.user_invitation (+ core.user_invitation_role), core.employee_invitation

How a migrated user signs in (the platform's own flows, not a migration-only path):

* core.user_account.keycloak_user_id is NOT NULL (V009__user_account.sql:7) and
  core.user_tenant.user_id is the Keycloak id (InvitationServiceImpl.java:507-510), so a
  role grant or an employee link cannot exist without a Keycloak user. Every legacy login
  therefore gets one: looked up by email first, created only when absent, enabled, with
  the UPDATE_PASSWORD required action and NO credential. A user that already exists keeps
  its password and its required actions.
* That is the state KeycloakProvisioningServiceImpl.java:169-176 recognises: an invitation
  accepted for this email sets the chosen password on the existing user (D-88), reuses
  this core.user_account (InvitationServiceImpl.java:500-504), skips the existing
  user_tenant (ON CONFLICT DO NOTHING) and adds roles to those held (grantRoles). Keycloak's
  own "Forgot password" (resetPasswordAllowed, infra/keycloak/infinevo-realm.json:8) works
  too. Nothing is created twice.
* No legacy password, legacy Keycloak id or legacy acceptance token is read into the
  target. A carried invitation gets a new token; only its hash is stored, exactly as
  InvitationTokenUtils.java hashes it (sha256 of the trimmed 64-hex token, lower-case hex).
  The token itself is discarded: the admin's Resend (InvitationServiceImpl.java:171-218)
  issues and mails a fresh one.
* Legacy pending invitations are people who never signed in, so no Keycloak user is made
  for them - W-24.2 creates the Keycloak user at acceptance.

MIGRATION_KEYCLOAK=off makes no Keycloak call. With no Keycloak id there can be no
core.user_account row, so no account, user_tenant, user_role or employee link is written;
each user is logged and quarantined as KEYCLOAK_OFF. The mode is part of the ledger
checksum, so a later run with Keycloak on is not skipped.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import secrets
import sys
import time
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

import requests

from common import db, staging
from common.env import optional, require
from common.ids import det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_timestamp, truthy
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_users"
# id_map.json is not an input (later loaders append to it). stg_merged_employees.json is the
# file the employee ids come from, and its change re-runs this step so a re-loaded employee
# gets its account link and portal flag back.
INPUTS = ["stg_hrms_ourusers.json", "stg_hrms_user_roles.json", "stg_hrms_role.json",
          "stg_payroll_companyUser.json", "stg_payroll_organizationUserMapping.json",
          "stg_payroll_organizationUserRoleMapping.json", "stg_payroll_organizationRole.json",
          "stg_payroll_userInvitations.json", "stg_payroll_employeeInvitation.json",
          "stg_payroll_organization.json", "stg_payroll_employee.json", "stg_merged_employees.json"]
ROLE_MAP = Path(__file__).resolve().parents[1] / "config" / "role_map.json"
INVITATION_DAYS = 7          # InvitationServiceImpl.java:133 - every newly issued token
EMPLOYEE_ROLE = "employee"   # granted with every employee link, as acceptance does (InvitationServiceImpl.java:578-587)
OWNER_ROLE = "tenant-admin"
log = logging.getLogger(STEP)


# ---------------------------------------------------------------- Keycloak

class KeycloakAdmin:
    """The three Admin REST calls this loader makes. Tests replace keycloak_client()."""

    def __init__(self, base_url: str, realm: str, admin_user: str, admin_password: str,
                 http=None, timeout: int = 10):
        self.base_url = base_url.rstrip("/")
        self.realm = realm
        self._user = admin_user
        self._password = admin_password
        self._http = http or requests.Session()
        self._timeout = timeout
        self._token: str | None = None
        self._token_until = 0.0

    @classmethod
    def from_env(cls) -> "KeycloakAdmin":
        return cls(require("KEYCLOAK_URL"), require("KEYCLOAK_REALM"),
                   require("KEYCLOAK_ADMIN_USER"), require("KEYCLOAK_ADMIN_PASSWORD"))

    @property
    def _users_url(self) -> str:
        return f"{self.base_url}/admin/realms/{self.realm}/users"

    def _headers(self) -> dict:
        if not self._token or time.monotonic() > self._token_until - 15:
            resp = self._http.post(f"{self.base_url}/realms/master/protocol/openid-connect/token",
                                   data={"client_id": "admin-cli", "grant_type": "password",
                                         "username": self._user, "password": self._password},
                                   timeout=self._timeout)
            if resp.status_code != 200:
                raise RuntimeError(f"Keycloak admin token refused: HTTP {resp.status_code}")
            body = resp.json()
            self._token = body["access_token"]
            self._token_until = time.monotonic() + int(body.get("expires_in", 60))
        return {"Authorization": f"Bearer {self._token}"}

    def find_user_by_email(self, email: str) -> str | None:
        resp = self._http.get(self._users_url, params={"email": email, "exact": "true"},
                              headers=self._headers(), timeout=self._timeout)
        if resp.status_code != 200:
            raise RuntimeError(f"Keycloak user search failed: HTTP {resp.status_code}")
        for user in resp.json() or []:
            if (user.get("email") or "").strip().lower() == email:
                return user["id"]
        return None

    def create_user(self, email: str, first_name: str | None, last_name: str | None) -> str:
        """No credential: UPDATE_PASSWORD marks the user as awaiting one, which is what the
        invitation accept page and the realm's reset-password flow both finish."""
        payload = {"username": email, "email": email, "firstName": first_name or "",
                   "lastName": last_name or "", "enabled": True, "emailVerified": True,
                   "requiredActions": ["UPDATE_PASSWORD"]}
        resp = self._http.post(self._users_url, json=payload, headers=self._headers(), timeout=self._timeout)
        if resp.status_code == 201 and resp.headers.get("Location"):
            return resp.headers["Location"].rstrip("/").rsplit("/", 1)[-1]
        if resp.status_code in (201, 409):
            found = self.find_user_by_email(email)
            if found:
                return found
        # The body is not quoted: Keycloak echoes the submitted user, email included.
        raise RuntimeError(f"Keycloak refused to create a user: HTTP {resp.status_code}")


def keycloak_mode() -> str:
    return "off" if optional("MIGRATION_KEYCLOAK", "on").strip().lower() == "off" else "on"


def keycloak_client():
    """The Admin REST client, or None with MIGRATION_KEYCLOAK=off."""
    return None if keycloak_mode() == "off" else KeycloakAdmin.from_env()


def new_token_hash() -> str:
    """A fresh invitation token, hashed as InvitationTokenUtils.generateToken / hashToken do.
    The token is not kept anywhere."""
    token = secrets.token_hex(32)
    return hashlib.sha256(token.strip().encode("utf-8")).hexdigest()


# ---------------------------------------------------------------- gathering

@dataclass
class Person:
    """One login in one tenant. HRMS and Payroll rows with the same email merge into it."""
    email: str
    ref: tuple[str, object]
    refs: list[tuple[str, object]] = field(default_factory=list)
    first_name: str | None = None
    last_name: str | None = None
    phone: str | None = None
    employee_id: str | None = None
    roles: set[str] = field(default_factory=set)
    portal: list[bool] = field(default_factory=list)
    owner: bool = False


def _email(v) -> str | None:
    s = text(v)
    if not s or "@" not in s or " " in s:
        return None
    return s.lower()[:255]


def _split_name(name) -> tuple[str | None, str | None]:
    s = text(name)
    if not s:
        return None, None
    parts = s.split(None, 1)
    return parts[0][:100], (parts[1][:100] if len(parts) > 1 else None)


def _load_role_map() -> dict[str, dict[str, str]]:
    path = Path(os.environ.get("MIGRATION_ROLE_MAP") or ROLE_MAP)
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    return {system: {k.strip().lower(): v for k, v in m.items() if not k.startswith("_")}
            for system, m in raw.items() if not system.startswith("_")}


def _role_key(name) -> str | None:
    s = text(name)
    return " ".join(s.lower().split()) if s else None


def _safe_role(key: str | None) -> str | None:
    """A role name is configuration, not personal data; still, only short plain names go to quarantine."""
    if key and len(key) <= 40 and all(c.isalnum() or c in " _-" for c in key):
        return key
    return None


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    role_map = _load_role_map()
    mode = keycloak_mode()
    people: dict[str, dict[str, Person]] = defaultdict(dict)   # tenant -> email -> Person
    user_invites: dict[str, list[dict]] = defaultdict(list)
    employee_invites: dict[str, list[dict]] = defaultdict(list)

    def person(tid: str, email: str, ref: tuple[str, object]) -> Person:
        p = people[tid].get(email)
        if p is None:
            p = people[tid][email] = Person(email=email, ref=ref)
        p.refs.append(ref)
        return p

    def grant(p: Person, system: str, name, legacy_table: str, legacy_id, tid: str, role_id=None) -> None:
        key = _role_key(name)
        code = role_map.get(system, {}).get(key) if key else None
        if code is None:
            q.add("ROLE_UNMAPPED", legacy_table, legacy_id, tid, legacy_role_id=role_id, role=_safe_role(key))
            return
        p.roles.add(code)

    # ---- HRMS: every login belongs to the founder tenant (W-67 R5)
    founder = tenants.founder
    hrms_roles = {str(r.get("role_id")): r.get("role_name") for r in staging.load("stg_hrms_role.json")}
    hrms_users = {str(u.get("id")): u for u in staging.load("stg_hrms_ourusers.json")}
    roles_of: dict[str, list[str]] = defaultdict(list)
    for ur in staging.load("stg_hrms_user_roles.json"):
        uid, rid = str(ur.get("user_id")), str(ur.get("role_id"))
        if uid not in hrms_users:
            q.add("USER_NOT_FOUND", "user_roles", f"{uid}:{rid}")
            continue
        roles_of[uid].append(rid)
    for uid, u in hrms_users.items():
        email = _email(u.get("email"))
        if email is None:
            q.add("NO_EMAIL", "ourusers", u.get("id"))
            continue
        if founder is None:
            q.add("ORG_NOT_MIGRATED", "ourusers", u.get("id"))
            continue
        tid = founder.tenant_id
        linked = bool(text(u.get("employee_id")) or text(u.get("emp_id")))
        emp = (idmap.get("hrms.employee", u.get("employee_id")) or idmap.get("hrms.emp_code", text(u.get("emp_id")))
               if linked else None)
        if linked and emp is None and not founder.hrms_only:
            # W-67 R1: the login of an HRMS-only employee stays behind with the employee
            q.add("EMPLOYEE_NOT_MIGRATED", "ourusers", u.get("id"), tid)
            continue
        p = person(tid, email, ("hrms.ourusers", u.get("id")))
        first, last = _split_name(u.get("name"))
        p.first_name, p.last_name = p.first_name or first, p.last_name or last
        if linked:
            if emp is None:
                q.add("EMPLOYEE_NOT_MIGRATED", "ourusers", u.get("id"), tid)
            else:
                p.employee_id = p.employee_id or emp
        for rid in roles_of.get(uid, []):
            grant(p, "hrms", hrms_roles.get(rid), "user_roles", f"{uid}:{rid}", tid, role_id=rid)

    # ---- Payroll: organisation by its string organizationId (OrganizationServiceImpl.java:183,221)
    orgs = staging.load("stg_payroll_organization.json")
    by_org_code = {str(o["organizationId"]): o.get("id") for o in orgs if text(o.get("organizationId"))}

    def tenant_of_code(code) -> Tenant | None:
        # organizationUserMapping / organizationUserRoleMapping hold organization.organizationId (a
        # string), not the numeric key. An extract without that column falls back to the numeric id.
        if by_org_code:
            return tenants.for_org(by_org_code.get(str(code)))
        return tenants.for_org(code)

    org_roles = {str(r.get("roleId")): r.get("roleName") for r in staging.load("stg_payroll_organizationRole.json")}
    company = {str(c.get("userId")): c for c in staging.load("stg_payroll_companyUser.json") if text(c.get("userId"))}
    orgs_of: dict[str, list[str]] = defaultdict(list)
    for m in staging.load("stg_payroll_organizationUserMapping.json"):
        if str(m.get("userId")) not in company:
            q.add("USER_NOT_FOUND", "organizationUserMapping", m.get("id"))
            continue
        orgs_of[str(m.get("userId"))].append(str(m.get("organizationId")))
    role_rows: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for rm in staging.load("stg_payroll_organizationUserRoleMapping.json"):
        if str(rm.get("userId")) not in company:
            q.add("USER_NOT_FOUND", "organizationUserRoleMapping", rm.get("id"))
            continue
        role_rows[(str(rm.get("userId")), str(rm.get("organizationId")))].append(rm)
    owners = {(str(i.get("userId")), str(i.get("organizationId")))
              for i in staging.load("stg_payroll_userInvitations.json")
              if truthy(i.get("isSuperAdmin")) and not truthy(i.get("isDeleted"))}
    for kc_old, c in company.items():
        email = _email(c.get("userEmail"))
        if email is None:
            q.add("NO_EMAIL", "companyUser", c.get("id"))
            continue
        codes = list(dict.fromkeys(orgs_of.get(kc_old, []) +
                                   [org for (u, org) in role_rows if u == kc_old]))
        if not codes:
            q.add("USER_NO_ORG", "companyUser", c.get("id"))
            continue
        for code in codes:
            t = tenant_of_code(code)
            if t is None:
                q.add("ORG_NOT_MIGRATED", "companyUser", c.get("id"))
                continue
            p = person(t.tenant_id, email, ("payroll.companyUser", f"{t.tenant_id}:{kc_old}"))
            p.first_name = p.first_name or text(c.get("firstName"), 100)
            p.last_name = p.last_name or text(c.get("lastName"), 100)
            p.phone = p.phone or text(c.get("phoneNumber"), 30)
            if (kc_old, t.legacy_org_id) in owners:
                p.owner = True
            for rm in role_rows.get((kc_old, code), []):
                if rm.get("isEmployeePortalEnable") is not None:
                    p.portal.append(bool(truthy(rm.get("isEmployeePortalEnable"), False)))
                name = text(rm.get("roleName")) or org_roles.get(str(rm.get("roleId")))
                if name is None and not text(rm.get("roleId")):
                    continue  # a portal-flag-only row (CompanyUserServiceImpl.java:767-774)
                grant(p, "payroll", name, "organizationUserRoleMapping", rm.get("id"), t.tenant_id,
                      role_id=text(rm.get("roleId")))

    # ---- Payroll invitations, pending only
    now = datetime.now(timezone.utc)
    latest: dict[tuple[str, str], dict] = {}
    for inv in staging.load("stg_payroll_userInvitations.json"):
        if (truthy(inv.get("isInvitationAccepted")) or truthy(inv.get("isDeleted")) or truthy(inv.get("isSuperAdmin"))
                or text(inv.get("rejection_reason")) or (text(inv.get("status")) or "active").lower() != "active"):
            continue
        t = tenants.for_org(inv.get("organizationId"))
        if t is None:
            q.add("ORG_NOT_MIGRATED", "userInvitations", inv.get("id"))
            continue
        email = _email(inv.get("email"))
        if email is None:
            q.add("NO_EMAIL", "userInvitations", inv.get("id"), t.tenant_id)
            continue
        key = (t.tenant_id, email)
        prev = latest.get(key)
        if prev is not None:
            older = min(prev, inv, key=lambda r: int(r.get("id") or 0))
            q.add("DUPLICATE_INVITATION", "userInvitations", older.get("id"), t.tenant_id)
            if older is inv:
                continue
        latest[key] = inv
    for (tid, email), inv in latest.items():
        name = org_roles.get(str(inv.get("roleId"))) or text(inv.get("userRole"))
        key = _role_key(name)
        code = role_map["payroll"].get(key) if key else None
        if code is None:
            q.add("ROLE_UNMAPPED" if key else "NO_ROLE", "userInvitations", inv.get("id"), tid,
                  legacy_role_id=text(inv.get("roleId")), role=_safe_role(key))
            continue
        user_invites[tid].append({"legacy_id": inv.get("id"), "email": email, "role": code,
                                  "created_at": to_timestamp(inv.get("createdAt"))})

    emp_latest: dict[str, dict] = {}
    for inv in staging.load("stg_payroll_employeeInvitation.json"):
        if truthy(inv.get("isInvitationAccepted")) or text(inv.get("rejection_reason")) \
                or not truthy(inv.get("isPortalEnabled"), True):
            continue
        t = tenants.for_org(inv.get("organizationId"))
        if t is None:
            q.add("ORG_NOT_MIGRATED", "employeeInvitation", inv.get("id"))
            continue
        expiry = to_timestamp(inv.get("expiry_date"))
        if expiry is not None and expiry < now:
            q.add("INVITATION_EXPIRED", "employeeInvitation", inv.get("id"), t.tenant_id)
            continue
        emp = idmap.get("payroll.employee.uuid", inv.get("employeeId"))
        if emp is None or idmap.get("employee.tenant", emp) not in (None, t.tenant_id):
            q.add("EMPLOYEE_NOT_MIGRATED", "employeeInvitation", inv.get("id"), t.tenant_id)
            continue
        email = _email(inv.get("email"))
        if email is None:
            q.add("NO_EMAIL", "employeeInvitation", inv.get("id"), t.tenant_id)
            continue
        prev = emp_latest.get(emp)
        if prev is not None:
            older = min(prev["row"], inv, key=lambda r: int(r.get("id") or 0))
            q.add("DUPLICATE_INVITATION", "employeeInvitation", older.get("id"), t.tenant_id)
            if older is inv:
                continue
        emp_latest[emp] = {"row": inv, "tid": t.tenant_id, "email": email, "employee_id": emp}
    for emp, e in emp_latest.items():
        employee_invites[e["tid"]].append({"legacy_id": e["row"].get("id"), "email": e["email"], "employee_id": emp})

    kc = keycloak_client()
    kc_ids: dict[str, str] = {}
    created: list[str] = []
    if kc is None:
        log.warning("MIGRATION_KEYCLOAK=off: no Keycloak call is made; core.user_account.keycloak_user_id is "
                    "NOT NULL, so no account, user_tenant, user_role or employee link is written")

    def keycloak_id(p: Person) -> str:
        if p.email not in kc_ids:
            found = kc.find_user_by_email(p.email)
            if found is None:
                found = kc.create_user(p.email, p.first_name, p.last_name)
                created.append(found)
            kc_ids[p.email] = str(found)
        return kc_ids[p.email]

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
        cur.execute("SELECT code, id::text FROM core.role WHERE tenant_id = %s", (tid,))
        role_ids = dict(cur.fetchall())
        cur.execute("SELECT id::text, lower(work_email), user_account_id::text FROM core.employee WHERE tenant_id = %s",
                    (tid,))
        employees = {r[0]: {"email": r[1], "account": r[2]} for r in cur.fetchall()}
        by_work_email = {}
        for eid, e in sorted(employees.items()):
            if e["email"]:
                by_work_email.setdefault(e["email"], eid)

        persons = sorted(people.get(tid, {}).values(), key=lambda p: p.email)
        accounts, account_of = [], {}
        for p in persons:
            if kc is None:
                q.add("KEYCLOAK_OFF", p.ref[0], p.ref[1], tid)
                continue
            accounts.append({"id": det_id(tid, "user_account", p.email), "tenant_id": tid,
                             "keycloak_user_id": keycloak_id(p), "email": p.email,
                             "first_name": p.first_name, "last_name": p.last_name, "phone_number": p.phone,
                             "status": "ACTIVE", **audit})
        # A row the platform made for the same Keycloak user (a login before the migration) is kept.
        # The link tables below hold keys and audit columns only: updated_by is kept as well, so a
        # second run has nothing to update and moves no updated_at.
        db.upsert(cur, "core.user_account", accounts, conflict=("tenant_id", "keycloak_user_id"),
                  keep_on_update=("id", "status"))
        if accounts:
            cur.execute("SELECT keycloak_user_id::text, id::text FROM core.user_account "
                        "WHERE tenant_id = %s AND keycloak_user_id = ANY(%s::uuid[])",
                        (tid, [a["keycloak_user_id"] for a in accounts]))
            by_kc = dict(cur.fetchall())
            account_of = {a["email"]: by_kc[a["keycloak_user_id"]] for a in accounts}
        db.upsert(cur, "core.user_tenant",
                  [{"id": det_id(tid, "user_tenant", a["keycloak_user_id"]), "tenant_id": tid,
                    "user_id": a["keycloak_user_id"], **audit} for a in accounts],
                  conflict=("user_id", "tenant_id"), keep_on_update=("id", "updated_by"))

        # Employee link (HRMS: ourusers.employee_id / emp_id; Payroll: work email in the
        # organisation, CompanyUserServiceImpl.java:780-782) and the portal flag.
        links, linked_by = 0, {}
        user_roles = []
        for p in persons:
            acct = account_of.get(p.email)
            if acct is None:
                continue
            emp = p.employee_id or by_work_email.get(p.email)
            if p.employee_id and p.employee_id not in employees:
                q.add("EMPLOYEE_NOT_MIGRATED", p.ref[0], p.ref[1], tid)
                emp = None
            if emp is not None:
                current = employees[emp]["account"]
                if emp in linked_by or (current is not None and current != acct):
                    q.add("EMPLOYEE_ALREADY_LINKED", p.ref[0], p.ref[1], tid)
                    emp = None
            if emp is not None:
                linked_by[emp] = acct
                employees[emp]["account"] = acct
                p.roles.add(EMPLOYEE_ROLE)
                cur.execute("UPDATE core.employee SET user_account_id = %s, updated_at = now(), updated_by = %s "
                            "WHERE tenant_id = %s AND id = %s AND user_account_id IS DISTINCT FROM %s::uuid",
                            (acct, db.AUDIT_USER, tid, emp, acct))
                if p.portal:
                    enabled = any(p.portal)
                    cur.execute("UPDATE core.employee SET is_portal_enabled = %s, updated_at = now(), updated_by = %s "
                                "WHERE tenant_id = %s AND id = %s AND is_portal_enabled IS DISTINCT FROM %s",
                                (enabled, db.AUDIT_USER, tid, emp, enabled))
                links += 1
            for code in sorted(p.roles):
                rid = role_ids.get(code)
                if rid is None:
                    q.add("ROLE_NOT_SEEDED", p.ref[0], p.ref[1], tid, role=code)
                    continue
                user_roles.append({"id": det_id(tid, "user_role", f"{acct}:{code}"), "tenant_id": tid,
                                   "user_account_id": acct, "role_id": rid, **audit})
        db.upsert(cur, "core.user_role", user_roles, conflict=("tenant_id", "user_account_id", "role_id"),
                  keep_on_update=("id", "updated_by"))

        # Invitations. The inviter column is NOT NULL and names a person (InvitationServiceImpl.java:798-806);
        # legacy kept no inviter, so it is the tenant's owner - the Payroll super admin - else the
        # first tenant administrator by email.
        inviter = None
        for p in persons:
            if p.owner and OWNER_ROLE in p.roles and p.email in account_of:
                inviter = account_of[p.email]
                break
        if inviter is None:
            cur.execute("SELECT ua.id::text FROM core.user_account ua JOIN core.user_role ur ON ur.user_account_id = ua.id "
                        "JOIN core.role r ON r.id = ur.role_id WHERE ua.tenant_id = %s AND r.code = %s "
                        "AND ua.status = 'ACTIVE' ORDER BY ua.email LIMIT 1", (tid, OWNER_ROLE))
            row = cur.fetchone()
            inviter = row[0] if row else None
        cur.execute("SELECT lower(email) FROM core.user_account WHERE tenant_id = %s", (tid,))
        has_account = {r[0] for r in cur.fetchall()}
        expires = now + timedelta(days=INVITATION_DAYS)
        u_rows, ur_rows, e_rows = [], [], []
        for inv in sorted(user_invites.get(tid, []), key=lambda i: str(i["legacy_id"])):
            if inv["email"] in has_account:
                q.add("INVITATION_ALREADY_HAS_ACCOUNT", "userInvitations", inv["legacy_id"], tid)
                continue
            if inviter is None:
                q.add("INVITER_UNRESOLVED", "userInvitations", inv["legacy_id"], tid)
                continue
            rid = role_ids.get(inv["role"])
            if rid is None:
                q.add("ROLE_NOT_SEEDED", "userInvitations", inv["legacy_id"], tid, role=inv["role"])
                continue
            iid = det_id(tid, "payroll.userInvitations", inv["legacy_id"])
            u_rows.append({"id": iid, "tenant_id": tid, "email": inv["email"], "token_hash": new_token_hash(),
                           "status": "PENDING", "expires_at": expires, "invited_by_user_id": inviter,
                           "created_at": inv["created_at"] or now, **audit})
            ur_rows.append({"id": det_id(tid, "user_invitation_role", f"{iid}:{rid}"), "tenant_id": tid,
                            "invitation_id": iid, "role_id": rid, **audit})
        for inv in sorted(employee_invites.get(tid, []), key=lambda i: str(i["legacy_id"])):
            emp = employees.get(inv["employee_id"])
            if emp is None:
                q.add("EMPLOYEE_NOT_MIGRATED", "employeeInvitation", inv["legacy_id"], tid)
                continue
            if emp["account"] is not None or inv["email"] in has_account:
                q.add("INVITATION_ALREADY_HAS_ACCOUNT", "employeeInvitation", inv["legacy_id"], tid)
                continue
            if inviter is None:
                q.add("INVITER_UNRESOLVED", "employeeInvitation", inv["legacy_id"], tid)
                continue
            e_rows.append({"id": det_id(tid, "payroll.employeeInvitation", inv["legacy_id"]), "tenant_id": tid,
                           "employee_id": inv["employee_id"], "email": inv["email"], "token_hash": new_token_hash(),
                           "status": "PENDING", "expires_at": expires, "invited_by_user_id": inviter, **audit})
        # A second run keeps the first token, expiry and status: the hash is never re-issued, and an
        # invitation accepted or revoked in the meantime is not reopened.
        frozen = ("token_hash", "expires_at", "status")
        db.upsert(cur, "core.user_invitation", u_rows, keep_on_update=frozen)
        db.upsert(cur, "core.user_invitation_role", ur_rows, conflict=("tenant_id", "invitation_id", "role_id"),
                  keep_on_update=("id", "updated_by"))
        db.upsert(cur, "core.employee_invitation", e_rows, keep_on_update=frozen)

        # hrms.ourusers -> account (founder tenant); payroll.companyUser "<tenant>:<legacy userId>" -> account
        for p in persons:
            for table, legacy_id in p.refs:
                if p.email in account_of:
                    idmap.put(table, legacy_id, account_of[p.email])
        return StepResult(processed=len(accounts) + len(user_roles) + links + len(u_rows) + len(e_rows),
                          skipped=q.count(tid))

    checksum = hashlib.sha256(f"{staging.checksum(INPUTS)}:keycloak={mode}".encode()).hexdigest()
    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, tenants.all(), checksum, work)
    finally:
        if own:
            conn.close()
    # An organisation in the map that load_tenant did not create (deleted, R5) never ran above.
    ran = set(summary.done) | set(summary.skipped_unchanged) | set(summary.failed)
    for tid in sorted(set(people) - ran):
        for p in people[tid].values():
            q.add("ORG_NOT_MIGRATED", p.ref[0], p.ref[1])
    for tid in sorted((set(user_invites) | set(employee_invites)) - ran):
        for inv in user_invites.get(tid, []):
            q.add("ORG_NOT_MIGRATED", "userInvitations", inv["legacy_id"])
        for inv in employee_invites.get(tid, []):
            q.add("ORG_NOT_MIGRATED", "employeeInvitation", inv["legacy_id"])
    idmap.save()
    q.write()
    log.info("Keycloak: %d user(s) created, %d reused", len(created), len(kc_ids) - len(created))
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
