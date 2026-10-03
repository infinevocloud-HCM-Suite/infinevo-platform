package com.infinevo.payroll.form16;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Row-Level Security (RLS) and multi-tenancy tests for Form 16 Part A (W-36.5 §7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class Form16PartARlsIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";
    private static final String PAN_TENANT_B = "BBBBB2222B";

    @Autowired
    private Form16PartAService form16PartAService;

    private Form16PartAController controller;
    private UUID employeeIdB;
    private UUID dummyDocIdB;

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

        // Seed employee in Tenant B
        employeeIdB = TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-RLS-B", "b@globex.com", "Rohan", "Mehta");
        seedPan(TENANT_B, employeeIdB, PAN_TENANT_B);

        // Seed document in core.document for tenant B
        dummyDocIdB = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.document (
                            id, tenant_id, employee_id, kind, file_name, content_type, size_bytes,
                            blob_container, blob_path, checksum_sha256, created_by, updated_by
                        ) VALUES (?, ?, ?, 'FORM16_PART_A', 'Form16-PartA-2026-2027.pdf', 'application/pdf', 100,
                                  'documents', 'dummy_path', '0000000000000000000000000000000000000000000000000000000000000000',
                                  'test', 'test')
                        """)) {
            ps.setObject(1, dummyDocIdB);
            ps.setObject(2, TENANT_B);
            ps.setObject(3, employeeIdB);
            ps.executeUpdate();
        }

        controller = new Form16PartAController(form16PartAService);
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        PayRunTestSchema.clean();
    }

    @Test
    @DisplayName("A PAN that belongs to tenant B is unmatched for tenant A")
    void panBelongingToTenantBIsUnmatchedInTenantA() throws IOException {
        TenantContext.set(TENANT_A);

        String entryName = PAN_TENANT_B + "_2026-27.pdf";
        byte[] zipBytes = createZip(Map.of(entryName, "%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile file = new MockMultipartFile("file", "tenant_a_upload.zip", "application/zip", zipBytes);

        PartAResponse<PartAUploadResult> response = controller.upload(FY, file);
        assertThat(response.data().matched()).isZero();
        assertThat(response.data().unmatched()).containsExactly(entryName);
    }

    @Test
    @DisplayName("As app_user, tenant A cannot read tenant B's payroll.form16_part_a rows")
    void appUserTenantACannotReadTenantBRows() throws SQLException {
        // Seed Form 16 Part A row for Tenant B
        UUID partARowIdB = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.form16_part_a (
                            id, tenant_id, employee_id, financial_year, document_id, source_file_name,
                            is_active, superseded_at, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, 'B_cert.pdf', true, null, 'test', 'test')
                        """)) {
            ps.setObject(1, partARowIdB);
            ps.setObject(2, TENANT_B);
            ps.setObject(3, employeeIdB);
            ps.setString(4, FY);
            ps.setObject(5, dummyDocIdB);
            ps.executeUpdate();
        }

        // As app_user bound to Tenant A
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
            }

            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("SELECT count(*) FROM payroll.form16_part_a")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("Two active rows for one (tenant, employee, fy) are refused by the unique index")
    void twoActiveRowsRefusedByUniqueIndex() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.form16_part_a (
                            id, tenant_id, employee_id, financial_year, document_id, source_file_name,
                            is_active, superseded_at, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, 'first.pdf', true, null, 'test', 'test')
                        """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_B);
            ps.setObject(3, employeeIdB);
            ps.setString(4, FY);
            ps.setObject(5, dummyDocIdB);
            ps.executeUpdate();
        }

        // Second active row for same (tenant, employee, fy) should fail on index
        // uk_form16_part_a_tenant_employee_fy_active
        assertThatThrownBy(() -> {
                    try (Connection conn = PayrollTestSchema.migrationConnection();
                            PreparedStatement ps = conn.prepareStatement(
                                    """
                            INSERT INTO payroll.form16_part_a (
                                id, tenant_id, employee_id, financial_year, document_id, source_file_name,
                                is_active, superseded_at, created_by, updated_by
                            ) VALUES (?, ?, ?, ?, ?, 'second.pdf', true, null, 'test', 'test')
                            """)) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, TENANT_B);
                        ps.setObject(3, employeeIdB);
                        ps.setString(4, FY);
                        ps.setObject(5, dummyDocIdB);
                        ps.executeUpdate();
                    }
                })
                .isInstanceOf(SQLException.class);
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
}
