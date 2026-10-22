"""3d load_users against a real migrated Postgres; Keycloak is a fake (W-68 Task 4 risk row)."""
import hashlib
import json
import uuid

import pytest

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.fixtures.fx_users import LEGACY_TOKEN
from tests.pipeline import run_base_and

KC_NS = uuid.UUID("12345678-1234-5678-1234-567812345678")
WRITTEN = ["core.user_account", "core.user_tenant", "core.user_role", "core.user_invitation",
           "core.user_invitation_role", "core.employee_invitation", "core.employee"]


class FakeKeycloak:
    """The two calls load_users makes, against an in-memory realm."""

    def __init__(self, existing=()):
        self.users = {e: {"id": str(uuid.uuid5(KC_NS, "pre:" + e)), "requiredActions": []} for e in existing}
        self.created = []

    def find_user_by_email(self, email):
        u = self.users.get(email)
        return u["id"] if u else None

    def create_user(self, email, first_name, last_name):
        assert email not in self.users, "created a user that already exists"
        uid = str(uuid.uuid5(KC_NS, email))
        self.users[email] = {"id": uid, "requiredActions": ["UPDATE_PASSWORD"]}
        self.created.append(email)
        return uid


@pytest.fixture
def fake_kc(monkeypatch):
    from loaders import load_users

    kc = FakeKeycloak(existing=["e2@founder.test"])
    monkeypatch.delenv("MIGRATION_KEYCLOAK", raising=False)
    monkeypatch.setattr(load_users, "keycloak_client", lambda: kc)
    return kc


def _quarantine(staging_dir):
    return json.loads((staging_dir / "stg_quarantined_load_users.json").read_text())


def _roles(query, tenant):
    out = {}
    for email, code in query("SELECT ua.email, r.code FROM core.user_role ur JOIN core.user_account ua "
                             "ON ua.id = ur.user_account_id JOIN core.role r ON r.id = ur.role_id "
                             "WHERE ur.tenant_id = %s", (tenant,)):
        out.setdefault(email, set()).add(code)
    return out


def test_accounts_roles_links_and_invitations(legacy_fixture, query, fake_kc):
    run_base_and("loaders.load_users")

    # Keycloak: one user per distinct email, looked up first; e2 already existed and is reused
    assert sorted(fake_kc.created) == sorted(["e1@founder.test", "e3@founder.test", "e15@founder.test",
                                              "ops@founder.test", "multi@acme.test", "c1@acme.test",
                                              "owner@acme.test", "c2@acme.test"])
    accounts = {(t, e): kc for t, e, kc in query(
        "SELECT tenant_id::text, email, keycloak_user_id::text FROM core.user_account")}
    assert len(accounts) == 10
    assert accounts[(FOUNDER_TENANT, "e2@founder.test")] == fake_kc.users["e2@founder.test"]["id"]
    # HRMS and Payroll logins with one email are one account (e1); one Keycloak user in two tenants (multi)
    assert accounts[(FOUNDER_TENANT, "multi@acme.test")] == accounts[(CUSTOMER_TENANT, "multi@acme.test")]
    assert query("SELECT count(*) FROM core.user_account WHERE status <> 'ACTIVE'")[0][0] == 0
    assert query("SELECT first_name, last_name, phone_number FROM core.user_account WHERE email = 'e1@founder.test'") \
        == [("First1", "Last1", "9100000010")]
    # user_tenant is keyed by the Keycloak id, as acceptance writes it
    assert query("SELECT count(*) FROM core.user_tenant ut JOIN core.user_account ua "
                 "ON ua.keycloak_user_id = ut.user_id AND ua.tenant_id = ut.tenant_id")[0][0] == 10

    assert _roles(query, FOUNDER_TENANT) == {
        "e1@founder.test": {"tenant-admin", "employee"},
        "e2@founder.test": {"hr", "manager", "employee"},
        "e3@founder.test": {"employee"},
        "e15@founder.test": {"employee"},
        "ops@founder.test": {"tenant-admin"},
        "multi@acme.test": {"payroll-officer"},
    }
    assert _roles(query, CUSTOMER_TENANT) == {
        "c1@acme.test": {"employee"}, "owner@acme.test": {"tenant-admin"},
        "c2@acme.test": {"employee"}, "multi@acme.test": {"hr"},
    }

    links = dict(query("SELECT e.employee_number, ua.email FROM core.employee e JOIN core.user_account ua "
                       "ON ua.id = e.user_account_id"))
    assert links == {"E001": "e1@founder.test", "E002": "e2@founder.test", "E003": "e3@founder.test",
                     "E015": "e15@founder.test", "C001": "c1@acme.test", "C002": "c2@acme.test"}
    portal = dict(query("SELECT employee_number, is_portal_enabled FROM core.employee "
                        "WHERE employee_number IN ('E001', 'C001', 'C002', 'E004')"))
    assert portal == {"E001": False, "C001": False, "C002": True, "E004": True}

    # Pending invitations: new token, hash only, inviter = the tenant's owner
    owner = {t: query("SELECT id::text FROM core.user_account WHERE tenant_id = %s AND email = %s", (t, e))[0][0]
             for t, e in ((FOUNDER_TENANT, "e1@founder.test"), (CUSTOMER_TENANT, "owner@acme.test"))}
    uinv = query("SELECT ui.tenant_id::text, ui.email, ui.status, ui.invited_by_user_id::text, r.code, ui.token_hash, "
                 "ui.expires_at > now() + interval '6 days' FROM core.user_invitation ui "
                 "JOIN core.user_invitation_role uir ON uir.invitation_id = ui.id JOIN core.role r ON r.id = uir.role_id "
                 "ORDER BY ui.email")
    assert [(r[0], r[1], r[2], r[3], r[4], r[6]) for r in uinv] == [
        (FOUNDER_TENANT, "fin@founder.test", "PENDING", owner[FOUNDER_TENANT], "finance", True),
        (CUSTOMER_TENANT, "newhire@acme.test", "PENDING", owner[CUSTOMER_TENANT], "hr", True),
    ]
    einv = query("SELECT ei.tenant_id::text, e.employee_number, ei.status, ei.invited_by_user_id::text, ei.token_hash "
                 "FROM core.employee_invitation ei JOIN core.employee e ON e.id = ei.employee_id ORDER BY 2")
    assert [r[:4] for r in einv] == [(CUSTOMER_TENANT, "C003", "PENDING", owner[CUSTOMER_TENANT]),
                                     (FOUNDER_TENANT, "E004", "PENDING", owner[FOUNDER_TENANT])]
    legacy_hashes = {hashlib.sha256(f"{LEGACY_TOKEN[:-2]}{i:02d}".encode()).hexdigest() for i in range(1, 9)}
    legacy_hashes |= {hashlib.sha256(f"emptoken{i:012d}".encode()).hexdigest() for i in range(1, 7)}
    for h in [r[5] for r in uinv] + [r[4] for r in einv]:
        assert len(h) == 64 and all(c in "0123456789abcdef" for c in h)
        assert h not in legacy_hashes

    q = _quarantine(legacy_fixture)
    got = {(i["reason"], i["legacy_table"], str(i["legacy_id"])) for i in q}
    for expected in [("ROLE_UNMAPPED", "user_roles", "3:7"), ("ROLE_UNMAPPED", "organizationUserRoleMapping", "4"),
                     ("ROLE_UNMAPPED", "userInvitations", "5"), ("NO_EMAIL", "ourusers", "6"),
                     ("USER_NOT_FOUND", "user_roles", "99:1"), ("USER_NOT_FOUND", "organizationUserRoleMapping", "7"),
                     ("USER_NO_ORG", "companyUser", "15"),
                     ("ORG_NOT_MIGRATED", "payroll.companyUser", "c0000000-0000-0000-0000-000000000202:kc-old-5"),
                     ("INVITATION_ALREADY_HAS_ACCOUNT", "userInvitations", "4"),
                     ("INVITATION_ALREADY_HAS_ACCOUNT", "employeeInvitation", "2"),
                     ("INVITATION_EXPIRED", "employeeInvitation", "3"),
                     ("EMPLOYEE_NOT_MIGRATED", "employeeInvitation", "4")]:
        assert expected in got, expected
    text = json.dumps(q)
    assert "@" not in text and "password" not in text.lower() and "legacytoken" not in text
    assert query("SELECT count(*) FROM core.migration_ledger WHERE step_name = 'load_users' AND status = 'DONE'")[0][0] == 2


def test_tenant_isolation(legacy_fixture, query, fake_kc):
    run_base_and("loaders.load_users")
    # every grant, link and invitation stays inside the account's own tenant
    assert query("SELECT count(*) FROM core.user_role ur JOIN core.user_account ua ON ua.id = ur.user_account_id "
                 "JOIN core.role r ON r.id = ur.role_id "
                 "WHERE ua.tenant_id <> ur.tenant_id OR r.tenant_id <> ur.tenant_id")[0][0] == 0
    assert query("SELECT count(*) FROM core.employee e JOIN core.user_account ua ON ua.id = e.user_account_id "
                 "WHERE ua.tenant_id <> e.tenant_id")[0][0] == 0
    assert query("SELECT count(*) FROM core.employee_invitation ei JOIN core.employee e ON e.id = ei.employee_id "
                 "WHERE e.tenant_id <> ei.tenant_id")[0][0] == 0
    per_tenant = dict(query("SELECT tenant_id::text, count(*) FROM core.user_account GROUP BY 1"))
    assert per_tenant == {FOUNDER_TENANT: 6, CUSTOMER_TENANT: 4}


def test_second_run_changes_nothing(legacy_fixture, query, fake_kc):
    run_base_and("loaders.load_users")
    snap = "SELECT count(*), max(updated_at) FROM {}"
    before = {t: query(snap.format(t))[0] for t in WRITTEN}
    hashes = query("SELECT id, token_hash FROM core.user_invitation UNION ALL "
                   "SELECT id, token_hash FROM core.employee_invitation ORDER BY 1")
    run_base_and("loaders.load_users")                    # ledger: DONE with the same checksum, skipped
    assert {t: query(snap.format(t))[0] for t in WRITTEN} == before
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_users'")
    created = list(fake_kc.created)
    run_base_and("loaders.load_users")                    # the work itself again: upserts change nothing
    assert {t: query(snap.format(t))[0] for t in WRITTEN} == before
    assert fake_kc.created == created                     # every user found by email, none created
    assert query("SELECT id, token_hash FROM core.user_invitation UNION ALL "
                 "SELECT id, token_hash FROM core.employee_invitation ORDER BY 1") == hashes
    assert query("SELECT tenant_id::text, count(*) FROM core.migration_ledger WHERE step_name = 'load_users' "
                 "AND status = 'DONE' GROUP BY 1 ORDER BY 1") == [(FOUNDER_TENANT, 1), (CUSTOMER_TENANT, 1)]


def test_keycloak_off_then_on(legacy_fixture, query, monkeypatch):
    from loaders import load_users

    monkeypatch.setenv("MIGRATION_KEYCLOAK", "off")
    for name in ("KEYCLOAK_URL", "KEYCLOAK_REALM", "KEYCLOAK_ADMIN_USER", "KEYCLOAK_ADMIN_PASSWORD"):
        monkeypatch.delenv(name, raising=False)       # off needs no Keycloak settings and calls nothing
    run_base_and("loaders.load_users")
    assert query("SELECT count(*) FROM core.user_account")[0][0] == 0
    assert query("SELECT count(*) FROM core.employee WHERE user_account_id IS NOT NULL")[0][0] == 0
    off = [i for i in _quarantine(legacy_fixture) if i["reason"] == "KEYCLOAK_OFF"]
    assert len(off) == 10
    assert query("SELECT count(*) FROM core.migration_ledger WHERE step_name = 'load_users' AND status = 'DONE'")[0][0] == 2

    kc = FakeKeycloak()
    monkeypatch.setenv("MIGRATION_KEYCLOAK", "on")
    monkeypatch.setattr(load_users, "keycloak_client", lambda: kc)
    run_base_and("loaders.load_users")                    # a different checksum: not skipped
    assert query("SELECT count(*) FROM core.user_account")[0][0] == 10
    assert len(kc.created) == 9


# ---- the HTTP client, without a realm

class _Resp:
    def __init__(self, status, body=None, headers=None):
        self.status_code, self._body, self.headers = status, body, headers or {}

    def json(self):
        return self._body


class _Http:
    def __init__(self, search_results, create_status=201):
        self.calls = []
        self.search_results = list(search_results)
        self.create_status = create_status

    def post(self, url, data=None, json=None, headers=None, timeout=None):
        self.calls.append(("POST", url, data, json))
        if url.endswith("/token"):
            return _Resp(200, {"access_token": "t", "expires_in": 300})
        if self.create_status == 201:
            return _Resp(201, headers={"Location": url + "/new-user-id"})
        return _Resp(self.create_status)

    def get(self, url, params=None, headers=None, timeout=None):
        self.calls.append(("GET", url, params, None))
        return _Resp(200, self.search_results.pop(0) if self.search_results else [])


def test_keycloak_client_creates_without_password():
    from loaders.load_users import KeycloakAdmin

    http = _Http(search_results=[[]])
    kc = KeycloakAdmin("https://kc.example/auth/", "infinevo", "admin", "pw", http=http)
    assert kc.find_user_by_email("new@x.test") is None
    assert kc.create_user("new@x.test", "New", None) == "new-user-id"
    token_call = http.calls[0]
    assert token_call[1] == "https://kc.example/auth/realms/master/protocol/openid-connect/token"
    assert token_call[2]["client_id"] == "admin-cli"
    create = next(c for c in http.calls if c[0] == "POST" and c[1].endswith("/admin/realms/infinevo/users"))
    assert create[3]["requiredActions"] == ["UPDATE_PASSWORD"]
    assert "credentials" not in create[3] and "password" not in create[3]
    assert not any(c[1].endswith("/reset-password") for c in http.calls)
    assert sum(1 for c in http.calls if c[1].endswith("/token")) == 1   # token reused


def test_keycloak_client_409_finds_existing():
    from loaders.load_users import KeycloakAdmin

    http = _Http(search_results=[[{"id": "other", "email": "someone@x.test"}, {"id": "kc-1", "email": "a@x.test"}]],
                 create_status=409)
    kc = KeycloakAdmin("https://kc.example", "infinevo", "admin", "pw", http=http)
    assert kc.create_user("a@x.test", "A", "B") == "kc-1"
