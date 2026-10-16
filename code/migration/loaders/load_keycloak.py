"""
Keycloak User Provisioning Loader (Step 4 - W-68.3 / W-24.2)
Provisions 637 merged employees into Keycloak realm 'infinevo' and links to PostgreSQL:
  - Keycloak Admin REST API provisioning
  - core.user_account (profile in tenant)
  - core.user_tenant (multi-tenant mapping)
  - core.user_role (employee role per tenant)
  - core.employee.user_account_id linking
  - core.employee_invitation (for Cohort 2 HRMS-only employees, W-24.2 native)
  - core.migration_ledger tracking
"""

import os
import sys
import json
import uuid
import hashlib
import secrets
import logging
import requests
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_keycloak")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
DEFAULT_PASSWORD = "Welcome@Infinevo2026!"

def get_db_connection():
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw")
    )

class KeycloakAdminClient:
    def __init__(self, base_url: str, realm: str, admin_user: str, admin_pw: str):
        self.base_url = base_url.rstrip("/")
        self.realm = realm
        self.admin_user = admin_user
        self.admin_pw = admin_pw
        self.token = None
        self.token_expiry = datetime.min.replace(tzinfo=timezone.utc)

    def _ensure_token(self):
        now = datetime.now(timezone.utc)
        if self.token and now < self.token_expiry - timedelta(seconds=10):
            return

        logger.info(f"Authenticating with Keycloak Admin at {self.base_url}...")
        token_url = f"{self.base_url}/realms/master/protocol/openid-connect/token"
        data = {
            "client_id": "admin-cli",
            "grant_type": "password",
            "username": self.admin_user,
            "password": self.admin_pw
        }
        try:
            resp = requests.post(token_url, data=data, timeout=10)
            resp.raise_for_status()
            res = resp.json()
            self.token = res["access_token"]
            expires_in = res.get("expires_in", 60)
            self.token_expiry = now + timedelta(seconds=expires_in)
            logger.info("Keycloak admin token acquired successfully.")
        except Exception as e:
            logger.error(f"Failed to acquire Keycloak admin token: {e}")
            raise

    def get_existing_users(self) -> Dict[str, str]:
        """Fetch all users currently in the realm, mapped by lowercased email."""
        self._ensure_token()
        users_by_email = {}
        first = 0
        max_batch = 100
        while True:
            url = f"{self.base_url}/admin/realms/{self.realm}/users"
            headers = {"Authorization": f"Bearer {self.token}"}
            params = {"first": first, "max": max_batch}
            try:
                resp = requests.get(url, headers=headers, params=params, timeout=10)
                resp.raise_for_status()
                batch = resp.json()
                if not batch:
                    break
                for u in batch:
                    em = u.get("email")
                    if em:
                        users_by_email[em.lower().strip()] = u["id"]
                first += len(batch)
                if len(batch) < max_batch:
                    break
            except Exception as e:
                logger.error(f"Error fetching existing users: {e}")
                raise
        logger.info(f"Loaded {len(users_by_email)} existing users from Keycloak realm '{self.realm}'.")
        return users_by_email

    def find_user_by_email(self, email: str) -> Optional[str]:
        self._ensure_token()
        clean = email.strip().lower()
        url = f"{self.base_url}/admin/realms/{self.realm}/users"
        headers = {"Authorization": f"Bearer {self.token}"}
        params = {"email": clean, "exact": "true"}
        try:
            resp = requests.get(url, headers=headers, params=params, timeout=10)
            resp.raise_for_status()
            res = resp.json()
            if res and len(res) > 0:
                return res[0]["id"]
            return None
        except Exception as e:
            logger.warning(f"Error searching user {email}: {e}")
            return None

    def create_user(self, email: str, first_name: str, last_name: str, is_cohort_2: bool) -> str:
        self._ensure_token()
        clean_email = email.strip().lower()
        required_actions = ["UPDATE_PASSWORD"] if is_cohort_2 else []
        
        payload = {
            "username": clean_email,
            "email": clean_email,
            "firstName": first_name or "",
            "lastName": last_name or "",
            "enabled": True,
            "emailVerified": True,
            "requiredActions": required_actions
        }
        url = f"{self.base_url}/admin/realms/{self.realm}/users"
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=10)
            if resp.status_code == 409:
                existing_id = self.find_user_by_email(clean_email)
                if existing_id:
                    return existing_id
            resp.raise_for_status()
            location = resp.headers.get("Location")
            if location:
                uid = location.rstrip("/").split("/")[-1]
                self.set_temporary_password(uid, DEFAULT_PASSWORD)
                return uid
        except requests.HTTPError as e:
            if e.response is not None and e.response.status_code == 409:
                existing_id = self.find_user_by_email(clean_email)
                if existing_id:
                    return existing_id
            logger.error(f"HTTP error creating user {clean_email}: {e}")
            raise
        except Exception as e:
            logger.error(f"Error creating user {clean_email}: {e}")
            raise

        uid = self.find_user_by_email(clean_email)
        if uid:
            self.set_temporary_password(uid, DEFAULT_PASSWORD)
            return uid
        raise RuntimeError(f"Failed to retrieve user ID for {clean_email} after creation")

    def set_temporary_password(self, user_id: str, password: str):
        self._ensure_token()
        url = f"{self.base_url}/admin/realms/{self.realm}/users/{user_id}/reset-password"
        payload = {
            "type": "password",
            "value": password,
            "temporary": True
        }
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }
        try:
            resp = requests.put(url, json=payload, headers=headers, timeout=10)
            resp.raise_for_status()
        except Exception as e:
            logger.warning(f"Could not set password for user {user_id}: {e}")

def hash_invitation_token(raw_token: str) -> str:
    return hashlib.sha256(raw_token.strip().encode("utf-8")).hexdigest()

def run_provisioning():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    staging_file = os.path.join(base_dir, "staging", "stg_merged_employees.json")

    if not os.path.exists(staging_file):
        logger.error(f"Staging file not found: {staging_file}")
        sys.exit(1)

    with open(staging_file, "r", encoding="utf-8") as f:
        employees = json.load(f)

    logger.info(f"Loaded {len(employees)} merged employee records for Keycloak provisioning.")

    kc_url = os.environ.get("KEYCLOAK_URL", "http://localhost:8081")
    kc_realm = os.environ.get("KEYCLOAK_REALM", "infinevo")
    kc_user = os.environ.get("KEYCLOAK_ADMIN_USER", "admin")
    kc_pw = os.environ.get("KEYCLOAK_ADMIN_PW", "local_keycloak_pw")

    client = KeycloakAdminClient(kc_url, kc_realm, kc_user, kc_pw)

    # 1. Pre-fetch existing Keycloak users
    existing_kc_users = client.get_existing_users()

    # 2. Identify all distinct work emails and cohort requirements
    distinct_emails = {}
    for emp in employees:
        email = emp.get("work_email")
        if not email:
            continue
        email_clean = email.strip().lower()
        if email_clean not in distinct_emails:
            distinct_emails[email_clean] = {
                "first_name": emp.get("first_name", ""),
                "last_name": emp.get("last_name", ""),
                "is_cohort_2": (emp.get("cohort") == "COHORT_2_HRMS_ONLY")
            }
        elif emp.get("cohort") == "COHORT_2_HRMS_ONLY":
            distinct_emails[email_clean]["is_cohort_2"] = True

    logger.info(f"Found {len(distinct_emails)} distinct work emails to sync with Keycloak.")

    # 3. Provision missing users in Keycloak
    email_to_kc_id = {}
    new_users_count = 0
    existing_users_count = 0

    for email, meta in distinct_emails.items():
        if email in existing_kc_users:
            email_to_kc_id[email] = existing_kc_users[email]
            existing_users_count += 1
        else:
            uid = client.create_user(
                email=email,
                first_name=meta["first_name"],
                last_name=meta["last_name"],
                is_cohort_2=meta["is_cohort_2"]
            )
            email_to_kc_id[email] = uid
            existing_kc_users[email] = uid
            new_users_count += 1
            if new_users_count % 50 == 0:
                logger.info(f"Provisioned {new_users_count} new users in Keycloak...")

    logger.info(f"Keycloak Provisioning Phase Complete: {new_users_count} created, {existing_users_count} matched existing.")

    # 4. Connect to PostgreSQL and sync DB tables
    conn = get_db_connection()
    conn.autocommit = False
    cur = conn.cursor()

    ledger_id = str(uuid.uuid4())
    step_name = "KEYCLOAK_USER_PROVISIONING"
    started_at = datetime.now(timezone.utc)
    tenant_id = employees[0]["tenant_id"] if employees else "a0000000-0000-0000-0000-000000000001"

    try:
        # Record start in ledger
        cur.execute(
            """
            INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (ledger_id, tenant_id, step_name, 0, "RUNNING", started_at)
        )
        conn.commit()

        # Pre-fetch 'employee' role for every tenant
        cur.execute("SELECT tenant_id, id FROM core.role WHERE code = 'employee'")
        tenant_employee_roles = {str(row[0]): str(row[1]) for row in cur.fetchall()}
        logger.info(f"Mapped 'employee' roles for {len(tenant_employee_roles)} tenants.")

        # Prepare batches
        user_accounts = []
        user_tenants = []
        user_roles = []
        employee_updates = []
        invitations = []

        now = datetime.now(timezone.utc)
        expires_at = now + timedelta(days=7)

        for emp in employees:
            emp_id = emp["id"]
            t_id = emp["tenant_id"]
            email = (emp.get("work_email") or "").strip().lower()
            kc_user_id = email_to_kc_id.get(email)

            if not kc_user_id:
                logger.error(f"Missing Keycloak user ID for employee {emp_id} ({email})")
                continue

            user_account_id = str(uuid.uuid5(NAMESPACE, f"user_account:{t_id}:{kc_user_id}"))

            # 1. core.user_account
            user_accounts.append((
                user_account_id,
                t_id,
                kc_user_id,
                email,
                emp.get("first_name"),
                emp.get("last_name"),
                emp.get("mobile"),
                "ACTIVE",
                now,
                now,
                "migration",
                now,
                "migration"
            ))

            # 2. core.user_tenant
            user_tenant_id = str(uuid.uuid5(NAMESPACE, f"user_tenant:{t_id}:{kc_user_id}"))
            user_tenants.append((
                user_tenant_id,
                t_id,
                kc_user_id,
                now,
                "migration",
                now,
                "migration"
            ))

            # 3. core.user_role ('employee' role)
            emp_role_id = tenant_employee_roles.get(t_id)
            if emp_role_id:
                user_role_id = str(uuid.uuid5(NAMESPACE, f"user_role:{t_id}:{user_account_id}:{emp_role_id}"))
                user_roles.append((
                    user_role_id,
                    t_id,
                    user_account_id,
                    emp_role_id,
                    now,
                    "migration",
                    now,
                    "migration"
                ))

            # 4. core.employee linking
            employee_updates.append((
                user_account_id,
                emp_id
            ))

            # 5. Cohort 2 Invitation (W-24.2)
            if emp.get("cohort") == "COHORT_2_HRMS_ONLY":
                raw_token = secrets.token_hex(32)
                token_hash = hash_invitation_token(raw_token)
                inv_id = str(uuid.uuid5(NAMESPACE, f"invitation:{emp_id}"))
                invitations.append((
                    inv_id,
                    t_id,
                    emp_id,
                    email,
                    token_hash,
                    "PENDING",
                    expires_at,
                    kc_user_id,
                    [emp_role_id] if emp_role_id else [],
                    now,
                    "migration",
                    now,
                    "migration"
                ))

        logger.info(f"Upserting {len(user_accounts)} records into core.user_account...")
        execute_batch(
            cur,
            """
            INSERT INTO core.user_account (
                id, tenant_id, keycloak_user_id, email, first_name, last_name, phone_number,
                status, last_synced_at, created_at, created_by, updated_at, updated_by
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (tenant_id, keycloak_user_id) DO UPDATE SET
                email = EXCLUDED.email,
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                phone_number = EXCLUDED.phone_number,
                last_synced_at = EXCLUDED.last_synced_at,
                updated_at = EXCLUDED.updated_at
            """,
            user_accounts,
            page_size=200
        )

        logger.info(f"Upserting {len(user_tenants)} records into core.user_tenant...")
        execute_batch(
            cur,
            """
            INSERT INTO core.user_tenant (
                id, tenant_id, user_id, created_at, created_by, updated_at, updated_by
            ) VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (user_id, tenant_id) DO NOTHING
            """,
            user_tenants,
            page_size=200
        )

        logger.info(f"Upserting {len(user_roles)} records into core.user_role...")
        execute_batch(
            cur,
            """
            INSERT INTO core.user_role (
                id, tenant_id, user_account_id, role_id, created_at, created_by, updated_at, updated_by
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (tenant_id, user_account_id, role_id) DO NOTHING
            """,
            user_roles,
            page_size=200
        )

        logger.info(f"Updating user_account_id on {len(employee_updates)} core.employee records...")
        execute_batch(
            cur,
            """
            UPDATE core.employee
            SET user_account_id = %s, updated_at = CURRENT_TIMESTAMP
            WHERE id = %s
            """,
            employee_updates,
            page_size=200
        )

        if invitations:
            logger.info(f"Creating {len(invitations)} Cohort 2 employee invitations in core.employee_invitation...")
            execute_batch(
                cur,
                """
                INSERT INTO core.employee_invitation (
                    id, tenant_id, employee_id, email, token_hash, status,
                    expires_at, invited_by_user_id, role_ids, created_at, created_by, updated_at, updated_by
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::uuid[], %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    token_hash = EXCLUDED.token_hash,
                    status = EXCLUDED.status,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = EXCLUDED.updated_at
                """,
                invitations,
                page_size=50
            )

        # Update ledger
        completed_at = datetime.now(timezone.utc)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET records_processed = %s, status = 'SUCCESS', completed_at = %s
            WHERE id = %s
            """,
            (len(employee_updates), completed_at, ledger_id)
        )
        conn.commit()
        logger.info("=== STEP 4 COMPLETE: Keycloak User Provisioning & Linking Successful! ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Step 4 failed: {e}", exc_info=True)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET status = 'FAILED', error_message = %s, completed_at = %s
            WHERE id = %s
            """,
            (str(e), datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    run_provisioning()
