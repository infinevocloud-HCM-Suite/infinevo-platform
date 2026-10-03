package com.infinevo.payroll.form16;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.form16.exception.ZipEncryptedException;
import com.infinevo.payroll.form16.exception.ZipLimitExceededException;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Acceptance integration tests for Form 16 Part A upload and self-service (W-36.5 §7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class Form16PartAIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";
    private static final String PAN_1 = "ABCDE1234F";
    private static final String PAN_2 = "WXYZP5678Q";
    private static final String PAN_UNKNOWN = "ZZZZZ9999Z";

    @Autowired
    private Form16PartAService form16PartAService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private Form16PartARepository form16PartARepository;

    private Form16PartAController controller;
    private UUID employeeId1;
    private UUID employeeId2;
    private EmployeeResponse employeeResp1;
    private EmployeeResponse employeeResp2;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws Exception {
        PayRunTestSchema.clean();
        PayrollTestSchema.seedTenants();

        employeeId1 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-F16A-01", "emp1@acme.com", "Anil", "Kumar");
        employeeId2 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-F16A-02", "emp2@acme.com", "Sunil", "Verma");

        seedPan(TENANT_A, employeeId1, PAN_1);
        seedPan(TENANT_A, employeeId2, PAN_2);

        employeeResp1 = PayrollTestSchema.createTestEmployee(
                employeeId1, TENANT_A, "EMP-F16A-01", "Anil", "Kumar", "emp1@acme.com");
        employeeResp2 = PayrollTestSchema.createTestEmployee(
                employeeId2, TENANT_A, "EMP-F16A-02", "Sunil", "Verma", "emp2@acme.com");

        TenantContext.set(TENANT_A);
        controller = new Form16PartAController(form16PartAService);
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        PayRunTestSchema.clean();
    }

    @Test
    @DisplayName("Acceptance test: ZIP of three PDFs (2 known PANs, 1 unknown) => matched 2, unmatched 1")
    void uploadThreePdfsMatchesKnownAndReportsUnmatched() throws Exception {
        byte[] pdf1Bytes = "%PDF-1.4 Part A Anil Kumar".getBytes(StandardCharsets.UTF_8);
        byte[] pdf2Bytes = "%PDF-1.4 Part A Sunil Verma".getBytes(StandardCharsets.UTF_8);
        byte[] pdf3Bytes = "%PDF-1.4 Part A Unknown PAN".getBytes(StandardCharsets.UTF_8);

        String entry1 = PAN_1 + "_2026-27.pdf";
        String entry2 = PAN_2 + "_2026-27.pdf";
        String entry3 = PAN_UNKNOWN + "_2026-27.pdf";

        byte[] zipBytes = createZip(Map.of(
                entry1, pdf1Bytes,
                entry2, pdf2Bytes,
                entry3, pdf3Bytes));

        MockMultipartFile file = new MockMultipartFile("file", "traces_part_a.zip", "application/zip", zipBytes);

        // 1. Upload via controller
        PartAResponse<PartAUploadResult> uploadResp = controller.upload(FY, file);
        assertThat(uploadResp.status()).isEqualTo(200);
        PartAUploadResult result = uploadResp.data();
        assertThat(result.matched()).isEqualTo(2);
        assertThat(result.unmatched()).containsExactly(entry3);
        assertThat(result.skipped()).isEmpty();

        // 2. Each employee's /me returns a link that downloads the same bytes
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeResp1);
        PartAResponse<PartAEmployeeResponse> meResp1 = controller.own(FY);
        assertThat(meResp1.status()).isEqualTo(200);
        assertThat(meResp1.data().documentId()).isNotNull();
        assertThat(meResp1.data().link()).contains("http://localhost:8080/api/v1/documents/download?t=");

        DocumentService.DocumentContent content1 =
                documentService.open(meResp1.data().documentId());
        byte[] downloadedBytes1 = content1.content().readAllBytes();
        assertThat(downloadedBytes1).isEqualTo(pdf1Bytes);

        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeResp2);
        PartAResponse<PartAEmployeeResponse> meResp2 = controller.own(FY);
        assertThat(meResp2.status()).isEqualTo(200);
        DocumentService.DocumentContent content2 =
                documentService.open(meResp2.data().documentId());
        byte[] downloadedBytes2 = content2.content().readAllBytes();
        assertThat(downloadedBytes2).isEqualTo(pdf2Bytes);

        // 3. Officer list endpoint returns active records
        PartAResponse<List<PartAOfficerItem>> listResp = controller.list(FY);
        assertThat(listResp.status()).isEqualTo(200);
        assertThat(listResp.data()).hasSize(2);
        assertThat(listResp.data())
                .extracting(PartAOfficerItem::employeeId)
                .containsExactlyInAnyOrder(employeeId1, employeeId2);

        // 4. Re-upload leaves one active row and soft-deletes the old document
        UUID oldDocId1 = meResp1.data().documentId();
        byte[] updatedPdf1Bytes = "%PDF-1.4 Part A Anil Kumar Updated".getBytes(StandardCharsets.UTF_8);
        byte[] reuploadZip = createZip(Map.of(entry1, updatedPdf1Bytes));
        MockMultipartFile reuploadFile =
                new MockMultipartFile("file", "traces_reupload.zip", "application/zip", reuploadZip);

        PartAResponse<PartAUploadResult> reuploadResp = controller.upload(FY, reuploadFile);
        assertThat(reuploadResp.data().matched()).isEqualTo(1);

        // Check rows in database via JDBC
        int activeCount = 0;
        int supersededCount = 0;
        UUID supersededDocId = null;
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT is_active, superseded_at, document_id FROM payroll.form16_part_a WHERE tenant_id = ? AND employee_id = ? AND financial_year = ?")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId1);
            ps.setString(3, FY);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int totalRows = 0;
                while (rs.next()) {
                    totalRows++;
                    boolean active = rs.getBoolean("is_active");
                    if (active) {
                        activeCount++;
                    } else {
                        supersededCount++;
                        assertThat(rs.getTimestamp("superseded_at")).isNotNull();
                        supersededDocId = (UUID) rs.getObject("document_id");
                    }
                }
                assertThat(totalRows).isEqualTo(2);
                assertThat(activeCount).isEqualTo(1);
                assertThat(supersededCount).isEqualTo(1);
                assertThat(supersededDocId).isEqualTo(oldDocId1);
            }
        }

        // Old document is soft-deleted
        assertThatThrownBy(() -> documentService.get(oldDocId1)).isInstanceOf(DocumentService.NotFoundException.class);
    }

    @Test
    @DisplayName("Encrypted ZIP archive throws ZipEncryptedException (HTTP 400 ZIP_ENCRYPTED)")
    void encryptedZipThrowsZipEncryptedException() {
        byte[] encryptedZip = createEncryptedZip();
        MockMultipartFile file = new MockMultipartFile("file", "encrypted.zip", "application/zip", encryptedZip);

        assertThatThrownBy(() -> controller.upload(FY, file)).isInstanceOf(ZipEncryptedException.class);
    }

    @Test
    @DisplayName("ZIP with 2,001 entries throws ZipLimitExceededException (HTTP 400)")
    void zipExceedingEntryLimitThrowsZipLimitExceededException() throws IOException {
        byte[] largeZip = createZipWithEntries(2001);
        MockMultipartFile file = new MockMultipartFile("file", "huge.zip", "application/zip", largeZip);

        assertThatThrownBy(() -> controller.upload(FY, file)).isInstanceOf(ZipLimitExceededException.class);
    }

    @Test
    @DisplayName("FORM16_PART_A cannot be uploaded through generic client upload (isUploadable is false)")
    void form16PartACannotBeUploadedByClient() {
        assertThat(DocumentKind.FORM16_PART_A.isUploadable()).isFalse();
    }

    private static void seedPan(UUID tenantId, UUID employeeId, String pan) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee_identification (
                            tenant_id, employee_id, pan_number, created_by, updated_by
                        ) VALUES (?, ?, ?, 'test', 'test')
                        ON CONFLICT (tenant_id, employee_id) DO UPDATE SET pan_number = EXCLUDED.pan_number
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setString(3, pan);
            ps.executeUpdate();
        }
    }

    private static byte[] createZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static byte[] createZipWithEntries(int count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < count; i++) {
                ZipEntry ze = new ZipEntry("entry_" + i + ".pdf");
                zos.putNextEntry(ze);
                zos.write("%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static byte[] createEncryptedZip() {
        // Construct a valid local zip entry header with the encryption bit (flag & 1) set
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // Local file header signature: 0x04034b50
            baos.write(new byte[] {0x50, 0x4b, 0x03, 0x04});
            // Version needed to extract: 20 (2.0)
            baos.write(new byte[] {0x14, 0x00});
            // General purpose bit flag: 0x0001 (BIT 0 SET = ENCRYPTED)
            baos.write(new byte[] {0x01, 0x00});
            // Compression method: 8 (deflated)
            baos.write(new byte[] {0x08, 0x00});
            // Last mod file time / date
            baos.write(new byte[] {0x00, 0x00, 0x00, 0x00});
            // CRC-32
            baos.write(new byte[] {0x00, 0x00, 0x00, 0x00});
            // Compressed size: 10
            baos.write(new byte[] {0x0a, 0x00, 0x00, 0x00});
            // Uncompressed size: 10
            baos.write(new byte[] {0x0a, 0x00, 0x00, 0x00});
            // File name length: 8
            baos.write(new byte[] {0x08, 0x00});
            // Extra field length: 0
            baos.write(new byte[] {0x00, 0x00});
            // File name: test.pdf
            baos.write("test.pdf".getBytes(StandardCharsets.UTF_8));
            // Dummy encrypted payload
            baos.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        } catch (IOException ignored) {
        }
        return baos.toByteArray();
    }
}
