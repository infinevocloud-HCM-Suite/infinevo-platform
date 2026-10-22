"""
Unit tests for Karma's Loaders (Ticket #90 / Task 4 / W-68.3):
  - load_salary.py (Task 3e - earnings, deductions, benefits, reimbursements, FBP, active CTC with sum check)
  - load_statutory.py (Task 3f - EPF, ESI, PT overrides only when differing from reference.pt_slab)
  - load_tax.py (Task 3h - current FY filter, declarations, 9 investment tables, TDS, proof items)
"""

import os
import sys
import io
import json
import uuid
from decimal import Decimal
import pytest
from unittest.mock import MagicMock, patch

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from loaders.load_salary import run_loader as run_salary_loader
from loaders.load_statutory import run_loader as run_statutory_loader
from loaders.load_tax import run_loader as run_tax_loader

FIXTURES = {
    "org_mapping.json": {
        "1": {"tenant_id": "a0000000-0000-0000-0000-000000000001", "slug": "acme"}
    },
    "stg_merged_employees.json": [
        {
            "id": "e0000000-0000-0000-0000-000000000001",
            "tenant_id": "a0000000-0000-0000-0000-000000000001",
            "source_legacy_payroll_id": "101",
            "employee_number": "EMP001"
        }
    ],
    # ── Salary Fixtures ────────────────────────────────────────────────────────
    "stg_payroll_earnings.json": [
        {
            "id": 1,
            "earningId": "BASIC",
            "organizationId": 1,
            "earningName": "Basic Salary",
            "earningType": "BASIC",
            "amount": 50000,
            "isIncludedInCtc": True,
            "status": "active"
        },
        {
            "id": 2,
            "earningId": "HRA",
            "organizationId": 1,
            "earningName": "House Rent Allowance",
            "earningType": "HRA",
            "amount": 25000,
            "isIncludedInCtc": True,
            "status": "active"
        }
    ],
    "stg_payroll_deductions.json": [
        {
            "id": 1,
            "deductionId": "PF_DED",
            "organizationId": 1,
            "deductionName": "Provident Fund Deduction",
            "deductionType": "PF",
            "status": "active"
        }
    ],
    "stg_payroll_benefits.json": [
        {
            "id": 1,
            "benefitId": "MED_INS",
            "organizationId": 1,
            "benefitName": "Medical Insurance",
            "status": "active"
        }
    ],
    "stg_payroll_reimbursements.json": [
        {
            "id": 1,
            "reimbursementId": "FUEL",
            "organizationId": 1,
            "reimbursementName": "Fuel Reimbursement",
            "reimbursementType": "FUEL",
            "status": "active"
        }
    ],
    "stg_payroll_FBP.json": [
        {
            "id": 1,
            "organizationId": 1,
            "isFbpEnabled": True,
            "isFbpDeclarationLocked": False
        }
    ],
    "stg_payroll_ctc_structure.json": [
        {
            "id": 1001,
            "organizationId": 1,
            "employee_id": 101,
            "annualCtc": 900000.0,
            "monthlySalary": 75000.0,
            "effectiveDate": "2025-04-01",
            "is_active": True,
            "is_deleted": False
        },
        {
            "id": 1002,
            "organizationId": 1,
            "employee_id": 101,
            "annualCtc": 600000.0,
            "monthlySalary": 50000.0,
            "effectiveDate": "2024-04-01",
            "is_active": False,
            "is_deleted": False
        },
        {
            "id": 1003,
            "organizationId": 1,
            "employee_id": 101,
            "annualCtc": 1200000.0,
            "monthlySalary": 100000.0,
            "effectiveDate": "2026-04-01",
            "is_active": True,
            "is_deleted": False
        }
    ],
    "stg_payroll_employee_earnings.json": [
        # CTC 1001 lines: 50000 + 25000 = 75000 (Matches monthlySalary)
        {
            "id": 201,
            "ctc_structure_id": 1001,
            "earning_id": 1,
            "amount": 50000.0,
            "enabled": True
        },
        {
            "id": 202,
            "ctc_structure_id": 1001,
            "earning_id": 2,
            "amount": 25000.0,
            "enabled": True
        },
        # CTC 1003 lines: 40000 + 20000 = 60000 != 100000 (Mismatch for CTC 1003)
        {
            "id": 203,
            "ctc_structure_id": 1003,
            "earning_id": 1,
            "amount": 40000.0,
            "enabled": True
        },
        {
            "id": 204,
            "ctc_structure_id": 1003,
            "earning_id": 2,
            "amount": 20000.0,
            "enabled": True
        }
    ],
    "stg_payroll_employee_benefits.json": [
        {
            "id": 301,
            "ctc_structure_id": 1001,
            "benefit_id": 1,
            "amount": 2000.0,
            "enabled": True
        }
    ],
    "stg_payroll_employee_reimbursements.json": [],
    "stg_payroll_employee_fbp_components.json": [],
    "stg_payroll_ctc_epf_components.json": [
        {
            "id": 401,
            "ctc_structure_id": 1001,
            "component_code": "EPF_EMPLOYEE",
            "wage_base": 15000.0,
            "rate": 12.0,
            "monthly_amount": 1800.0
        }
    ],
    "stg_payroll_ctc_esi_components.json": [],

    # ── Statutory Fixtures ─────────────────────────────────────────────────────
    "stg_payroll_epf.json": [
        {
            "id": 1,
            "organizationId": 1,
            "isEpfEnabled": True,
            "epfNumber": "EPF12345",
            "employeeContributionRate": 12.0,
            "employerContributionRate": 12.0,
            "wageCeiling": 15000.0
        }
    ],
    "stg_payroll_esi.json": [
        {
            "id": 1,
            "organizationId": 1,
            "isEsiEnabled": True,
            "esiNumber": "ESI98765",
            "employeeContributionRate": 0.75,
            "employerContributionRate": 3.25,
            "wageCeiling": 21000.0
        }
    ],
    "stg_payroll_professionalTax.json": [
        {
            "id": 1,
            "organizationId": 1,
            "state": "KA",
            "registrationNumber": "PT-KA-001"
        },
        {
            "id": 2,
            "organizationId": 1,
            "state": "MH",
            "registrationNumber": "PT-MH-002"
        }
    ],
    "stg_payroll_slabDetail.json": [
        # KA Custom Slabs (differ from standard)
        {
            "id": 1,
            "slabRateConfigurationId": 1,
            "startRange": 0,
            "endRange": 10000,
            "monthlyTax": 0,
            "sortOrder": 1
        },
        {
            "id": 2,
            "slabRateConfigurationId": 1,
            "startRange": 10000,
            "endRange": 25000,
            "monthlyTax": 300,  # Custom: standard is 200
            "sortOrder": 2
        },
        # MH Standard Slabs (identical to reference)
        {
            "id": 3,
            "slabRateConfigurationId": 2,
            "startRange": 0,
            "endRange": 7500,
            "monthlyTax": 0,
            "sortOrder": 1
        },
        {
            "id": 4,
            "slabRateConfigurationId": 2,
            "startRange": 7500,
            "endRange": 10000,
            "monthlyTax": 175,
            "sortOrder": 2
        }
    ],

    # ── Tax Fixtures ───────────────────────────────────────────────────────────
    "stg_payroll_IncomeTaxDeclaration.json": [
        {
            "id": 1,
            "organizationId": 1,
            "financialYear": "2026-2027",
            "defaultTaxRegime": "NEW",
            "isLocked": False
        },
        {
            "id": 2,
            "organizationId": 1,
            "financialYear": "2024-2025",
            "defaultTaxRegime": "OLD",
            "isLocked": True
        }
    ],
    "stg_payroll_employee_investment_declaration.json": [
        {
            "id": 501,
            "organizationId": 1,
            "employee_id": 101,
            "financialYear": "2026-2027",
            "taxRegime": "NEW",
            "status": "SUBMITTED"
        },
        {
            "id": 502,
            "organizationId": 1,
            "employee_id": 101,
            "financialYear": "2024-2025",
            "taxRegime": "OLD",
            "status": "DRAFT"
        }
    ],
    "stg_payroll_employee_inv_house_rent.json": [
        {
            "id": 601,
            "declaration_id": 501,
            "address": "123 Tech Park, Bangalore",
            "landlordName": "John Doe",
            "amountPerMonth": 20000.0
        }
    ],
    "stg_payroll_employee_inv_home_loan.json": [],
    "stg_payroll_employee_inv_let_out_property.json": [],
    "stg_payroll_employee_inv_let_out_property_detail.json": [],
    "stg_payroll_employee_inv_section6a.json": [
        {
            "id": 701,
            "declaration_id": 501,
            "sectionCode": "80C",
            "amount": 150000.0,
            "description": "PPF Deposit"
        }
    ],
    "stg_payroll_employee_inv_pre_tax_deduction.json": [],
    "stg_payroll_employee_inv_prev_employment.json": [],
    "stg_payroll_employee_inv_other_income.json": [],
    "stg_payroll_employee_inv_tax_summary.json": [
        {
            "id": 801,
            "declaration_id": 501,
            "regime": "NEW",
            "netTaxableIncome": 800000.0,
            "taxToBePaid": 25000.0
        }
    ],
    "stg_payroll_employee_tds.json": [
        {
            "id": 901,
            "employee_id": 101,
            "financialYear": "2026-2027",
            "regime": "NEW",
            "annualGross": 900000.0,
            "annualTaxableIncome": 800000.0,
            "annualTax": 25000.0
        }
    ],
    "stg_payroll_ProofOfInvestment.json": [
        {
            "id": 1001,
            "employee_id": 101,
            "declaration_id": 501,
            "status": "APPROVED",
            "reviewerNote": "Verified receipts"
        }
    ],
    "stg_payroll_employee_proof_of_investment.json": [],
    "stg_payroll_employee_poi_item.json": [
        {
            "id": 1101,
            "proof_id": 1001,
            "sourceKind": "SECTION_6A",
            "declaredAmount": 150000.0,
            "claimedAmount": 150000.0,
            "approvedAmount": 150000.0,
            "status": "APPROVED"
        }
    ]
}


def mock_open_staging(fpath, *args, **kwargs):
    basename = os.path.basename(fpath)
    if basename in FIXTURES:
        return io.StringIO(json.dumps(FIXTURES[basename]))
    return io.StringIO("[]")


def mock_exists_staging(fpath):
    basename = os.path.basename(fpath)
    return basename in FIXTURES


@pytest.fixture
def mock_db():
    conn = MagicMock()
    cur = MagicMock()
    conn.cursor.return_value = cur
    # Ensure check for existing DONE returns None
    cur.fetchone.return_value = None
    return conn, cur


# ── Test Suite: Task 3e load_salary ──────────────────────────────────────────

def test_load_salary_active_ctc_and_components(mock_db):
    """
    Verifies that:
      - Master components (earning, deduction, benefit, reimbursement, fbp) are loaded.
      - Active CTC with matching sum (1001: 50k+25k=75k) is loaded into payroll.ctc_structure.
      - Inactive CTC (1002) is skipped.
      - Child lines are loaded into payroll.employee_earning and payroll.employee_benefit.
    """
    conn, cur = mock_db
    captured_inserts = []

    def mock_execute_batch(cursor, sql, batch, page_size=100):
        captured_inserts.append((sql, batch))

    with patch("loaders.load_salary.get_pg_connection", return_value=conn), \
         patch("loaders.load_salary.execute_batch", side_effect=mock_execute_batch), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging):
        res = run_salary_loader()

    assert res["earnings_loaded"] == 2
    assert res["deductions_loaded"] == 1
    assert res["benefits_loaded"] == 1
    assert res["reimbursements_loaded"] == 1
    assert res["ctc_structures_loaded"] == 1  # Only 1001 (1002 inactive, 1003 mismatched)

    # Verify ctc_structure batch
    ctc_batches = [b for sql, b in captured_inserts if "payroll.ctc_structure (" in sql]
    assert len(ctc_batches) == 1
    assert len(ctc_batches[0]) == 1
    # Monthly CTC check
    assert ctc_batches[0][0][5] == Decimal("75000.0000")

    # Verify employee_earning batch
    ee_batches = [b for sql, b in captured_inserts if "payroll.employee_earning" in sql]
    assert len(ee_batches) == 1
    assert len(ee_batches[0]) == 2
    sum_monthly = sum(row[7] for row in ee_batches[0])
    assert sum_monthly == Decimal("75000.0000")


def test_load_salary_quarantines_mismatch(mock_db):
    """
    Verifies that CTC structure 1003 (monthlySalary=100k, earning lines sum=60k)
    is quarantined with reason 'CTC_SUM_MISMATCH'.
    """
    conn, cur = mock_db
    saved_quarantine = []

    def mock_dump(data, f, **kwargs):
        saved_quarantine.extend(data)

    with patch("loaders.load_salary.get_pg_connection", return_value=conn), \
         patch("loaders.load_salary.execute_batch"), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging), \
         patch("json.dump", side_effect=mock_dump):
        res = run_salary_loader()

    mismatches = [q for q in saved_quarantine if q.get("reason") == "CTC_SUM_MISMATCH"]
    assert len(mismatches) == 1
    assert mismatches[0]["legacy_id"] == "1003"
    assert mismatches[0]["monthly_salary"] == "100000.0000"
    assert mismatches[0]["monthly_earning_sum"] == "60000.0000"


# ── Test Suite: Task 3f load_statutory ───────────────────────────────────────

def test_load_statutory_epf_esi_and_pt_override(mock_db):
    """
    Verifies that:
      - EPF setting and ESI setting are loaded per tenant.
      - Professional Tax: KA slabs differ from standard reference -> override written.
      - MH slabs match standard reference -> override skipped per W-68 rule.
    """
    conn, cur = mock_db
    captured_inserts = []

    def mock_execute_batch(cursor, sql, batch, page_size=100):
        captured_inserts.append((sql, batch))

    # Mock standard reference.pt_slab
    # KA reference: 0-15000: 0, 15000+: 200
    # MH reference: 0-7500: 0, 7500-10000: 175
    ref_rows = [
        ("KA", Decimal("0"), Decimal("15000"), Decimal("0"), 1),
        ("KA", Decimal("15000"), None, Decimal("200"), 2),
        ("MH", Decimal("0"), Decimal("7500"), Decimal("0"), 1),
        ("MH", Decimal("7500"), Decimal("10000"), Decimal("175"), 2),
    ]
    cur.fetchall.return_value = ref_rows

    with patch("loaders.load_statutory.get_pg_connection", return_value=conn), \
         patch("loaders.load_statutory.execute_batch", side_effect=mock_execute_batch), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging):
        res = run_statutory_loader()

    assert res["epf_settings_loaded"] == 1
    assert res["esi_settings_loaded"] == 1
    assert res["pt_overrides_loaded"] == 1  # Only KA, MH was skipped because identical
    assert res["skipped_identical_count"] == 1

    # Verify EPF rates in batch
    epf_batches = [b for sql, b in captured_inserts if "payroll.epf_setting" in sql]
    assert len(epf_batches) == 1
    assert epf_batches[0][0][6] == Decimal("12.0000")  # employee_rate
    assert epf_batches[0][0][11] == Decimal("15000.0000")  # wage_ceiling

    # Verify PT override batch
    pt_batches = [b for sql, b in captured_inserts if "payroll.org_pt_override (" in sql]
    assert len(pt_batches) == 1
    assert pt_batches[0][0][2] == "KA"


# ── Test Suite: Task 3h load_tax ─────────────────────────────────────────────

def test_load_tax_current_fy_and_components(mock_db):
    """
    Verifies that:
      - Only current FY (2026-2027) declarations and windows are loaded.
      - Past FY (2024-2025) declarations are filtered out.
      - Child investment tables (house rent, section 6A, tax summary), TDS, and POI proofs are loaded.
    """
    conn, cur = mock_db
    captured_inserts = []

    def mock_execute_batch(cursor, sql, batch, page_size=100):
        captured_inserts.append((sql, batch))

    # Mock reference.section6a_item_master
    sec6a_rows = [
        (uuid.uuid4(), "80C"),
        (uuid.uuid4(), "80D"),
    ]
    cur.fetchall.return_value = sec6a_rows

    with patch("loaders.load_tax.get_pg_connection", return_value=conn), \
         patch("loaders.load_tax.execute_batch", side_effect=mock_execute_batch), \
         patch("builtins.open", side_effect=mock_open_staging), \
         patch("os.path.exists", side_effect=mock_exists_staging), \
         patch.dict(os.environ, {"MIGRATION_FY": "2026-2027"}):
        res = run_tax_loader()

    assert res["windows_loaded"] >= 1
    assert res["declarations_loaded"] == 1  # 501 loaded, 502 (2024-2025) filtered out
    assert res["house_rent_loaded"] == 1
    assert res["tds_loaded"] == 1
    assert res["proofs_loaded"] == 1
    assert res["proof_items_loaded"] == 1

    # Verify declaration FY
    decl_batches = [b for sql, b in captured_inserts if "payroll.employee_investment_declaration" in sql]
    assert len(decl_batches) == 1
    assert decl_batches[0][0][3] == "2026-2027"

    # Verify House Rent amount
    hr_batches = [b for sql, b in captured_inserts if "payroll.employee_inv_house_rent" in sql]
    assert len(hr_batches) == 1
    assert hr_batches[0][0][9] == Decimal("20000.0000")

    # Verify TDS record
    tds_batches = [b for sql, b in captured_inserts if "payroll.employee_tds" in sql]
    assert len(tds_batches) == 1
    assert tds_batches[0][0][7] == Decimal("900000.0000")
    assert tds_batches[0][0][9] == Decimal("25000.0000")


# ── Strict Verification: Zero Floats Rule ────────────────────────────────────

def test_zero_floats_rule():
    """
    Ensures no float(...) calls exist in the migration loaders.
    """
    loader_files = [
        os.path.join(BASE_DIR, "loaders", "load_salary.py"),
        os.path.join(BASE_DIR, "loaders", "load_statutory.py"),
        os.path.join(BASE_DIR, "loaders", "load_tax.py"),
    ]
    for lf in loader_files:
        with open(lf, "r", encoding="utf-8") as f:
            content = f.read()
            assert "float(" not in content, f"Forbidden float(...) found in {lf}"
