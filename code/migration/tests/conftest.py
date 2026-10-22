"""pytest plumbing for the migration engine (W-68 Task 4).

Database tests use a real Postgres 16 built by tests/pg_harness.py: provisioned like
Azure and migrated with every Flyway script on this branch. It is built once per session
as a template; each test gets its own database cloned from it, its own staging directory
and its own org map, so tests never see each other's rows.

Tests that need Postgres take the `target_db` fixture. Without Docker they are skipped,
unless MIGRATION_TESTS_REQUIRE_DOCKER=1 (CI sets it), when they fail instead.
"""
from __future__ import annotations

import json
import os
import sys
import uuid
from pathlib import Path

import pytest

MIGRATION_DIR = Path(__file__).resolve().parents[1]
if str(MIGRATION_DIR) not in sys.path:
    sys.path.insert(0, str(MIGRATION_DIR))

from tests import pg_harness  # noqa: E402

TEMPLATE_DB = "infinevo_tpl"


def _docker_ok() -> bool:
    try:
        import docker  # noqa: F401  (testcontainers dependency)
        import docker as d
        d.from_env().ping()
        return True
    except Exception:  # noqa: BLE001
        return False


@pytest.fixture(scope="session")
def pg_server():
    if not _docker_ok():
        if os.environ.get("MIGRATION_TESTS_REQUIRE_DOCKER") == "1":
            pytest.fail("Docker is required for the migration database tests and is not reachable")
        pytest.skip("Docker not reachable; database tests skipped")
    from testcontainers.postgres import PostgresContainer

    container = PostgresContainer("postgres:16-alpine", username="postgres", password="postgres", dbname="postgres")
    container.start()
    try:
        host, port = container.get_container_host_ip(), int(container.get_exposed_port(5432))
        import psycopg2

        su = psycopg2.connect(host=host, port=port, user="postgres", password="postgres", dbname="postgres")
        su.autocommit = True
        with su.cursor() as cur:
            cur.execute(f"CREATE DATABASE {TEMPLATE_DB}")
        su.close()
        pg_harness.build_database(host, port, "postgres", "postgres", TEMPLATE_DB)
        yield {"host": host, "port": port}
    finally:
        container.stop()


@pytest.fixture
def target_db(pg_server, monkeypatch):
    """A fresh migrated database for one test; TARGET_DB_* point at it as migration_user."""
    import psycopg2

    name = f"t_{uuid.uuid4().hex[:12]}"
    su = psycopg2.connect(host=pg_server["host"], port=pg_server["port"], user="postgres",
                          password="postgres", dbname="postgres")
    su.autocommit = True
    with su.cursor() as cur:
        cur.execute(f"CREATE DATABASE {name} TEMPLATE {TEMPLATE_DB}")
    settings = dict(host=pg_server["host"], port=pg_server["port"], user="migration_user",
                    password=pg_harness.TEST_PASSWORD, dbname=name)
    for k, v in pg_harness.target_env(settings).items():
        monkeypatch.setenv(k, v)
    from common import db as common_db

    common_db._info_cache.clear()
    yield settings
    with su.cursor() as cur:
        cur.execute(f"DROP DATABASE IF EXISTS {name} WITH (FORCE)")
    su.close()


@pytest.fixture
def staging_dir(tmp_path, monkeypatch):
    d = tmp_path / "staging"
    d.mkdir()
    monkeypatch.setenv("MIGRATION_STAGING_DIR", str(d))
    return d


@pytest.fixture
def org_map(tmp_path, monkeypatch):
    """Write the fixture org map and point the loaders at it."""
    from tests.fixtures import ORG_MAPPING

    p = tmp_path / "org_mapping.json"
    p.write_text(json.dumps(ORG_MAPPING), encoding="utf-8")
    monkeypatch.setenv("MIGRATION_ORG_MAPPING", str(p))
    return ORG_MAPPING


@pytest.fixture
def legacy_fixture(staging_dir, org_map):
    """Write every fixture staging file (all areas) into the test's staging directory."""
    from tests.fixtures import write_all

    write_all(staging_dir)
    return staging_dir


@pytest.fixture
def query(target_db):
    """query(sql, params) -> list of rows, as migration_user on the test database."""
    import psycopg2

    conn = psycopg2.connect(**target_db)
    conn.autocommit = True

    def _q(sql, params=None):
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchall() if cur.description else []

    yield _q
    conn.close()
