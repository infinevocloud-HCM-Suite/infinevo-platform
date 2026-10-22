"""
PostgreSQL Target Database Loader (W-68.3)
Loads merged employees into PostgreSQL target schema:
- core.employee
- core.employee_personal
- core.employee_contact
- core.employee_identification
- payroll.employee_statutory_profile
- core.migration_ledger
"""

import os
import sys
import json
import uuid
import logging
from datetime import datetime, timezone
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_postgres")

def get_pg_connection():
    host = os.environ.get("TARGET_DB_HOST", "localhost")
    port = int(os.environ.get("TARGET_DB_PORT", "5432"))
    database = os.environ.get("TARGET_DB_NAME", "infinevo")
    user = os.environ.get("TARGET_DB_USER", "migration_user")
    password = os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw")
    
    logger.info(f"Connecting to PostgreSQL {host}:{port}/{database} as {user}...")
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=database,
        user=user,
        password=password
    )

def run_loader():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    staging_file = os.path.join(base_dir, "staging", "stg_merged_employees.json")
    
    if not os.path.exists(staging_file):
        logger.error(f"Merged staging file not found: {staging_file}")
        sys.exit(1)
        
    with open(staging_file, "r", encoding="utf-8") as f:
        employees = json.load(f)
        
    logger.info(f"Loaded {len(employees)} records from {staging_file}")
    
    conn = get_pg_connection()
    conn.autocommit = False
    cur = conn.cursor()
    
    step_name = "3_COHORT_EMPLOYEE_LOAD"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    tenant_id = employees[0]["tenant_id"] if employees else "a0000000-0000-0000-0000-000000000001"
    try:
        # 1. Ensure all needed tenants exist in core.tenant and have active subscriptions
        org_mapping_file = os.path.join(base_dir, "config", "org_mapping.json")
        if os.path.exists(org_mapping_file):
            with open(org_mapping_file, "r", encoding="utf-8") as f:
                org_map = json.load(f)
            needed_tenants = set(e["tenant_id"] for e in employees)
            tenant_batch = []
            for oid, data in org_map.items():
                t_id = data["tenant_id"]
                if t_id in needed_tenants:
                    tenant_batch.append((t_id, data.get("name") or f"Org {oid}"))
            logger.info(f"Ensuring {len(tenant_batch)} tenants exist in core.tenant...")
            execute_batch(
                cur,
                """
                INSERT INTO core.tenant (tenant_id, name, country_code, audit_retention_months, notification_retention_months, created_by, updated_by)
                VALUES (%s, %s, 'IN', 84, 12, 'system', 'system')
                ON CONFLICT (tenant_id) DO NOTHING
                """,
                tenant_batch
            )
            execute_batch(
                cur,
                """
                INSERT INTO core.subscription (tenant_id, status, created_by, updated_by)
                VALUES (%s, 'ACTIVE', 'system', 'system')
                ON CONFLICT (tenant_id) DO NOTHING
                """,
                [(t[0],) for t in tenant_batch]
            )
            conn.commit()

        # 2. Record migration start in ledger
        cur.execute(
            """
            INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (ledger_id, tenant_id, step_name, 0, "RUNNING", started_at)
        )
        conn.commit()
        
        # Prepare batches
        emp_batch = []
        personal_batch = []
        contact_batch = []
        ident_batch = []
        statutory_batch = []
        
        for e in employees:
            emp_id = e["id"]
            t_id = e["tenant_id"]
            
            # core.employee
            emp_batch.append((
                emp_id,
                t_id,
                e["employee_number"],
                e["first_name"],
                e.get("middle_name"),
                e.get("last_name"),
                e.get("gender"),
                e.get("date_of_joining") or "2025-01-01",
                e.get("work_email"),
                e.get("mobile"),
                e.get("status", "ACTIVE"),
                e.get("is_portal_enabled", True),
                e.get("is_deleted", False)
            ))
            
            # core.employee_personal
            personal_batch.append((
                str(uuid.uuid5(uuid.UUID(emp_id), "personal")),
                t_id,
                emp_id,
                e.get("date_of_birth"),
                e.get("marital_status"),
                e.get("nationality", "Indian")
            ))
            
            # core.employee_contact
            contact_batch.append((
                str(uuid.uuid5(uuid.UUID(emp_id), "contact")),
                t_id,
                emp_id,
                e.get("personal_email"),
                e.get("residential_address"),
                e.get("city"),
                e.get("state"),
                e.get("postal_code")
            ))
            
            # core.employee_identification
            ident_batch.append((
                str(uuid.uuid5(uuid.UUID(emp_id), "identification")),
                t_id,
                emp_id,
                e.get("pan_number"),
                e.get("aadhaar_number")
            ))
            
            # payroll.employee_statutory_profile
            statutory_batch.append((
                str(uuid.uuid5(uuid.UUID(emp_id), "statutory")),
                t_id,
                emp_id,
                e.get("is_eligible_for_pf", False),
                e.get("is_eligible_for_esi", False),
                e.get("is_eligible_for_pt", False),
                e.get("is_eligible_for_lwf", False),
                e.get("is_eligible_for_eps", False),
                e.get("contributes_eps_on_higher_wages", False),
                e.get("is_director", False),
                e.get("pf_account_number"),
                e.get("uan"),
                e.get("esi_number")
            ))
            
        logger.info(f"Upserting {len(emp_batch)} records into core.employee...")
        execute_batch(
            cur,
            """
            INSERT INTO core.employee (
                id, tenant_id, employee_number, first_name, middle_name, last_name,
                gender, date_of_joining, work_email, mobile, status, is_portal_enabled, is_deleted
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO UPDATE SET
                employee_number = EXCLUDED.employee_number,
                first_name = EXCLUDED.first_name,
                middle_name = EXCLUDED.middle_name,
                last_name = EXCLUDED.last_name,
                gender = EXCLUDED.gender,
                date_of_joining = EXCLUDED.date_of_joining,
                work_email = EXCLUDED.work_email,
                mobile = EXCLUDED.mobile,
                status = EXCLUDED.status,
                is_portal_enabled = EXCLUDED.is_portal_enabled,
                is_deleted = EXCLUDED.is_deleted,
                updated_at = CURRENT_TIMESTAMP
            """,
            emp_batch,
            page_size=200
        )
        
        logger.info(f"Upserting {len(personal_batch)} records into core.employee_personal...")
        execute_batch(
            cur,
            """
            INSERT INTO core.employee_personal (
                id, tenant_id, employee_id, date_of_birth, marital_status, nationality
            ) VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (tenant_id, employee_id) DO UPDATE SET
                date_of_birth = COALESCE(EXCLUDED.date_of_birth, core.employee_personal.date_of_birth),
                marital_status = COALESCE(EXCLUDED.marital_status, core.employee_personal.marital_status),
                nationality = COALESCE(EXCLUDED.nationality, core.employee_personal.nationality),
                updated_at = CURRENT_TIMESTAMP
            """,
            personal_batch,
            page_size=200
        )
        
        logger.info(f"Upserting {len(contact_batch)} records into core.employee_contact...")
        execute_batch(
            cur,
            """
            INSERT INTO core.employee_contact (
                id, tenant_id, employee_id, personal_email, address_line1, city, state, zip_code
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (tenant_id, employee_id) DO UPDATE SET
                personal_email = COALESCE(EXCLUDED.personal_email, core.employee_contact.personal_email),
                address_line1 = COALESCE(EXCLUDED.address_line1, core.employee_contact.address_line1),
                city = COALESCE(EXCLUDED.city, core.employee_contact.city),
                state = COALESCE(EXCLUDED.state, core.employee_contact.state),
                zip_code = COALESCE(EXCLUDED.zip_code, core.employee_contact.zip_code),
                updated_at = CURRENT_TIMESTAMP
            """,
            contact_batch,
            page_size=200
        )
        
        logger.info(f"Upserting {len(ident_batch)} records into core.employee_identification...")
        execute_batch(
            cur,
            """
            INSERT INTO core.employee_identification (
                id, tenant_id, employee_id, pan_number, aadhaar_number
            ) VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (tenant_id, employee_id) DO UPDATE SET
                pan_number = COALESCE(EXCLUDED.pan_number, core.employee_identification.pan_number),
                aadhaar_number = COALESCE(EXCLUDED.aadhaar_number, core.employee_identification.aadhaar_number),
                updated_at = CURRENT_TIMESTAMP
            """,
            ident_batch,
            page_size=200
        )
        
        logger.info(f"Upserting {len(statutory_batch)} records into payroll.employee_statutory_profile...")
        execute_batch(
            cur,
            """
            INSERT INTO payroll.employee_statutory_profile (
                id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_esi, is_eligible_for_pt,
                is_eligible_for_lwf, is_eligible_for_eps, contributes_eps_on_higher_wages, is_director,
                pf_account_number, uan, esi_number
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (tenant_id, employee_id) DO UPDATE SET
                is_eligible_for_pf = EXCLUDED.is_eligible_for_pf,
                is_eligible_for_esi = EXCLUDED.is_eligible_for_esi,
                is_eligible_for_pt = EXCLUDED.is_eligible_for_pt,
                is_eligible_for_lwf = EXCLUDED.is_eligible_for_lwf,
                is_eligible_for_eps = EXCLUDED.is_eligible_for_eps,
                contributes_eps_on_higher_wages = EXCLUDED.contributes_eps_on_higher_wages,
                is_director = EXCLUDED.is_director,
                pf_account_number = COALESCE(EXCLUDED.pf_account_number, payroll.employee_statutory_profile.pf_account_number),
                uan = COALESCE(EXCLUDED.uan, payroll.employee_statutory_profile.uan),
                esi_number = COALESCE(EXCLUDED.esi_number, payroll.employee_statutory_profile.esi_number),
                updated_at = CURRENT_TIMESTAMP
            """,
            statutory_batch,
            page_size=200
        )
        
        # 3. Update ledger record
        completed_at = datetime.now(timezone.utc)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET records_processed = %s, status = %s, completed_at = %s
            WHERE id = %s
            """,
            (len(employees), "SUCCESS", completed_at, ledger_id)
        )
        conn.commit()
        logger.info(f"=== Successfully Loaded {len(employees)} Employees into PostgreSQL! Ledger Updated ===")
        
    except Exception as e:
        conn.rollback()
        logger.error(f"Migration load failed: {e}", exc_info=True)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET status = %s, error_message = %s, completed_at = %s
            WHERE id = %s
            """,
            ("FAILED", str(e), datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    run_loader()
