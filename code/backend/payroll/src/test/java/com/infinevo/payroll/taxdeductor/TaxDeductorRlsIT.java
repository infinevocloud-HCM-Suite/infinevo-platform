package com.infinevo.payroll.taxdeductor;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Row-Level Security integration tests for payroll.tax_deductor (W-36.3, spec section 7).
 * Verifies as app_user that Tenant A cannot read Tenant B's deductor details,
 * cross-tenant raw SQL insert is blocked by RLS, and second row is blocked by unique index.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class TaxDeductorRlsIT extends AbstractIntegrationTest {

    private UUID deductorBId;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        deductorBId = UUID.randomUUID();
        seedTaxDeductor(
                TENANT_B,
                deductorBId,
                "BLRT12345A",
                "ABCDE1234F",
                "BLR/TD/001/01",
                "Anand Kumar",
                "Suresh Kumar",
                "Director Finance");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("As app_user, Tenant A cannot read Tenant B's tax deductor row under RLS")
    void tenantACannotReadTenantBRow() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.tax_deductor WHERE id = ?")) {
                ps.setObject(1, deductorBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must see 0 rows for Tenant B's tax deductor under RLS")
                            .isZero();
                }
            }

            // Control check: bound to Tenant B
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.tax_deductor WHERE id = ?")) {
                ps.setObject(1, deductorBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("A raw-SQL INSERT with Tenant B's ID under Tenant A's context is refused by RLS")
    void rawSqlInsertCrossTenant_isRefusedByRls() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payroll.tax_deductor (id, tenant_id, tan, pan, tds_circle, signatory_name, signatory_designation, created_by, updated_by) "
                            + "VALUES (?, ?, 'MUMT99999A', 'ABCDE9999F', 'MUM/TD/009/09', 'Malicious Actor', 'Hacker', 'attacker', 'attacker')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);

                assertThatThrownBy(ps::executeUpdate)
                        .as("Tenant A inserting a row with tenant_id B must be blocked by RLS")
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("A second row for one tenant is refused by the unique index uk_tax_deductor_tenant")
    void secondRowForOneTenant_isRefusedByUniqueIndex() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            // First row for Tenant A
            UUID id1 = UUID.randomUUID();
            seedTaxDeductor(
                    TENANT_A, id1, "MUMT11111A", "ABCDE1111F", "MUM/TD/001/01", "Officer A", null, "Head Payroll");

            // Attempting second row for Tenant A
            UUID id2 = UUID.randomUUID();
            assertThatThrownBy(() -> seedTaxDeductor(
                            TENANT_A,
                            id2,
                            "MUMT22222B",
                            "ABCDE2222G",
                            "MUM/TD/002/02",
                            "Officer B",
                            null,
                            "Head Finance"))
                    .as("Second tax deductor row for Tenant A must violate uk_tax_deductor_tenant")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_tax_deductor_tenant");
        }
    }

    private static void seedTaxDeductor(
            UUID tenantId,
            UUID id,
            String tan,
            String pan,
            String tdsCircle,
            String signatoryName,
            String signatoryParentName,
            String signatoryDesignation)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.tax_deductor (id, tenant_id, tan, pan, tds_circle, signatory_name, signatory_parent_name, signatory_designation, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'system', 'system')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, tan);
            ps.setString(4, pan);
            ps.setString(5, tdsCircle);
            ps.setString(6, signatoryName);
            ps.setString(7, signatoryParentName);
            ps.setString(8, signatoryDesignation);
            ps.executeUpdate();
        }
    }
}
