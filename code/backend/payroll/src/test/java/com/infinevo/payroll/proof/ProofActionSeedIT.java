package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-34.1 — the four {@code payroll.proof.*} codes exist and reach the right roles, in a tenant that
 * existed before V110, in one created after it, and in one created through the trigger (spec section 7).
 *
 * <p>The new-tenant case is the one that catches a trigger that fires before the system roles exist:
 * Postgres runs same-event triggers by name, so the grant would find no role and silently grant nothing.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ProofActionSeedIT extends AbstractIntegrationTest {

    private static final List<String> CODES =
            List.of("payroll.proof.read", "payroll.proof.review", "payroll.proof.read_own", "payroll.proof.submit_own");

    @BeforeAll
    static void applySchema() throws Exception {
        ProofTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        ProofTestSchema.seedTenants();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The four action codes exist in reference.action")
    void codesExist() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT code FROM reference.action WHERE code LIKE 'payroll.proof.%'");
                ResultSet rs = ps.executeQuery()) {
            Set<String> found = new HashSet<>();
            while (rs.next()) {
                found.add(rs.getString(1));
            }
            assertThat(found).containsExactlyInAnyOrderElementsOf(CODES);
        }
    }

    @Test
    @DisplayName("A tenant that existed before V110 gets the grants from the seed function")
    void existingTenantIsGranted() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM core.role_action WHERE tenant_id = ? AND action_code LIKE 'payroll.proof.%'")) {
                del.setObject(1, TENANT_A);
                del.executeUpdate();
            }
            assertThat(actions(TENANT_A, "employee")).doesNotContain("payroll.proof.read_own");

            try (PreparedStatement ps = conn.prepareStatement("SELECT core.seed_proof_roles(?)")) {
                ps.setObject(1, TENANT_A);
                ps.execute();
            }
        }
        assertGrantsFor(TENANT_A);
    }

    @Test
    @DisplayName("A tenant created after V110 gets the grants from the trigger, with no manual call")
    void newTenantIsGrantedByTrigger() throws SQLException {
        UUID fresh = UUID.randomUUID();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            try {
                try (PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
                    ps.setObject(1, fresh);
                    ps.setString(2, "Proof Trigger Tenant " + fresh);
                    ps.executeUpdate();
                }
                assertGrantsFor(fresh);
            } finally {
                try (PreparedStatement del =
                        conn.prepareStatement("DELETE FROM core.role_action WHERE tenant_id = ?")) {
                    del.setObject(1, fresh);
                    del.executeUpdate();
                }
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM core.role WHERE tenant_id = ?")) {
                    del.setObject(1, fresh);
                    del.executeUpdate();
                }
                // When the shared schema carries V038 (main's payslip notification), its tenant trigger seeds
                // notification templates for every new tenant, and they must go before the tenant can. Whether
                // V038 is there depends on which test class built the schema first, so look before deleting.
                if (tableExists(conn, "core.notification_template")) {
                    try (PreparedStatement del =
                            conn.prepareStatement("DELETE FROM core.notification_template WHERE tenant_id = ?")) {
                        del.setObject(1, fresh);
                        del.executeUpdate();
                    }
                }
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM core.tenant WHERE tenant_id = ?")) {
                    del.setObject(1, fresh);
                    del.executeUpdate();
                }
            }
        }
    }

    @Test
    @DisplayName("Running the seed twice changes nothing, and applying the schema again is safe")
    void idempotent() {
        assertThatCode(() -> {
                    ProofTestSchema.seedTenants();
                    ProofTestSchema.seedTenants();
                    ProofTestSchema.apply();
                })
                .doesNotThrowAnyException();
        assertThat(actions(TENANT_A, "employee")).contains("payroll.proof.read_own");
    }

    private void assertGrantsFor(UUID tenant) {
        assertThat(actions(tenant, "employee"))
                .contains("payroll.proof.read_own", "payroll.proof.submit_own")
                .doesNotContain("payroll.proof.read", "payroll.proof.review");
        assertThat(actions(tenant, "hr")).contains("payroll.proof.read", "payroll.proof.review");
        assertThat(actions(tenant, "payroll-officer"))
                .contains("payroll.proof.read")
                .doesNotContain("payroll.proof.review", "payroll.proof.submit_own");
        assertThat(actions(tenant, "finance")).doesNotContain(CODES.toArray(String[]::new));
        assertThat(actions(tenant, "manager")).doesNotContain(CODES.toArray(String[]::new));
        assertThat(actions(tenant, "platform-admin")).containsAll(CODES);
        assertThat(actions(tenant, "tenant-admin")).containsAll(CODES);
    }

    private static Set<String> actions(UUID tenantId, String roleCode) {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT ra.action_code
                          FROM core.role r
                          JOIN core.role_action ra ON ra.role_id = r.id AND ra.tenant_id = r.tenant_id
                         WHERE r.tenant_id = ? AND r.code = ?
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> actions = new HashSet<>();
                while (rs.next()) {
                    actions.add(rs.getString(1));
                }
                return actions;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean tableExists(Connection conn, String qualifiedName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, qualifiedName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
