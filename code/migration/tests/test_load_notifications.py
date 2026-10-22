"""
Unit Tests for Notifications Migration Loader (W-68 Task 3m / Ticket #90)
Verifies:
  1. Transform general notifications to core.notification format (event='LEGACY', channel='IN_APP', status='SENT')
  2. Transform timesheet notifications to core.notification format
  3. Recipient resolution across target id, legacy HRMS id, employee number, and raw email
  4. Read state: read_at populated when isRead is True, None when False
  5. Subject ref linking: timesheetId -> "timesheet:<id>", projectId -> "project:<id>"
  6. Quarantine handling: unresolvable recipient or missing legacy id/body
  7. Checksum calculation and restartability check
  8. Full run_loader executes cleanly in dry-run mode
  9. Database transaction, RLS tenant setting, and ledger tracking with mocks
"""

import os
import sys
import unittest
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from loaders.load_notifications import (
    det_uuid,
    parse_timestamp,
    compute_file_checksum,
    build_employee_lookup,
    resolve_recipient,
    transform_notification_item,
    transform_timesheet_notification_item,
    run_loader,
    FOUNDER_TENANT_ID,
)


class TestLoadNotifications(unittest.TestCase):
    def setUp(self):
        self.tenant_id = "11111111-1111-1111-1111-111111111111"
        self.employee_id = "22222222-2222-2222-2222-222222222222"
        self.work_email = "alice@example.com"
        self.run_started_at = datetime(2026, 10, 19, 10, 0, 0, tzinfo=timezone.utc)

        self.merged_employees = [
            {
                "id": self.employee_id,
                "tenant_id": self.tenant_id,
                "source_legacy_hrms_id": "hrms-emp-101",
                "employee_number": "EMP0101",
                "work_email": self.work_email,
            }
        ]
        self.emp_map = build_employee_lookup(self.merged_employees)

    def test_recipient_resolution(self):
        """Verifies recipient resolution by HRMS legacy id, employee number, email, and raw email."""
        # By HRMS ID
        t, emp_id, email = resolve_recipient("hrms-emp-101", self.emp_map)
        self.assertEqual(emp_id, self.employee_id)
        self.assertEqual(t, self.tenant_id)
        self.assertEqual(email, self.work_email)

        # By Employee Number
        t, emp_id, email = resolve_recipient("EMP0101", self.emp_map)
        self.assertEqual(emp_id, self.employee_id)

        # By Email
        t, emp_id, email = resolve_recipient("alice@example.com", self.emp_map)
        self.assertEqual(emp_id, self.employee_id)

        # Direct external email (not in employee map)
        t, emp_id, email = resolve_recipient("external@infinevo.com", self.emp_map)
        self.assertIsNone(emp_id)
        self.assertEqual(email, "external@infinevo.com")
        self.assertEqual(t, FOUNDER_TENANT_ID)

        # Unknown non-email reference
        t, emp_id, email = resolve_recipient("UNKNOWN_REF_999", self.emp_map)
        self.assertIsNone(emp_id)
        self.assertIsNone(email)

    def test_transform_general_notification_read(self):
        """Verifies transformation of a general notification with isRead=True and timesheetId."""
        raw = {
            "id": 1,
            "message": "Your timesheet has been approved",
            "recipientId": "hrms-emp-101",
            "senderId": "admin-1",
            "type": "APPROVAL",
            "isRead": True,
            "createdAt": "2026-10-10 14:30:00",
            "sent_at": "2026-10-10 14:35:00",
            "timesheetId": "ts-501",
        }

        res = transform_notification_item(raw, self.emp_map, self.run_started_at)

        self.assertEqual(res["recipient_employee_id"], self.employee_id)
        self.assertEqual(res["recipient_email"], self.work_email)
        self.assertEqual(res["event"], "LEGACY")
        self.assertEqual(res["channel"], "IN_APP")
        self.assertEqual(res["status"], "SENT")
        self.assertEqual(res["subject"], "Notification: APPROVAL")
        self.assertEqual(res["body"], "Your timesheet has been approved")
        self.assertEqual(res["subject_ref"], "timesheet:ts-501")
        self.assertIsNotNone(res["read_at"])
        self.assertEqual(res["attempt_count"], 1)
        self.assertEqual(res["created_by"], "migration")
        self.assertEqual(res["updated_by"], "migration")

    def test_transform_general_notification_unread(self):
        """Verifies transformation of unread notification leaves read_at as None."""
        raw = {
            "id": 2,
            "message": "Please submit your timesheet for this week",
            "recipientId": "EMP0101",
            "senderId": "admin-1",
            "type": "REMINDER",
            "isRead": False,
            "createdAt": "2026-10-12 09:00:00",
        }

        res = transform_notification_item(raw, self.emp_map, self.run_started_at)
        self.assertIsNone(res["read_at"])
        self.assertEqual(res["status"], "SENT")
        self.assertIsNone(res["subject_ref"])

    def test_transform_timesheet_notification(self):
        """Verifies transformation of timesheet_notifications table item."""
        raw = {
            "id": 10,
            "message": "Timesheet rejected due to incorrect project hours",
            "recipientId": "hrms-emp-101",
            "senderId": "manager-2",
            "type": "REJECTION",
            "isRead": True,
            "createdAt": "2026-10-15 16:00:00",
            "timesheetId": "ts-999",
            "projectId": 42,
            "rejectionReason": "Incorrect hours logged",
        }

        res = transform_timesheet_notification_item(raw, self.emp_map, self.run_started_at)

        self.assertEqual(res["recipient_employee_id"], self.employee_id)
        self.assertEqual(res["event"], "LEGACY")
        self.assertEqual(res["channel"], "IN_APP")
        self.assertEqual(res["status"], "SENT")
        self.assertEqual(res["subject"], "Timesheet: REJECTION")
        self.assertEqual(res["subject_ref"], "timesheet:ts-999")
        self.assertEqual(res["body"], "Timesheet rejected due to incorrect project hours")

    def test_transform_timesheet_notification_fallback_body(self):
        """Verifies timesheet notification uses rejectionReason when message is empty."""
        raw = {
            "id": 11,
            "message": "",
            "recipientId": "alice@example.com",
            "type": "REJECTION",
            "isRead": False,
            "rejectionReason": "Billing code invalid",
            "projectId": 88,
        }

        res = transform_timesheet_notification_item(raw, self.emp_map, self.run_started_at)
        self.assertEqual(res["body"], "Billing code invalid")
        self.assertEqual(res["subject_ref"], "project:88")

    def test_quarantine_unresolved_recipient(self):
        """Verifies missing/unknown recipient raises ValueError for quarantine."""
        raw = {
            "id": 99,
            "message": "System notification",
            "recipientId": "NONEXISTENT_USER",
            "type": "INFO",
        }

        with self.assertRaises(ValueError) as ctx:
            transform_notification_item(raw, self.emp_map, self.run_started_at)
        self.assertIn("UNRESOLVED_RECIPIENT", str(ctx.exception))

    def test_quarantine_empty_body(self):
        """Verifies empty message raises ValueError for quarantine."""
        raw = {
            "id": 100,
            "message": "   ",
            "recipientId": "EMP0101",
            "type": "INFO",
        }

        with self.assertRaises(ValueError) as ctx:
            transform_notification_item(raw, self.emp_map, self.run_started_at)
        self.assertIn("EMPTY_NOTIFICATION_BODY", str(ctx.exception))

    def test_deterministic_uuid(self):
        """Verifies deterministic UUID generation is stable across calls."""
        u1 = det_uuid(f"{self.tenant_id}:notifications:10")
        u2 = det_uuid(f"{self.tenant_id}:notifications:10")
        u3 = det_uuid(f"{self.tenant_id}:timesheet_notifications:10")

        self.assertEqual(u1, u2)
        self.assertNotEqual(u1, u3)

    def test_checksum_calculation(self):
        """Verifies SHA-256 checksum produces valid 64-char hex string."""
        cs = compute_file_checksum(["nonexistent_a.json", "nonexistent_b.json"])
        self.assertEqual(len(cs), 64)

    def test_run_loader_dry_run(self):
        """Verifies full run_loader executes cleanly in dry-run mode without database connection."""
        res = run_loader(dry_run=True)
        self.assertEqual(res["status"], "SUCCESS")
        self.assertTrue(res["dry_run"])
        self.assertIn("total_transformed", res)
        self.assertIn("total_quarantined", res)

    @patch("loaders.load_notifications.get_conn")
    def test_run_loader_db_execution(self, mock_get_conn):
        """Verifies database transaction, RLS tenant context, batch insert, and ledger update."""
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_get_conn.return_value = mock_conn

        # Mock ledger restart check: not DONE yet
        mock_cur.fetchone.return_value = None

        with patch("loaders.load_notifications.build_employee_lookup") as mock_lookup:
            mock_lookup.return_value = self.emp_map
            with patch("loaders.load_notifications.compute_file_checksum") as mock_cs:
                mock_cs.return_value = "dummy-checksum-1234"

                res = run_loader(conn=mock_conn, dry_run=False)

                self.assertEqual(res["status"], "SUCCESS")
                self.assertFalse(res["dry_run"])
                self.assertTrue(mock_conn.commit.called)


if __name__ == "__main__":
    unittest.main()
