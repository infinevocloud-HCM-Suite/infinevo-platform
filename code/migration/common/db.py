"""The target Postgres connection and a checked, change-aware upsert."""
from __future__ import annotations

import psycopg2
import psycopg2.extras
from psycopg2 import sql

from .env import optional, require

AUDIT_USER = "migration"
_NEVER_COMPARED = {"updated_at", "updated_by", "created_at", "created_by", "version"}


def connect():
    """migration_user on the target, from TARGET_DB_* only."""
    conn = psycopg2.connect(
        host=require("TARGET_DB_HOST"),
        port=int(optional("TARGET_DB_PORT", "5432")),
        dbname=require("TARGET_DB_NAME"),
        user=require("TARGET_DB_USER"),
        password=require("TARGET_DB_PASSWORD"),
        sslmode=optional("TARGET_DB_SSLMODE", "prefer"),
    )
    conn.autocommit = False
    return conn


def on_table(template: str, qualified: str) -> sql.Composed:
    """template with its one {} filled by schema.table, quoted as identifiers - never
    pasted in as text."""
    return sql.SQL(template).format(sql.Identifier(*qualified.split(".")))


def set_tenant(cur, tenant_id: str) -> None:
    """RLS context for the rest of the transaction (W-68 Task 3 "Tenant")."""
    cur.execute("SELECT set_config('app.current_tenant_id', %s, true)", (str(tenant_id),))


class TableInfo:
    def __init__(self, columns: dict[str, dict], pk: list[str]):
        self.columns = columns  # name -> {nullable, has_default, generated}
        self.pk = pk

    def required(self) -> set[str]:
        return {c for c, m in self.columns.items() if not m["nullable"] and not m["has_default"] and not m["generated"]}


_info_cache: dict[str, TableInfo] = {}


def table_info(cur, qualified: str) -> TableInfo:
    if qualified in _info_cache:
        return _info_cache[qualified]
    schema, table = qualified.split(".", 1)
    cur.execute(
        """
        SELECT column_name, is_nullable = 'YES', column_default IS NOT NULL OR is_identity = 'YES',
               is_generated = 'ALWAYS'
        FROM information_schema.columns WHERE table_schema = %s AND table_name = %s
        """,
        (schema, table),
    )
    cols = {r[0]: {"nullable": r[1], "has_default": r[2], "generated": r[3]} for r in cur.fetchall()}
    if not cols:
        raise LookupError(f"Target table {qualified} does not exist")
    cur.execute(
        """
        SELECT a.attname FROM pg_index i
        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
        WHERE i.indrelid = %s::regclass AND i.indisprimary
        ORDER BY array_position(i.indkey, a.attnum)
        """,
        (qualified,),
    )
    pk = [r[0] for r in cur.fetchall()]
    info = TableInfo(cols, pk)
    _info_cache[qualified] = info
    return info


def upsert(cur, qualified: str, rows: list[dict], conflict: tuple[str, ...] | None = None,
           keep_on_update: tuple[str, ...] = ()) -> int:
    """INSERT ... ON CONFLICT DO UPDATE, changing a row only when a value really differs.

    Refuses, before touching the database, any column the target does not have and any
    NOT NULL column without a default that the rows leave out - a mapping error surfaces
    in the test, not as a half-loaded tenant. created_at / created_by are never
    overwritten; updated_at moves only when something changed, so a second run is a no-op.
    keep_on_update names further columns the first insert sets and later runs must not
    overwrite. Returns the number of rows sent.
    """
    if not rows:
        return 0
    info = table_info(cur, qualified)
    cols = list(rows[0].keys())
    for r in rows:
        if list(r.keys()) != cols:
            raise ValueError(f"{qualified}: rows have different column sets: {sorted(cols)} vs {sorted(r.keys())}")
    unknown = [c for c in cols if c not in info.columns]
    if unknown:
        raise LookupError(f"{qualified} has no column(s) {unknown}")
    generated = [c for c in cols if info.columns[c]["generated"]]
    if generated:
        raise ValueError(f"{qualified}: cannot write generated column(s) {generated}")
    missing = info.required() - set(cols)
    if missing:
        raise ValueError(f"{qualified}: NOT NULL column(s) without default not supplied: {sorted(missing)}")
    target = list(conflict or info.pk)
    if not target:
        raise ValueError(f"{qualified} has no primary key; pass conflict=")

    frozen = set(target) | {"created_at", "created_by"} | set(keep_on_update)
    updatable = [c for c in cols if c not in frozen]
    compared = [c for c in updatable if c not in _NEVER_COMPARED]
    col_sql = ", ".join(f'"{c}"' for c in cols)
    sql = f'INSERT INTO {qualified} AS t ({col_sql}) VALUES %s ON CONFLICT ({", ".join(target)}) '
    if updatable:
        sets = [f'"{c}" = EXCLUDED."{c}"' for c in updatable]
        if "updated_at" in info.columns and "updated_at" not in cols:
            sets.append("updated_at = now()")
        sql += "DO UPDATE SET " + ", ".join(sets)
        if compared:
            lhs = ", ".join(f't."{c}"' for c in compared)
            rhs = ", ".join(f'EXCLUDED."{c}"' for c in compared)
            sql += f" WHERE ({lhs}) IS DISTINCT FROM ({rhs})"
    else:
        sql += "DO NOTHING"
    values = [tuple(r[c] for c in cols) for r in rows]
    psycopg2.extras.execute_values(cur, sql, values, page_size=500)
    return len(rows)
