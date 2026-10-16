"""
HRMS MySQL Extractor (W-68.1)
Extracts authoritative HRMS tables from legacy MySQL into local staging.
Operates exclusively in READ-ONLY mode. Zero mutations on source databases.
"""

import os
import sys
import json
import logging
from typing import Dict, Any, List
import pymysql

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("extract_hrms")

TABLES_TO_EXTRACT = [
    "ourusers",
    "employee",
    "personal",
    "contact",
    "identification",
    "work",
    "timesheets",
    "timesheet",
    "day_entry",
    "task_entry",
    "projects",
    "tasks",
    "assignments",
    "leave_requests",
    "leave_balances",
    "leave_types",
    "attendance",
    "clock_sessions",
    "employee_document",
    "holidays",
    "user_roles",
    "user_action_mapping"
]

def get_connection(host: str, port: int, user: str, password: str, database: str):
    logger.info(f"Connecting to MySQL {host}:{port}/{database} (Read-Only session)...")
    ssl_config = {"ssl": True} if host not in ("localhost", "127.0.0.1") else None
    conn = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10,
        read_timeout=30,
        write_timeout=30,
        ssl=ssl_config
    )
    with conn.cursor() as cur:
        cur.execute("SET SESSION TRANSACTION READ ONLY")
    return conn

def extract_table(conn, table_name: str, staging_dir: str) -> int:
    output_file = os.path.join(staging_dir, f"stg_hrms_{table_name}.json")
    logger.info(f"Extracting table '{table_name}'...")
    
    with conn.cursor() as cur:
        cur.execute(f"SHOW TABLES LIKE '{table_name}'")
        if not cur.fetchone():
            logger.warning(f"Table '{table_name}' does not exist in HRMS database. Skipping.")
            return 0
            
        cur.execute(f"SELECT COUNT(*) AS total FROM `{table_name}`")
        count = cur.fetchone()["total"]
        logger.info(f"Table '{table_name}' contains {count} rows.")
        
        cur.execute(f"SELECT * FROM `{table_name}`")
        rows = cur.fetchall()
        
    def default_serializer(obj):
        return str(obj)

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(rows, f, default=default_serializer, indent=2)
        
    logger.info(f"Saved {len(rows)} records to '{output_file}'")
    return len(rows)

def main():
    host = os.environ.get("LEGACY_HRMS_DB_HOST")
    port = int(os.environ.get("LEGACY_HRMS_DB_PORT", "3306"))
    database = os.environ.get("LEGACY_HRMS_DB_NAME", "hrmstestdb")
    user = os.environ.get("LEGACY_HRMS_DB_USER", "ro_migration_user")
    password = os.environ.get("LEGACY_HRMS_DB_PASSWORD")
    
    if not host or not password:
        logger.error("LEGACY_HRMS_DB_HOST and LEGACY_HRMS_DB_PASSWORD environment variables are required.")
        sys.exit(1)
    
    staging_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "staging")
    os.makedirs(staging_dir, exist_ok=True)
    
    logger.info("=== Starting HRMS MySQL Extraction (W-68.1) ===")
    logger.info(f"Source: {host}:{port}/{database}")
    logger.info(f"Staging Directory: {staging_dir}")
    
    try:
        conn = get_connection(host, port, user, password, database)
    except Exception as e:
        logger.error(f"Failed to connect to source HRMS database: {e}")
        sys.exit(1)
        
    summary = {}
    try:
        for tbl in TABLES_TO_EXTRACT:
            summary[tbl] = extract_table(conn, tbl, staging_dir)
    finally:
        conn.close()
        logger.info("HRMS MySQL connection closed.")
        
    logger.info("=== HRMS Extraction Completed Successfully ===")
    logger.info("Summary of extracted tables:")
    for tbl, count in summary.items():
        logger.info(f" - {tbl}: {count} records")

if __name__ == "__main__":
    main()
