package com.infinevo.payroll.taxcalc.recalc;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Row-level security on {@code payroll.tax_recalc_failure} (D-147, V222): one tenant can neither read nor retry
 * another tenant's failed recalculation, through raw SQL as {@code app_user} or through the service.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class TaxRecalcFailureRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private TaxRecalcFailureService failureService;

    private UUID failureA;
    private UUID failureB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();
        UUID employeeA = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-RF-A", "rf_a@acme.com", "Alice", "Acme");
        UUID employeeB =
                TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-RF-B", "rf_b@globex.com", "Bob", "Globex");
        failureA = insertFailure(TENANT_A, employeeA);
        failureB = insertFailure(TENANT_B, employeeB);
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Unbound app_user sees no failure rows; bound to tenant A it sees only tenant A's")
    void rawSqlIsTenantScoped() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM payroll.tax_recalc_failure")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
        try (Connection conn = PayrollTestSchema.appConnection()) {
            setConnectionTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, tenant_id FROM payroll.tax_recalc_failure");
                    ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1, UUID.class)).isEqualTo(failureA);
                assertThat(rs.getObject(2, UUID.class)).isEqualTo(TENANT_A);
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("app_user bound to tenant A cannot insert a failure row for tenant B (SQLState 42501)")
    void crossTenantInsertRefused() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.tax_recalc_failure (tenant_id, employee_id, financial_year, trigger, error_message)
                    SELECT ?, employee_id, '2025-2026', 'OFFICER', 'bad' FROM payroll.tax_recalc_failure LIMIT 1
                    """)) {
                ps.setObject(1, TENANT_B);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(e ->
                                assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Tenant B's service lists only its own failure and cannot retry tenant A's")
    void serviceCannotReadOrRetryAcrossTenants() throws SQLException {
        TenantContext.set(TENANT_B);
        try {
            assertThat(failureService.open(null))
                    .extracting(TaxRecalcFailureResponse::id)
                    .containsExactly(failureB);
            assertThat(failureService.open(FY))
                    .extracting(TaxRecalcFailureResponse::id)
                    .containsExactly(failureB);
            assertThatThrownBy(() -> failureService.retry(failureA, null))
                    .isInstanceOf(TaxRecalcFailureNotFoundException.class);
        } finally {
            TenantContext.clear();
        }

        // Tenant A's row is untouched by the refused retry.
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT attempts, resolved_at FROM payroll.tax_recalc_failure WHERE id = ?")) {
            ps.setObject(1, failureA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
                assertThat(rs.getObject(2)).isNull();
            }
        }
    }

    private static UUID insertFailure(UUID tenantId, UUID employeeId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.tax_recalc_failure (id, tenant_id, employee_id, financial_year, trigger,
                                                                error_message)
                        VALUES (?, ?, ?, ?, 'DECLARATION_SUBMITTED', 'seeded failure')
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setString(4, FY);
            ps.executeUpdate();
        }
        return id;
    }

    private static void setConnectionTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
