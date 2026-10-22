"""
A throwaway Postgres 16 built the way Azure's is (W-68 Task 4).

1. infra/postgres/01-roles.sql, 02-schemas.sql, 03-grants.sql as the superuser, the way
   provision.sh runs them (its psql :'x_pw' variables are filled with a test password).
2. Every Flyway script on this branch, in Flyway's order: one version sequence across the
   reference, core, hrms and payroll folders, each script in its own transaction, as
   migration_user. Placeholder replacement is off in the real Flyway config
   (code/backend/migration/src/main/resources/application.yml), so the files run verbatim.

The loaders then connect as migration_user, exactly as caj-data-migration does.
"""
from __future__ import annotations

import os
import re
from pathlib import Path

import psycopg2

REPO_ROOT = Path(__file__).resolve().parents[3]
PROVISION_DIR = REPO_ROOT / "infra" / "postgres"
FLYWAY_DIR = REPO_ROOT / "code" / "backend" / "migration" / "src" / "main" / "resources" / "db" / "migration"
FLYWAY_LOCATIONS = ("reference", "core", "hrms", "payroll")
TEST_PASSWORD = "test_pw_1"
_VERSION = re.compile(r"^V(\d+(?:[._]\d+)*)__.+\.sql$")


def flyway_scripts() -> list[Path]:
    """All versioned scripts, ordered as Flyway orders them across locations."""
    found = []
    for loc in FLYWAY_LOCATIONS:
        for path in (FLYWAY_DIR / loc).glob("V*.sql"):
            m = _VERSION.match(path.name)
            if not m:
                raise RuntimeError(f"Not a Flyway versioned script name: {path}")
            version = tuple(int(p) for p in re.split(r"[._]", m.group(1)))
            found.append((version, path))
    versions = [v for v, _ in found]
    dupes = {v for v in versions if versions.count(v) > 1}
    if dupes:
        raise RuntimeError(f"Duplicate Flyway versions: {sorted(dupes)}")
    return [p for _, p in sorted(found)]


def _provision_sql(name: str) -> str:
    sql = (PROVISION_DIR / name).read_text(encoding="utf-8")
    return re.sub(r":'([a-z_]+_pw)'", f"'{TEST_PASSWORD}'", sql)


def build_database(host: str, port: int, superuser: str, superpw: str, dbname: str) -> dict:
    """Provision and migrate; return connection settings for migration_user."""
    su = psycopg2.connect(host=host, port=port, user=superuser, password=superpw, dbname=dbname)
    su.autocommit = True
    with su.cursor() as cur:
        for name in ("01-roles.sql", "02-schemas.sql", "03-grants.sql"):
            cur.execute(_provision_sql(name))
    su.close()

    settings = dict(host=host, port=port, user="migration_user", password=TEST_PASSWORD, dbname=dbname)
    conn = psycopg2.connect(**settings)
    try:
        for path in flyway_scripts():
            with conn:  # one transaction per script, as Flyway does on Postgres
                with conn.cursor() as cur:
                    cur.execute("SET search_path TO migration")
                    try:
                        cur.execute(path.read_text(encoding="utf-8"))
                    except Exception as exc:
                        raise RuntimeError(f"Flyway script failed: {path.relative_to(REPO_ROOT)}: {exc}") from exc
    finally:
        conn.close()
    return settings


def target_env(settings: dict) -> dict:
    """The TARGET_DB_* variables the loaders read."""
    return {
        "TARGET_DB_HOST": settings["host"],
        "TARGET_DB_PORT": str(settings["port"]),
        "TARGET_DB_NAME": settings["dbname"],
        "TARGET_DB_USER": settings["user"],
        "TARGET_DB_PASSWORD": settings["password"],
    }


def dump_columns(settings: dict, out: Path) -> None:
    """Write schema.table.column type nullability for core, hrms, payroll, reference."""
    conn = psycopg2.connect(**settings)
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema IN ('core','hrms','payroll','reference')
            ORDER BY table_schema, table_name, ordinal_position
            """
        )
        lines = [
            f"{s}.{t}.{c}\t{d}\t{'NULL' if n == 'YES' else 'NOT NULL'}\t{(df or '')[:60]}"
            for s, t, c, d, n, df in cur.fetchall()
        ]
        cur.execute(
            """
            SELECT n.nspname || '.' || r.relname, c.contype, c.conname, pg_get_constraintdef(c.oid)
            FROM pg_constraint c JOIN pg_class r ON r.oid = c.conrelid JOIN pg_namespace n ON n.oid = r.relnamespace
            WHERE n.nspname IN ('core','hrms','payroll','reference') AND c.contype IN ('c','u','f','p','x')
            UNION ALL
            SELECT n.nspname || '.' || t.relname, 'i', i.relname, pg_get_indexdef(ix.indexrelid)
            FROM pg_index ix JOIN pg_class i ON i.oid = ix.indexrelid JOIN pg_class t ON t.oid = ix.indrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname IN ('core','hrms','payroll','reference') AND ix.indisunique AND NOT ix.indisprimary
            ORDER BY 1, 2, 3
            """
        )
        cons = [f"{t}\t{k}\t{n}\t{d}" for t, k, n, d in cur.fetchall()]
    conn.close()
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    out.with_name(out.stem + "_constraints.tsv").write_text("\n".join(cons) + "\n", encoding="utf-8")


if __name__ == "__main__":  # manual: python tests/pg_harness.py <out-file>
    import sys
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer("postgres:16-alpine", username="postgres", password="postgres", dbname="infinevo") as pg:
        s = build_database(pg.get_container_host_ip(), int(pg.get_exposed_port(5432)), "postgres", "postgres", "infinevo")
        dump_columns(s, Path(sys.argv[1] if len(sys.argv) > 1 else "target_columns.tsv"))
        print("ok")
