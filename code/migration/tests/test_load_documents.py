"""
Unit Tests for W-69 Document Migration Loader (Task 2)
Verifies:
  1. Valid HRMS & POI documents upload to documents/{tenantId}/{employeeId}/{kind}/{docId}
  2. size_bytes and checksum_sha256 calculation and read-back verification
  3. HTTP 404 Cloudinary download failures land in quarantine
  4. Files > 10 MB limit land in quarantine and are not uploaded
  5. Idempotency: already migrated documents are skipped on second run
"""

import os
import sys
import unittest
import hashlib
import uuid
from unittest.mock import MagicMock, patch

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from loaders.load_documents import (
    process_single_document,
    BLOB_CONTAINER,
    MAX_FILE_SIZE_BYTES,
    get_file_extension
)


class MockBlobClient:
    def __init__(self, blob_path, storage_dict):
        self.blob_path = blob_path
        self.storage_dict = storage_dict

    def upload_blob(self, content, overwrite=True, content_settings=None):
        self.storage_dict[self.blob_path] = content

    def download_blob(self):
        content = self.storage_dict.get(self.blob_path, b"")
        mock_stream = MagicMock()
        mock_stream.readall.return_value = content
        return mock_stream

    def delete_blob(self):
        if self.blob_path in self.storage_dict:
            del self.storage_dict[self.blob_path]


class MockContainerClient:
    def __init__(self):
        self.blobs = {}

    def get_blob_client(self, blob_path):
        return MockBlobClient(blob_path, self.blobs)


class TestLoadDocuments(unittest.TestCase):
    def setUp(self):
        self.tenant_id = "11111111-1111-1111-1111-111111111111"
        self.employee_id = "22222222-2222-2222-2222-222222222222"
        self.doc_id = "33333333-3333-3333-3333-333333333333"
        self.container_client = MockContainerClient()

    def test_successful_document_upload_and_verification(self):
        """Scenario 1 & 2: Valid document downloads, uploads to Blob, and verifies checksum/size."""
        content = b"PDF-1.4 Mock valid document content for testing"
        expected_sha = hashlib.sha256(content).hexdigest()
        blob_path = f"{self.tenant_id}/{self.employee_id}/EMPLOYEE_DOCUMENT/{self.doc_id}"

        doc_item = {
            "source": "HRMS",
            "legacy_id": 1,
            "doc_id": self.doc_id,
            "tenant_id": self.tenant_id,
            "employee_id": self.employee_id,
            "kind": "EMPLOYEE_DOCUMENT",
            "file_name": "passport.pdf",
            "content_type": "application/pdf",
            "blob_path": blob_path,
            "file_url": "https://res.cloudinary.com/dthv5gvff/image/upload/v1/passport.pdf"
        }

        with patch("loaders.load_documents.fetch_file_with_retry") as mock_fetch:
            mock_fetch.return_value = (content, None)
            ok, result, quarantine = process_single_document(
                doc_item, self.container_client, existing_checksums={}, dry_run=False
            )

        self.assertTrue(ok)
        self.assertIsNone(quarantine)
        self.assertEqual(result["checksum_sha256"], expected_sha)
        self.assertEqual(result["size_bytes"], len(content))
        self.assertEqual(result["blob_container"], BLOB_CONTAINER)
        self.assertEqual(result["blob_path"], blob_path)
        self.assertIn(blob_path, self.container_client.blobs)
        self.assertEqual(self.container_client.blobs[blob_path], content)

    def test_http_404_lands_in_quarantine(self):
        """Scenario 3: A 404 Cloudinary download error is quarantined, does not fail run."""
        blob_path = f"{self.tenant_id}/{self.employee_id}/EMPLOYEE_DOCUMENT/{self.doc_id}"
        doc_item = {
            "source": "HRMS",
            "legacy_id": 99,
            "doc_id": self.doc_id,
            "tenant_id": self.tenant_id,
            "employee_id": self.employee_id,
            "kind": "EMPLOYEE_DOCUMENT",
            "file_name": "missing_doc.pdf",
            "content_type": "application/pdf",
            "blob_path": blob_path,
            "file_url": "https://res.cloudinary.com/dthv5gvff/image/upload/v1/missing_doc.pdf"
        }

        with patch("loaders.load_documents.fetch_file_with_retry") as mock_fetch:
            mock_fetch.return_value = (None, "HTTP 404 Not Found at legacy URL")
            ok, result, quarantine = process_single_document(
                doc_item, self.container_client, existing_checksums={}, dry_run=False
            )

        self.assertFalse(ok)
        self.assertIsNone(result)
        self.assertIsNotNone(quarantine)
        self.assertEqual(quarantine["legacy_id"], 99)
        self.assertIn("404", quarantine["reason"])
        self.assertNotIn(blob_path, self.container_client.blobs)

    def test_file_over_10mb_is_quarantined(self):
        """Scenario 4: An 11 MB fixture is quarantined and not uploaded to Blob."""
        # 11 MB
        oversized_content = b"X" * (MAX_FILE_SIZE_BYTES + 1024 * 1024)
        blob_path = f"{self.tenant_id}/{self.employee_id}/EMPLOYEE_DOCUMENT/{self.doc_id}"
        doc_item = {
            "source": "HRMS",
            "legacy_id": 101,
            "doc_id": self.doc_id,
            "tenant_id": self.tenant_id,
            "employee_id": self.employee_id,
            "kind": "EMPLOYEE_DOCUMENT",
            "file_name": "huge_scan.pdf",
            "content_type": "application/pdf",
            "blob_path": blob_path,
            "file_url": "https://res.cloudinary.com/dthv5gvff/image/upload/v1/huge_scan.pdf"
        }

        with patch("loaders.load_documents.fetch_file_with_retry") as mock_fetch:
            mock_fetch.return_value = (oversized_content, None)
            ok, result, quarantine = process_single_document(
                doc_item, self.container_client, existing_checksums={}, dry_run=False
            )

        self.assertFalse(ok)
        self.assertIsNone(result)
        self.assertIsNotNone(quarantine)
        self.assertIn("10 MB limit", quarantine["reason"])
        self.assertNotIn(blob_path, self.container_client.blobs)

    def test_disallowed_extension_is_quarantined(self):
        """Scenario: File extension outside pdf/jpg/jpeg/png/xlsx/csv is quarantined."""
        blob_path = f"{self.tenant_id}/{self.employee_id}/EMPLOYEE_DOCUMENT/{self.doc_id}"
        doc_item = {
            "source": "HRMS",
            "legacy_id": 102,
            "doc_id": self.doc_id,
            "tenant_id": self.tenant_id,
            "employee_id": self.employee_id,
            "kind": "EMPLOYEE_DOCUMENT",
            "file_name": "script.exe",
            "content_type": "application/x-msdownload",
            "blob_path": blob_path,
            "file_url": "https://res.cloudinary.com/dthv5gvff/image/upload/v1/script.exe"
        }

        ok, result, quarantine = process_single_document(
            doc_item, self.container_client, existing_checksums={}, dry_run=False
        )
        self.assertFalse(ok)
        self.assertIsNone(result)
        self.assertIn("not in allowed list", quarantine["reason"])

    def test_idempotent_skip_on_second_run(self):
        """Scenario 5: Re-running skips documents that have already been verified in core.document."""
        blob_path = f"{self.tenant_id}/{self.employee_id}/INVESTMENT_PROOF/{self.doc_id}"
        existing_sha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        doc_item = {
            "source": "POI",
            "legacy_id": 50,
            "doc_id": self.doc_id,
            "tenant_id": self.tenant_id,
            "employee_id": self.employee_id,
            "kind": "INVESTMENT_PROOF",
            "file_name": "rent_receipt.jpg",
            "content_type": "image/jpeg",
            "blob_path": blob_path,
            "file_url": "https://res.cloudinary.com/dbsu0ghks/image/upload/v1/rent_receipt.jpg",
            "existing_size": 24500
        }

        existing_checksums = {blob_path: existing_sha}

        with patch("loaders.load_documents.fetch_file_with_retry") as mock_fetch:
            ok, result, quarantine = process_single_document(
                doc_item, self.container_client, existing_checksums=existing_checksums, dry_run=False
            )
            # Should NOT make any network call
            mock_fetch.assert_not_called()

        self.assertTrue(ok)
        self.assertTrue(result["skipped_existing"])
        self.assertEqual(result["checksum_sha256"], existing_sha)
        self.assertIsNone(quarantine)


class TestRunLoaderStorageGuard(unittest.TestCase):
    """A real run with no storage must stop, not write rows for files it never uploaded."""

    def test_real_run_without_storage_credentials_refuses(self):
        from loaders import load_documents
        env = {k: v for k, v in os.environ.items()
               if k not in ("AZURE_STORAGE_CONNECTION_STRING", "AZURE_STORAGE_ACCOUNT_URL")}
        with patch.dict(os.environ, env, clear=True):
            with self.assertRaises(RuntimeError) as ctx:
                load_documents.run_loader(dry_run=False, skip_db=False)
        self.assertIn("refusing to write document rows", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
