"""
Unit Tests for Claims and Deductions Loader (W-68 Task 3l)
Verifies:
  1. Open reimbursement requests transform to payroll.employee_reimbursement_request with SUBMITTED status and Decimal precision
  2. Historical PAID reimbursements are dropped per Decision X-14
  3. Claims with unknown employee are quarantined with reason
  4. Open salary deductions transform to payroll.employee_deduction and core.pay_input with normalized deduction types
  5. Historical PROCESSED deductions are dropped per Decision X-14
  6. Zero or negative amounts are rejected and quarantined
  7. Deductions type normalization covers all valid enum values
  8. Full run_loader executes cleanly in dry-run mode
"""

import os
import sys
import unittest
from decimal import Decimal

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from loaders.load_claims import (
    to_decimal,
    det_uuid,
    normalize_deduction_type,
    format_period,
    transform_reimbursement_item,
    transform_deduction_item,
    run_loader,
    VALID_DEDUCTION_TYPES
)


class TestLoadClaims(unittest.TestCase):
    def setUp(self):
        self.tenant_id = "11111111-1111-1111-1111-111111111111"
        self.employee_id = "22222222-2222-2222-2222-222222222222"
        self.reimbursement_comp_id = "44444444-4444-4444-4444-444444444444"

        self.mock_emp = {
            "id": self.employee_id,
            "tenant_id": self.tenant_id,
            "source_legacy_payroll_id": "1001"
        }
        self.emp_by_payroll_id = {"1001": self.mock_emp}
        self.emp_by_uuid = {"emp-uuid-1": self.mock_emp}
        self.reimb_map = {(self.tenant_id, "TRAVEL"): self.reimbursement_comp_id}

    def test_to_decimal_precision(self):
        """Verifies Decimal conversion avoids floats and enforces 4 decimal places."""
        d = to_decimal("1500.5")
        self.assertIsInstance(d, Decimal)
        self.assertEqual(d, Decimal("1500.5000"))

        d2 = to_decimal(2000)
        self.assertEqual(d2, Decimal("2000.0000"))

    def test_deduction_type_normalization(self):
        """Verifies mapping of various legacy strings to valid closed enum types."""
        self.assertEqual(normalize_deduction_type("Advance Payment"), "ADVANCE_RECOVERY")
        self.assertEqual(normalize_deduction_type("Company Loan"), "LOAN_RECOVERY")
        self.assertEqual(normalize_deduction_type("Asset Damage"), "DAMAGE")
        self.assertEqual(normalize_deduction_type("Late fine penalty"), "PENALTY")
        self.assertEqual(normalize_deduction_type("Salary Overpayment Excess"), "EXCESS_PAYMENT")
        self.assertEqual(normalize_deduction_type("Miscellaneous deduction"), "OTHER")
        self.assertEqual(normalize_deduction_type(""), "OTHER")

        for dt in VALID_DEDUCTION_TYPES:
            self.assertEqual(normalize_deduction_type(dt), dt)

    def test_format_period(self):
        """Verifies period formatting to YYYY-MM."""
        self.assertEqual(format_period("2026-10-01"), "2026-10")
        self.assertEqual(format_period("2026-10"), "2026-10")

    def test_transform_open_reimbursement_claim(self):
        """Scenario 1: Open reimbursement request transforms into target tuple."""
        raw_item = {
            "id": 10,
            "employee_id": "1001",
            "reimbursement_type": "TRAVEL",
            "requested_amount": "2500.00",
            "approved_amount": "2000.00",
            "bill_date": "2026-10-05",
            "description": "Travel expenses for onsite visit",
            "status": "PENDING",
            "payment_status": "UNPAID"
        }

        ok, row, q = transform_reimbursement_item(
            raw_item, self.emp_by_payroll_id, self.emp_by_uuid, self.reimb_map
        )

        self.assertTrue(ok)
        self.assertIsNone(q)
        self.assertIsNotNone(row)

        claim_id, tid, emp_id, comp_id, req_amt, app_amt, b_date, desc, doc_id, status, remarks = row[:11]
        self.assertEqual(tid, self.tenant_id)
        self.assertEqual(emp_id, self.employee_id)
        self.assertEqual(comp_id, self.reimbursement_comp_id)
        self.assertEqual(req_amt, Decimal("2500.0000"))
        self.assertEqual(app_amt, Decimal("2000.0000"))
        self.assertEqual(b_date, "2026-10-05")
        self.assertEqual(status, "SUBMITTED")  # PENDING mapped to SUBMITTED
        self.assertIsNone(doc_id)  # document_id is NULL until W-69

    def test_drop_paid_reimbursement_claim(self):
        """Scenario 2: Historical PAID reimbursement claim is dropped per Decision X-14."""
        raw_item = {
            "id": 11,
            "employee_id": "1001",
            "reimbursement_type": "FOOD",
            "requested_amount": "500.00",
            "payment_status": "PAID"
        }

        ok, row, q = transform_reimbursement_item(
            raw_item, self.emp_by_payroll_id, self.emp_by_uuid, self.reimb_map
        )

        self.assertFalse(ok)
        self.assertIsNone(row)
        self.assertIsNotNone(q)
        self.assertIn("Decision X-14", q["reason"])

    def test_quarantine_reimbursement_unknown_employee(self):
        """Scenario 3: Claim for unmapped employee lands in quarantine."""
        raw_item = {
            "id": 12,
            "employee_id": "UNKNOWN-999",
            "requested_amount": "100.00",
            "payment_status": "UNPAID"
        }

        ok, row, q = transform_reimbursement_item(
            raw_item, self.emp_by_payroll_id, self.emp_by_uuid, self.reimb_map
        )

        self.assertFalse(ok)
        self.assertIsNone(row)
        self.assertIsNotNone(q)
        self.assertIn("not found in merged employees", q["reason"])

    def test_transform_open_deduction(self):
        """Scenario 4: Open salary deduction transforms into pay_input and employee_deduction tuples."""
        raw_item = {
            "id": 20,
            "employee_id": "1001",
            "deduction_amount": "750.00",
            "deduction_month": "2026-10-01",
            "deduction_type": "Travel advance repayment",
            "reason": "Monthly recovery",
            "status": "ACTIVE"
        }

        ok, row_pair, q = transform_deduction_item(
            raw_item, self.emp_by_payroll_id, self.emp_by_uuid
        )

        self.assertTrue(ok)
        self.assertIsNone(q)
        self.assertIsNotNone(row_pair)

        pay_input, deduction = row_pair

        # Verify core.pay_input tuple
        pi_id, pi_tid, pi_eid, pi_period, pi_kind, pi_qty, pi_amt, pi_mod, pi_ref, pi_rev = pay_input
        self.assertEqual(pi_tid, self.tenant_id)
        self.assertEqual(pi_eid, self.employee_id)
        self.assertEqual(pi_period, "2026-10")
        self.assertEqual(pi_kind, "AD_HOC_DEDUCTION")
        self.assertEqual(pi_amt, Decimal("750.0000"))
        self.assertEqual(pi_mod, "PAYROLL")
        self.assertEqual(pi_ref, "deduction:20")

        # Verify payroll.employee_deduction tuple
        d_id, d_tid, d_eid, d_period, d_type, d_amt, d_reason, d_rem, d_doc, d_status, d_pi_id, d_post_period = deduction
        self.assertEqual(d_tid, self.tenant_id)
        self.assertEqual(d_eid, self.employee_id)
        self.assertEqual(d_period, "2026-10")
        self.assertEqual(d_type, "ADVANCE_RECOVERY")
        self.assertEqual(d_amt, Decimal("750.0000"))
        self.assertEqual(d_status, "POSTED")
        self.assertEqual(d_pi_id, pi_id)  # references pay_input_id
        self.assertIsNone(d_doc)

    def test_drop_processed_deduction(self):
        """Scenario 5: Historical PROCESSED deduction is dropped per Decision X-14."""
        raw_item = {
            "id": 21,
            "employee_id": "1001",
            "deduction_amount": "500.00",
            "status": "PROCESSED"
        }

        ok, row_pair, q = transform_deduction_item(
            raw_item, self.emp_by_payroll_id, self.emp_by_uuid
        )

        self.assertFalse(ok)
        self.assertIsNone(row_pair)
        self.assertIsNotNone(q)
        self.assertIn("Decision X-14", q["reason"])

    def test_quarantine_invalid_amounts(self):
        """Scenario 6: Amounts <= 0 are rejected and quarantined."""
        raw_claim = {
            "id": 13,
            "employee_id": "1001",
            "requested_amount": "0.00",
            "payment_status": "UNPAID"
        }
        ok, row, q = transform_reimbursement_item(
            raw_claim, self.emp_by_payroll_id, self.emp_by_uuid, self.reimb_map
        )
        self.assertFalse(ok)
        self.assertIn("greater than 0", q["reason"])

        raw_deduct = {
            "id": 22,
            "employee_id": "1001",
            "deduction_amount": "-50.00",
            "status": "ACTIVE"
        }
        ok, row_pair, q = transform_deduction_item(
            raw_deduct, self.emp_by_payroll_id, self.emp_by_uuid
        )
        self.assertFalse(ok)
        self.assertIn("greater than 0", q["reason"])

    def test_run_loader_dry_run(self):
        """Scenario 7: Full loader executes cleanly in dry-run mode."""
        res = run_loader(dry_run=True, skip_db=True)
        self.assertIn("total_loaded", res)
        self.assertIn("quarantined", res)


if __name__ == "__main__":
    unittest.main()
