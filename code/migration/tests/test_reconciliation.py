"""
Unit tests for W-70 Financial Reconciliation and LOP Mismatch Generator
Verifies:
  - 0.0000 INR clean delta audit produces clean: True
  - 0.01 INR delta triggers clean: False / exit code 1
  - LOP generator filters only variance > 0 rows
  - LOP generator uses deterministic uuid5 and preserves status on conflict (idempotency)
  - Decimal precision is strictly preserved
"""

import os
import sys
import json
import uuid
import pytest
from decimal import Decimal
from unittest.mock import MagicMock, patch

# Ensure code/migration is in sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from reconciliation.financial_reconciliation import to_decimal, run_reconciliation
from reconciliation.lop_mismatch_generator import run_generator, to_decimal as lop_to_decimal

def test_to_decimal_precision():
    """Verify Decimal scale and rounding."""
    assert to_decimal("100.55554", 4) == Decimal("100.5555")
    assert to_decimal("100.55556", 4) == Decimal("100.5556")
    assert to_decimal("100.50", 2) == Decimal("100.50")
    assert to_decimal(None, 4) == Decimal("0.0000")
    assert to_decimal("", 2) == Decimal("0.00")

def test_clean_financial_reconciliation(tmp_path, monkeypatch):
    """Clean run with matching totals returns clean=True and 0.00 delta."""
    stg_dir = tmp_path / "staging"
    stg_dir.mkdir()
    
    mock_emp_payruns = [
        {"id": 1, "payrunId": 101, "employeeNumber": "EMP01", "totalEarnings": "50000.00", "totalDeductions": "5000.00", "netPay": "45000.00"},
        {"id": 2, "payrunId": 101, "employeeNumber": "EMP02", "totalEarnings": "60000.00", "totalDeductions": "6000.00", "netPay": "54000.00"},
    ]
    with open(stg_dir / "stg_payroll_employee_payruns.json", "w", encoding="utf-8") as f:
        json.dump(mock_emp_payruns, f)
        
    with open(stg_dir / "stg_merged_employees.json", "w", encoding="utf-8") as f:
        json.dump([], f)
        
    monkeypatch.setattr("os.path.dirname", lambda _: str(tmp_path))

    mock_conn = MagicMock()
    mock_cur = MagicMock()
    mock_conn.cursor.return_value = mock_cur

    last_sql = []
    def mock_execute(sql, params=None):
        last_sql.append(str(sql))

    def mock_fetchone():
        curr_sql = last_sql[-1] if last_sql else ""
        if "payroll.employee_payrun" in curr_sql and "SELECT" in curr_sql and "count(*)" in curr_sql:
            return (2, Decimal("110000.0000"), Decimal("11000.0000"), Decimal("99000.0000"))
        if "payroll.payrun" in curr_sql and "total_gross" in curr_sql:
            return (1, Decimal("110000.0000"), Decimal("11000.0000"), Decimal("99000.0000"), 2)
        if "core.lop_mismatch_log" in curr_sql:
            return (0,)
        if "COUNT(*)" in curr_sql or "count(*)" in curr_sql:
            return (0,)
        return (0,)

    mock_cur.execute.side_effect = mock_execute
    mock_cur.fetchone.side_effect = mock_fetchone
    mock_cur.fetchall.return_value = [
        ("45000.00", "50000.00", "5000.00", "EMP01", "2025-10"),
        ("54000.00", "60000.00", "6000.00", "EMP02", "2025-10")
    ]

    with patch("reconciliation.financial_reconciliation.get_pg_connection", return_value=mock_conn):
        clean = run_reconciliation(exit_on_finish=False)
        assert clean is True

def test_variance_financial_reconciliation_fails(tmp_path, monkeypatch):
    """A 0.01 INR variance causes clean=False."""
    stg_dir = tmp_path / "staging"
    stg_dir.mkdir()
    
    mock_emp_payruns = [
        {"id": 1, "payrunId": 101, "employeeNumber": "EMP01", "totalEarnings": "50000.00", "totalDeductions": "5000.00", "netPay": "45000.00"},
    ]
    with open(stg_dir / "stg_payroll_employee_payruns.json", "w", encoding="utf-8") as f:
        json.dump(mock_emp_payruns, f)
    with open(stg_dir / "stg_merged_employees.json", "w", encoding="utf-8") as f:
        json.dump([], f)

    monkeypatch.setattr("os.path.dirname", lambda _: str(tmp_path))

    mock_conn = MagicMock()
    mock_cur = MagicMock()
    mock_conn.cursor.return_value = mock_cur

    last_sql = []
    def mock_execute(sql, params=None):
        last_sql.append(str(sql))

    def mock_fetchone():
        curr_sql = last_sql[-1] if last_sql else ""
        if "payroll.employee_payrun" in curr_sql and "SELECT" in curr_sql and "count(*)" in curr_sql:
            # 0.01 INR mismatch
            return (1, Decimal("50000.0000"), Decimal("5000.0000"), Decimal("45000.0100"))
        if "payroll.payrun" in curr_sql and "total_gross" in curr_sql:
            return (1, Decimal("50000.0000"), Decimal("5000.0000"), Decimal("45000.0100"), 1)
        if "core.lop_mismatch_log" in curr_sql:
            return (0,)
        if "COUNT(*)" in curr_sql or "count(*)" in curr_sql:
            return (0,)
        return (0,)

    mock_cur.execute.side_effect = mock_execute
    mock_cur.fetchone.side_effect = mock_fetchone
    mock_cur.fetchall.return_value = [
        ("45000.01", "50000.00", "5000.00", "EMP01", "2025-10")
    ]

    with patch("reconciliation.financial_reconciliation.get_pg_connection", return_value=mock_conn):
        clean = run_reconciliation(exit_on_finish=False)
        assert clean is False

def test_lop_mismatch_generator_filtering_and_idempotency(tmp_path, monkeypatch):
    """LOP generator filters to variance > 0 only, uses deterministic uuid5, and preserves status."""
    stg_dir = tmp_path / "staging"
    stg_dir.mkdir()

    # 2 Mismatches and 1 Match
    # Row 1: EMP01 has 2.0 HRMS days vs 1.0 Payroll days -> Variance 1.0 (MISMATCH)
    # Row 2: EMP02 has 3.0 HRMS days vs 3.0 Payroll days -> Variance 0.0 (MATCH - must be filtered)
    # Row 3: EMP03 has 0.0 HRMS days vs 2.5 Payroll days -> Variance 2.5 (MISMATCH)
    emp_payruns = [
        {"payrunId": 1, "employeeNumber": "EMP01", "lwp_days": "1.0", "lwp_amount": "1000.00"},
        {"payrunId": 1, "employeeNumber": "EMP02", "lwp_days": "3.0", "lwp_amount": "3000.00"},
        {"payrunId": 1, "employeeNumber": "EMP03", "lwp_days": "2.5", "lwp_amount": "2500.00"},
    ]
    payruns = [
        {"id": 1, "payPeriodStartDate": "2025-10-01"}
    ]
    leave_requests = [
        {"employee_id": "EMP01", "from_date": "2025-10-05", "total_days": "2.0"},
        {"employee_id": "EMP02", "from_date": "2025-10-10", "total_days": "3.0"},
    ]
    merged_emp = [
        {"employee_number": "EMP01", "work_email": "emp01@test.com", "tenant_id": "a0000000-0000-0000-0000-000000000001"},
        {"employee_number": "EMP02", "work_email": "emp02@test.com", "tenant_id": "a0000000-0000-0000-0000-000000000001"},
        {"employee_number": "EMP03", "work_email": "emp03@test.com", "tenant_id": "a0000000-0000-0000-0000-000000000001"},
    ]

    with open(stg_dir / "stg_payroll_employee_payruns.json", "w", encoding="utf-8") as f:
        json.dump(emp_payruns, f)
    with open(stg_dir / "stg_payroll_payruns.json", "w", encoding="utf-8") as f:
        json.dump(payruns, f)
    with open(stg_dir / "stg_hrms_leave_requests.json", "w", encoding="utf-8") as f:
        json.dump(leave_requests, f)
    with open(stg_dir / "stg_merged_employees.json", "w", encoding="utf-8") as f:
        json.dump(merged_emp, f)

    monkeypatch.setattr("os.path.dirname", lambda _: str(tmp_path))

    mock_conn = MagicMock()
    mock_cur = MagicMock()
    mock_conn.cursor.return_value = mock_cur

    captured_batch = []
    def mock_execute_batch(cur, sql, rows, page_size=200):
        # Verify query does NOT update status on conflict
        assert "status = EXCLUDED.status" not in sql
        assert "ON CONFLICT (id) DO UPDATE" in sql
        captured_batch.extend(rows)

    with patch("reconciliation.lop_mismatch_generator.get_pg_connection", return_value=mock_conn), \
         patch("reconciliation.lop_mismatch_generator.execute_batch", side_effect=mock_execute_batch):
        run_generator()

    # Out of 3 entries, only 2 have variance > 0 (EMP01 and EMP03)
    assert len(captured_batch) == 2
    emp_numbers = [r[2] for r in captured_batch]
    assert "EMP01" in emp_numbers
    assert "EMP03" in emp_numbers
    assert "EMP02" not in emp_numbers  # Filtered out because variance is 0.0

    # Verify deterministic UUID5 IDs
    for row in captured_batch:
        r_id, tid, emp_no, email, period = row[0], row[1], row[2], row[3], row[4]
        expected_uuid = str(uuid.uuid5(uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8"), f"{tid}:{emp_no}:{period}"))
        assert r_id == expected_uuid
        # Verify all numbers are Decimal
        assert isinstance(row[5], Decimal) # hrms_unpaid_days
        assert isinstance(row[6], Decimal) # payroll_lop_days
        assert isinstance(row[7], Decimal) # variance_days
        assert isinstance(row[8], Decimal) # payroll_processed_deduction
