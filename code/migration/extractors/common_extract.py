"""Shared code for the two legacy extractors (W-68 Task 1).

Reads every listed table from a legacy MySQL database in a read-only session and writes
staging/stg_<source>_<table>.json through common.staging. Configuration comes from the
environment only (common.env.require): a missing variable exits 2 naming it. Row values
are never logged; only table names and counts are.
"""
from __future__ import annotations

import base64
import datetime as dt
import logging
import sys
from decimal import Decimal
from pathlib import Path

import pymysql
from pymysql.constants import FIELD_TYPE

MIGRATION_DIR = Path(__file__).resolve().parents[1]
if str(MIGRATION_DIR) not in sys.path:
    sys.path.insert(0, str(MIGRATION_DIR))

from common import staging  # noqa: E402
from common.env import optional, require  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

_LOCAL_HOSTS = ("localhost", "127.0.0.1", "::1")


def connection_settings(prefix: str) -> dict:
    """LEGACY_<prefix>_DB_HOST/PORT/NAME/USER/PASSWORD. Only the port has a default."""
    base = f"LEGACY_{prefix}_DB_"
    return {
        "host": require(base + "HOST"),
        "port": int(optional(base + "PORT", "3306")),
        "database": require(base + "NAME"),
        "user": require(base + "USER"),
        "password": require(base + "PASSWORD"),
    }


def get_connection(settings: dict):
    """Read-only session; SSL for any host that is not local."""
    ssl_config = None if settings["host"] in _LOCAL_HOSTS else {"ssl": True}
    conn = pymysql.connect(
        host=settings["host"],
        port=settings["port"],
        user=settings["user"],
        password=settings["password"],
        database=settings["database"],
        charset="utf8mb4",
        cursorclass=pymysql.cursors.Cursor,
        connect_timeout=10,
        read_timeout=300,
        write_timeout=30,
        ssl=ssl_config,
    )
    with conn.cursor() as cur:
        cur.execute("SET SESSION TRANSACTION READ ONLY")
    return conn


def _value(v, type_code):
    """JSON-safe value: dates ISO, Decimal and float as str (never a JSON float), BIT as bool."""
    if v is None:
        return None
    if type_code == FIELD_TYPE.BIT and isinstance(v, (bytes, bytearray)):
        n = int.from_bytes(v, "big")
        return bool(n) if len(v) == 1 else n
    if isinstance(v, bool):
        return v
    if isinstance(v, (dt.datetime, dt.date, dt.time)):
        return v.isoformat()
    if isinstance(v, dt.timedelta):  # MySQL TIME
        total = int(v.total_seconds())
        sign = "-" if total < 0 else ""
        total = abs(total)
        return f"{sign}{total // 3600:02d}:{total % 3600 // 60:02d}:{total % 60:02d}"
    if isinstance(v, Decimal):
        return str(v)
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, (bytes, bytearray)):
        try:
            return v.decode("utf-8")
        except UnicodeDecodeError:
            return base64.b64encode(v).decode("ascii")
    return v


def _actual_name(cur, table: str) -> str | None:
    """The table's name as the server stores it, or None when absent (case-insensitive,
    so a server with lower_case_table_names=1 still finds `paySchedule`)."""
    cur.execute(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(%s)",
        (table,),
    )
    found = [r[0] for r in cur.fetchall()]
    if not found:
        return None
    return table if table in found else found[0]


def _select_list(cur, actual: str, excluded: set[str]) -> str:
    """`*`, or every column by name except the excluded (binary) ones."""
    if not excluded:
        return "*"
    drop = {e.lower() for e in excluded}
    cur.execute(
        "SELECT column_name FROM information_schema.columns "
        "WHERE table_schema = DATABASE() AND table_name = %s ORDER BY ordinal_position",
        (actual,),
    )
    cols = [r[0] for r in cur.fetchall() if r[0].lower() not in drop]
    return ", ".join(f"`{c}`" for c in cols)


def count_table(conn, table: str) -> int | None:
    with conn.cursor() as cur:
        actual = _actual_name(cur, table)
        if actual is None:
            return None
        # Name comes from information_schema, never from input.
        # nosemgrep: python.lang.security.audit.formatted-sql-query.formatted-sql-query
        cur.execute(f"SELECT COUNT(*) FROM `{actual}`")
        return int(cur.fetchone()[0])


def extract_table(conn, source: str, table: str, excluded: set[str], log) -> int | None:
    """Write staging/stg_<source>_<table>.json; None when the table does not exist."""
    with conn.cursor() as cur:
        actual = _actual_name(cur, table)
        if actual is None:
            return None
        cols = _select_list(cur, actual, excluded)
        # Names come from information_schema, never from input.
        # nosemgrep: python.lang.security.audit.formatted-sql-query.formatted-sql-query
        cur.execute(f"SELECT {cols} FROM `{actual}`")
        names = [d[0] for d in cur.description]
        types = [d[1] for d in cur.description]
        rows = [
            {n: _value(v, t) for n, v, t in zip(names, raw, types)}
            for raw in cur.fetchall()
        ]
    staging.write(f"stg_{source}_{table}.json", rows)
    log.info("  %-45s %8d rows", table, len(rows))
    return len(rows)


def run(source: str, prefix: str, tables, count_only, excluded_columns: dict) -> dict:
    """Extract `tables`, count `count_only`; write and return the summary."""
    log = logging.getLogger(f"extract_{source}")
    settings = connection_settings(prefix)  # exits 2 before any network call
    log.info("=== %s extraction (W-68.1): %s:%s/%s, read-only ===",
             source.upper(), settings["host"], settings["port"], settings["database"])
    log.info("Staging directory: %s", staging.staging_dir())
    try:
        conn = get_connection(settings)
    except pymysql.MySQLError as e:
        log.error("Cannot connect to the legacy %s database: %s", source, e.__class__.__name__)
        sys.exit(1)

    extracted: dict[str, int] = {}
    counted: dict[str, int] = {}
    missing: list[str] = []
    try:
        for table in tables:
            n = extract_table(conn, source, table, set(excluded_columns.get(table, ())), log)
            if n is None:
                log.warning("  %-45s missing in source, skipped", table)
                missing.append(table)
            else:
                extracted[table] = n
        for table in count_only:
            n = count_table(conn, table)
            if n is None:
                log.warning("  %-45s (count only) missing in source", table)
                missing.append(table)
            else:
                counted[table] = n
                log.info("  %-45s %8d rows (count only, no file)", table, n)
    finally:
        conn.close()

    summary = {
        "source": source,
        "database": settings["database"],
        "extracted_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "tables_listed": len(tables),
        "tables_extracted": len(extracted),
        "rows_extracted": sum(extracted.values()),
        "row_counts": extracted,
        "count_only": counted,
        "missing_tables": missing,
    }
    staging.write(f"extract_summary_{source}.json", summary)
    log.info("=== %s summary: %d of %d tables extracted, %d rows; %d counted only; %d missing ===",
             source.upper(), len(extracted), len(tables), summary["rows_extracted"],
             len(counted), len(missing))
    if missing:
        log.warning("Missing tables: %s", ", ".join(missing))
    return summary
