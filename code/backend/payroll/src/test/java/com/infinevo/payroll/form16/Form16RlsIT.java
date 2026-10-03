package com.infinevo.payroll.form16;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * PostgreSQL Row-Level Security (RLS) isolation tests for {@code payroll.tax_deductor} and
 * Form 16 reporting queries (W-36.4).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class Form16RlsIT extends AbstractIntegrationTest {

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

        // Seed deductor for both tenants as migration_user
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.tax_deductor (
                            tenant_id, tan, pan, tds_circle, signatory_name, signatory_parent_name, signatory_designation,
                            created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'test', 'test')
                        """)) {
            // Tenant A
            ps.setObject(1, TENANT_A);
            ps.setString(2, "MUMA12345A");
            ps.setString(3, "AAACM1234A");
            ps.setString(4, "MUM/TD/001/01");
            ps.setString(5, "Signatory A");
            ps.setString(6, "Parent A");
            ps.setString(7, "Director");
            ps.executeUpdate();

            // Tenant B
            ps.setObject(1, TENANT_B);
            ps.setString(2, "DELB12345B");
            ps.setString(3, "BBBCM1234B");
            ps.setString(4, "DEL/TD/002/02");
            ps.setString(5, "Signatory B");
            ps.setString(6, "Parent B");
            ps.setString(7, "VP");
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        PayRunTestSchema.clean();
    }

    @Test
    @DisplayName("Unbound app_user connection sees zero rows in payroll.tax_deductor")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll.tax_deductor")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("Bound app_user as tenant A sees only tenant A deductor")
    void tenantASeesOnlyTenantADeductor() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tan, signatory_name FROM payroll.tax_deductor")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("tan")).isEqualTo("MUMA12345A");
                assertThat(rs.getString("signatory_name")).isEqualTo("Signatory A");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("Bound app_user as tenant B sees only tenant B deductor")
    void tenantBSeesOnlyTenantBDeductor() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tan, signatory_name FROM payroll.tax_deductor")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("tan")).isEqualTo("DELB12345B");
                assertThat(rs.getString("signatory_name")).isEqualTo("Signatory B");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("Inserting a deductor with mismatched tenant_id fails under app_user RLS")
    void cannotInsertMismatchedTenantDeductor() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.tax_deductor (
                        tenant_id, tan, pan, tds_circle, signatory_name, signatory_parent_name, signatory_designation,
                        created_by, updated_by
                    ) VALUES (?, 'PUNX12345Z', 'XXXXM1234Z', 'CIT-TDS', 'Spoofer', 'Parent', 'Hacker', 'test', 'test')
                    """)) {
                // Try inserting tenant B's ID while bound as tenant A
                ps.setObject(1, TENANT_B);
                assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }
}
