"""W-68 Task 1: the legacy extractors, against a real MySQL 8 (Testcontainers).

Two small legacy databases (hrmstestdb, payroll_test_db) are built with real table names
from the frozen entities; both extractors run with LEGACY_*_DB_* set, and the staging
files, the summaries and organisations.md are checked.
"""
from __future__ import annotations

import json
import logging
import os

import pytest

from extractors import extract_hrms, extract_payroll

SENTINEL_PAN = "ZZPAN9999Z"  # must never appear in a log line

HRMS_DDL = [
    """CREATE TABLE employee (id BIGINT PRIMARY KEY, personal_id BIGINT)""",
    """CREATE TABLE personal (
        id BIGINT PRIMARY KEY, emp_id VARCHAR(20), first_name VARCHAR(50),
        date_of_birth DATE, pan VARCHAR(20))""",
    """CREATE TABLE employee_document (
        id BIGINT PRIMARY KEY, document_type VARCHAR(30), file_name VARCHAR(100),
        file_type VARCHAR(50), document_content MEDIUMBLOB, employee_id BIGINT,
        company_index INT, file_url VARCHAR(255), public_id VARCHAR(100))""",
    """CREATE TABLE leave_documents (
        id BIGINT PRIMARY KEY, leave_request_id BIGINT, file_name VARCHAR(100),
        file_type VARCHAR(50), file_data MEDIUMBLOB, file_url VARCHAR(255),
        uploaded_at DATETIME(6))""",
    """CREATE TABLE leave_requests (
        id BIGINT PRIMARY KEY, employee_id BIGINT, from_date DATE, to_date DATE,
        total_days DOUBLE, is_half_day BIT(1), created_date DATETIME(6))""",
    """CREATE TABLE leave_request (id BIGINT PRIMARY KEY, reason VARCHAR(50))""",
]
HRMS_DATA = [
    "INSERT INTO employee VALUES (1, 1), (2, 2)",
    f"INSERT INTO personal VALUES (1, 'E001', 'Asha', '1990-05-17', '{SENTINEL_PAN}'),"
    " (2, 'E002', 'Ravi', NULL, NULL)",
    "INSERT INTO employee_document VALUES (1, 'PAN', 'pan.pdf', 'application/pdf',"
    " X'255044462D312E340A', 1, 0, 'https://files.example/pan.pdf', 'pid-1')",
    "INSERT INTO leave_documents VALUES (1, 1, 'mc.png', 'image/png', X'89504E47', "
    " 'https://files.example/mc.png', '2025-02-03 10:15:30.123456')",
    "INSERT INTO leave_requests VALUES (1, 1, '2025-02-03', '2025-02-04', 1.5, b'1',"
    " '2025-02-01 09:00:00')",
    "INSERT INTO leave_request VALUES (1, 'a'), (2, 'b'), (3, 'c')",
]

PAYROLL_DDL = [
    """CREATE TABLE organization (
        id BIGINT PRIMARY KEY, organizationId VARCHAR(40), organizationName VARCHAR(100),
        createdDate DATETIME(6), email VARCHAR(100), isOrgActive BIT(1), isDeleted BIT(1))""",
    """CREATE TABLE employee (
        id BIGINT PRIMARY KEY, employeeId VARCHAR(40), organizationId BIGINT,
        work_mail VARCHAR(100), date_of_joining DATE)""",
    """CREATE TABLE payruns (
        id BIGINT PRIMARY KEY, organizationId BIGINT, payDate DATE,
        totalNetPay DECIMAL(19,2))""",
    """CREATE TABLE paySchedule (
        id BIGINT PRIMARY KEY, organizationId BIGINT, payDay INT, payScheduleType VARCHAR(20))""",
    """CREATE TABLE payScheduleWorkingDays (payScheduleId BIGINT, workingDays VARCHAR(20))""",
    """CREATE TABLE employee_investment_proof (id BIGINT PRIMARY KEY)""",
]
PAYROLL_DATA = [
    "INSERT INTO organization VALUES"
    " (1, 'org-a', 'Acme | Pvt', '2024-01-02 03:04:05', 'a@acme.example', b'1', b'0'),"
    " (2, 'org-b', 'Beta Ltd', '2024-06-07 08:09:10', 'b@beta.example', b'0', b'1')",
    "INSERT INTO employee VALUES (10, 'u-10', 1, 'x@acme.example', '2024-02-01'),"
    " (11, 'u-11', 1, 'y@acme.example', NULL), (12, 'u-12', 2, 'z@beta.example', '2024-07-01')",
    "INSERT INTO payruns VALUES (100, 1, '2024-03-31', 123456.70), (101, 1, '2024-04-30', 0.05)",
    "INSERT INTO paySchedule VALUES (1, 1, 31, 'MONTHLY')",
    "INSERT INTO payScheduleWorkingDays VALUES (1, 'MONDAY'), (1, 'TUESDAY')",
    "INSERT INTO employee_investment_proof VALUES (1), (2)",
]


def _docker_ok() -> bool:
    try:
        import docker

        docker.from_env().ping()
        return True
    except Exception:  # noqa: BLE001
        return False


@pytest.fixture(scope="module")
def mysql_server():
    if not _docker_ok():
        if os.environ.get("MIGRATION_TESTS_REQUIRE_DOCKER") == "1":
            pytest.fail("Docker is required for the extractor tests and is not reachable")
        pytest.skip("Docker not reachable; extractor tests skipped")
    import pymysql
    from testcontainers.mysql import MySqlContainer

    container = MySqlContainer("mysql:8.0", username="tc", password="tc",
                               root_password="rootpw", dbname="hrmstestdb")
    container.start()
    try:
        host = container.get_container_host_ip()
        if host not in ("localhost", "127.0.0.1"):
            host = "127.0.0.1"
        port = int(container.get_exposed_port(3306))
        conn = pymysql.connect(host=host, port=port, user="root", password="rootpw",
                               autocommit=True)
        with conn.cursor() as cur:
            cur.execute("CREATE DATABASE IF NOT EXISTS payroll_test_db")
            for db, ddl, data in (("hrmstestdb", HRMS_DDL, HRMS_DATA),
                                  ("payroll_test_db", PAYROLL_DDL, PAYROLL_DATA)):
                cur.execute(f"USE {db}")
                for stmt in ddl + data:
                    cur.execute(stmt)
        conn.close()
        yield {"host": host, "port": str(port), "user": "root", "password": "rootpw"}
    finally:
        container.stop()


def _set_env(monkeypatch, prefix, server, db):
    monkeypatch.setenv(f"LEGACY_{prefix}_DB_HOST", server["host"])
    monkeypatch.setenv(f"LEGACY_{prefix}_DB_PORT", server["port"])
    monkeypatch.setenv(f"LEGACY_{prefix}_DB_NAME", db)
    monkeypatch.setenv(f"LEGACY_{prefix}_DB_USER", server["user"])
    monkeypatch.setenv(f"LEGACY_{prefix}_DB_PASSWORD", server["password"])


def _read(staging_dir, name):
    return json.loads((staging_dir / name).read_text(encoding="utf-8"))


# --- no Docker needed ---------------------------------------------------------------

def test_table_lists_are_disjoint_and_complete():
    for mod in (extract_hrms, extract_payroll):
        tables = mod.TABLES_TO_EXTRACT
        assert len(tables) == len(set(tables)), f"{mod.__name__} lists a table twice"
        assert not set(tables) & set(mod.COUNT_ONLY)
    assert set(extract_hrms.COUNT_ONLY) == {"leave_request", "employee_leave_balances", "timesheet"}
    assert extract_payroll.COUNT_ONLY == ("employee_investment_proof",)
    assert extract_hrms.EXCLUDED_COLUMNS == {"employee_document": ("document_content",),
                                             "leave_documents": ("file_data",),
                                             "ourusers": ("password", "blacklisted_token")}
    # No password or legacy token ever reaches staging (mapping §3.2).
    assert extract_payroll.EXCLUDED_COLUMNS == {"companyUser": ("password",),
                                                "userInvitations": ("acceptance_token",),
                                                "employeeInvitation": ("acceptance_token",)}
    assert "role" in extract_hrms.TABLES_TO_EXTRACT
    assert "organizationRole" in extract_payroll.TABLES_TO_EXTRACT
    # Payroll keeps entity names verbatim (PhysicalNamingStrategyStandardImpl).
    for name in ("paySchedule", "payScheduleWorkingDays", "workLocations", "FBP",
                 "IncomeTaxDeclaration", "ProofOfInvestment", "companyUser"):
        assert name in extract_payroll.TABLES_TO_EXTRACT


@pytest.mark.parametrize("missing", ["LEGACY_HRMS_DB_HOST", "LEGACY_HRMS_DB_NAME",
                                     "LEGACY_HRMS_DB_USER", "LEGACY_HRMS_DB_PASSWORD"])
def test_missing_variable_exits_2(monkeypatch, staging_dir, capsys, missing):
    for v in ("HOST", "NAME", "USER", "PASSWORD"):
        monkeypatch.setenv(f"LEGACY_HRMS_DB_{v}", "x")
    monkeypatch.delenv(missing)
    with pytest.raises(SystemExit) as exc:
        extract_hrms.main()
    assert exc.value.code == 2
    assert missing in capsys.readouterr().err
    assert list(staging_dir.iterdir()) == []


def test_missing_payroll_variable_exits_2(monkeypatch, staging_dir, capsys):
    for v in ("HOST", "PORT", "NAME", "USER", "PASSWORD"):
        monkeypatch.delenv(f"LEGACY_PAYROLL_DB_{v}", raising=False)
    with pytest.raises(SystemExit) as exc:
        extract_payroll.main()
    assert exc.value.code == 2
    assert "LEGACY_PAYROLL_DB_HOST" in capsys.readouterr().err


# --- MySQL 8 --------------------------------------------------------------------------

def test_hrms_extract(mysql_server, monkeypatch, staging_dir, caplog):
    _set_env(monkeypatch, "HRMS", mysql_server, "hrmstestdb")
    caplog.set_level(logging.INFO)
    assert extract_hrms.main() == 0

    docs = _read(staging_dir, "stg_hrms_employee_document.json")
    assert len(docs) == 1
    assert list(docs[0]) == ["id", "document_type", "file_name", "file_type", "employee_id",
                             "company_index", "file_url", "public_id"]
    leave_docs = _read(staging_dir, "stg_hrms_leave_documents.json")
    assert "file_data" not in leave_docs[0]
    assert leave_docs[0]["uploaded_at"] == "2025-02-03T10:15:30.123456"

    personal = _read(staging_dir, "stg_hrms_personal.json")
    assert list(personal[0]) == ["id", "emp_id", "first_name", "date_of_birth", "pan"]
    assert personal[0]["date_of_birth"] == "1990-05-17"
    lr = _read(staging_dir, "stg_hrms_leave_requests.json")[0]
    assert lr["total_days"] == "1.5" and lr["is_half_day"] is True  # DOUBLE as text, BIT as bool

    assert not (staging_dir / "stg_hrms_leave_request.json").exists()
    assert not (staging_dir / "stg_hrms_ourusers.json").exists()  # absent table: no file
    summary = _read(staging_dir, "extract_summary_hrms.json")
    assert summary["count_only"] == {"leave_request": 3}
    assert summary["row_counts"]["employee_document"] == 1
    assert summary["row_counts"]["employee"] == 2
    assert summary["tables_extracted"] == 5
    assert "ourusers" in summary["missing_tables"]
    assert "employee_leave_balances" in summary["missing_tables"]
    assert SENTINEL_PAN not in caplog.text
    assert mysql_server["password"] not in caplog.text


def test_payroll_extract_and_organisations(mysql_server, monkeypatch, staging_dir, caplog):
    _set_env(monkeypatch, "PAYROLL", mysql_server, "payroll_test_db")
    caplog.set_level(logging.INFO)
    assert extract_payroll.main() == 0

    orgs = _read(staging_dir, "stg_payroll_organization.json")
    assert list(orgs[0]) == ["id", "organizationId", "organizationName", "createdDate",
                             "email", "isOrgActive", "isDeleted"]
    assert orgs[0]["isOrgActive"] is True and orgs[0]["isDeleted"] is False
    runs = _read(staging_dir, "stg_payroll_payruns.json")
    assert [r["totalNetPay"] for r in runs] == ["123456.70", "0.05"]  # Decimal as text
    assert _read(staging_dir, "stg_payroll_paySchedule.json")[0]["payDay"] == 31
    wd = _read(staging_dir, "stg_payroll_payScheduleWorkingDays.json")
    assert [list(r) for r in wd] == [["payScheduleId", "workingDays"]] * 2

    assert not (staging_dir / "stg_payroll_employee_investment_proof.json").exists()
    summary = _read(staging_dir, "extract_summary_payroll.json")
    assert summary["count_only"] == {"employee_investment_proof": 2}
    assert summary["row_counts"]["payScheduleWorkingDays"] == 2
    assert summary["tables_extracted"] == 5

    md = (staging_dir / "organisations.md").read_text(encoding="utf-8")
    rows = [line for line in md.splitlines() if line.startswith("| ") and "---" not in line][1:]
    assert len(rows) == 2
    assert rows[0] == ("| Acme \\| Pvt | 1 | 2024-01-02T03:04:05 | a@acme.example "
                       "| yes | no | 2 | 2 |")
    assert rows[1] == ("| Beta Ltd | 2 | 2024-06-07T08:09:10 | b@beta.example "
                       "| no | yes | 1 | 0 |")
    assert "x@acme.example" not in caplog.text
