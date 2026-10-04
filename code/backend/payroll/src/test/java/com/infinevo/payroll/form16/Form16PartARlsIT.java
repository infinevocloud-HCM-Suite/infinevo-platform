package com.infinevo.payroll.form16;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static com.infinevo.payroll.form16.PartATestFixtures.FY;
import static com.infinevo.payroll.form16.PartATestFixtures.insertPan;
import static com.infinevo.payroll.form16.PartATestFixtures.pdf;
import static com.infinevo.payroll.form16.PartATestFixtures.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.proof.ProofTestDocuments;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

/**
 * W-36.5 §7 — tenant isolation of {@code payroll.form16_part_a} and of the PAN match (BUG-002).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@Import(ProofTestDocuments.class)
@EnabledIfDockerAvailable
class Form16PartARlsIT extends AbstractIntegrationTest {

    @Autowired
    private Form16PartAService service;

    private UUID employeeA;
    private UUID employeeB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayrollTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();
        employeeA = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-RLS-A", "a@acme.com", "Anil", "Kumar");
        employeeB = TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-RLS-B", "b@globex.com", "Bina", "Shah");
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("a PAN that belongs to tenant B is unmatched for tenant A")
    void otherTenantsPanIsUnmatched() throws Exception {
        insertPan(TENANT_B, employeeB, "BBBBB2222B");
        TenantContext.set(TENANT_A);

        PartAUploadResult result = service.upload(
                FY,
                new MockMultipartFile("file", "partA.zip", "application/zip", zip(Map.of("BBBBB2222B.pdf", pdf("b")))));

        assertThat(result.matched()).isZero();
        assertThat(result.unmatched()).containsExactly("BBBBB2222B.pdf");
        assertThat(PartATestFixtures.count("SELECT count(*) FROM payroll.form16_part_a"))
                .isZero();
    }

    @Test
    @DisplayName("as app_user, tenant A reads only its own rows, and an unbound session reads none")
    void appUserSeesOwnTenantOnly() throws Exception {
        insertRow(TENANT_A, employeeA, true);
        insertRow(TENANT_B, employeeB, true);

        try (Connection conn = PayrollTestSchema.appConnection()) {
            assertThat(countRows(conn)).isZero();

            PayrollTestSchema.bindTenant(conn, TENANT_A);
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("SELECT tenant_id, employee_id FROM payroll.form16_part_a")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1, UUID.class)).isEqualTo(TENANT_A);
                assertThat(rs.getObject(2, UUID.class)).isEqualTo(employeeA);
                assertThat(rs.next()).isFalse();
            }
        }

        TenantContext.set(TENANT_A);
        assertThat(service.list(FY)).extracting(PartARow::employeeId).containsExactly(employeeA);
    }

    @Test
    @DisplayName("as app_user bound to tenant A, a row for tenant B cannot be written")
    void appUserCannotWriteOtherTenant() throws Exception {
        UUID documentB = PayrollTestSchema.insertDocument(TENANT_B, "b.pdf", pdf("b"));
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.form16_part_a
                        (tenant_id, employee_id, financial_year, document_id, source_file_name)
                    VALUES (?, ?, ?, ?, 'b.pdf')
                    """)) {
                ps.setObject(1, TENANT_B);
                ps.setObject(2, employeeB);
                ps.setString(3, FY);
                ps.setObject(4, documentB);
                assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("two active rows for one (tenant, employee, fy) are refused by the index; a superseded one is not")
    void oneActiveRowPerEmployeeAndYear() throws Exception {
        insertRow(TENANT_A, employeeA, true);
        assertThatThrownBy(() -> insertRow(TENANT_A, employeeA, true))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_form16_part_a_tenant_employee_fy_active");

        insertRow(TENANT_A, employeeA, false);
        assertThat(PartATestFixtures.count(
                        "SELECT count(*) FROM payroll.form16_part_a WHERE employee_id = ?", employeeA))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("an inactive row must carry superseded_at, and an active one must not")
    void supersededCheck() throws Exception {
        UUID document = PayrollTestSchema.insertDocument(TENANT_A, "a.pdf", pdf("a"));
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.form16_part_a
                            (tenant_id, employee_id, financial_year, document_id, source_file_name, is_active)
                        VALUES (?, ?, ?, ?, 'a.pdf', false)
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeA);
            ps.setString(3, FY);
            ps.setObject(4, document);
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    private static void insertRow(UUID tenantId, UUID employeeId, boolean active) throws SQLException {
        UUID document = PayrollTestSchema.insertDocument(tenantId, "partA.pdf", pdf(tenantId.toString()));
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.form16_part_a
                            (tenant_id, employee_id, financial_year, document_id, source_file_name, is_active,
                             superseded_at)
                        VALUES (?, ?, ?, ?, 'partA.pdf', ?, CASE WHEN ? THEN NULL ELSE clock_timestamp() END)
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setString(3, FY);
            ps.setObject(4, document);
            ps.setBoolean(5, active);
            ps.setBoolean(6, active);
            ps.executeUpdate();
        }
    }

    private static long countRows(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM payroll.form16_part_a")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
