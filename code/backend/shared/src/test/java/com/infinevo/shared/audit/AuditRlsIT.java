package com.infinevo.shared.audit;

import static com.infinevo.shared.audit.AuditTestSchema.TENANT_A;
import static com.infinevo.shared.audit.AuditTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infinevo.shared.audit.AuditQueryService.AuditLogView;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * W-22.1 — tenant A cannot read tenant B's audit rows, and {@code app_user} is refused
 * {@code UPDATE} and {@code DELETE} on {@code core.audit_log} (spec section 7).
 *
 * <p>The grant half is the point of the table: an audit row cannot be edited by the application
 * that wrote it — {@code V008__audit_log.sql:44}.
 */
@SpringBootTest(classes = AuditTestApp.class)
class AuditRlsIT extends AbstractIntegrationTest {

    @Autowired
    private AuditQueryService queryService;

    @BeforeAll
    static void applySchema() throws Exception {
        AuditTestSchema.apply();
    }

    @BeforeEach
    void reset() throws Exception {
        TenantContext.clear();
        AuditTestSchema.seedTenants();
        AuditTestSchema.clearAll();
        seedAuditRow(TENANT_A, "tenant A change");
        seedAuditRow(TENANT_B, "tenant B change");
    }

    private static void seedAuditRow(UUID tenantId, String label) throws SQLException {
        try (Connection conn = AuditTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.audit_log
                            (tenant_id, actor_label, operation, entity_schema, entity_table, entity_id, new_values)
                        VALUES (?, ?, 'INSERT', 'core', 'audited_probe', ?, '{"name":"x"}'::jsonb)
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, label);
            ps.setString(3, UUID.randomUUID().toString());
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Tenant A cannot read tenant B's audit rows through the query service")
    void tenantACannotReadTenantBThroughTheService() {
        TenantContext.set(TENANT_A);
        try {
            List<AuditLogView> rows = queryService
                    .search(null, null, null, null, null, PageRequest.of(0, 50))
                    .getContent();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).actorLabel()).isEqualTo("tenant A change");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Row-level security alone hides tenant B's rows, even on a raw app_user connection")
    void rlsHidesOtherTenantsRowsOnARawConnection() throws SQLException {
        assertThat(countAllRows(TENANT_A)).isEqualTo(1);
        assertThat(countAllRows(TENANT_B)).isEqualTo(1);
        // Bound to A, asking for everything: B's row is not there to be seen.
        try (Connection conn = AuditTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, TENANT_A.toString());
                bind.execute();
            }
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.audit_log")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            conn.commit();
        }
    }

    @Test
    @DisplayName("app_user is refused UPDATE on core.audit_log")
    void appUserRefusedUpdate() {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = AuditTestSchema.appConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE core.audit_log SET actor_label = 'tampered'");
            }
        });
        assertThat(ex.getSQLState()).isEqualTo("42501");
    }

    @Test
    @DisplayName("app_user is refused DELETE on core.audit_log")
    void appUserRefusedDelete() {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = AuditTestSchema.appConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM core.audit_log");
            }
        });
        assertThat(ex.getSQLState()).isEqualTo("42501");
    }

    @Test
    @DisplayName("app_user may still INSERT and SELECT: the table is append-only, not read-only")
    void appUserMayInsertAndSelect() throws SQLException {
        try (Connection conn = AuditTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, TENANT_A.toString());
                bind.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.audit_log
                        (tenant_id, actor_label, operation, entity_schema, entity_table, entity_id)
                    VALUES (?, 'system', 'INSERT', 'core', 'audited_probe', 'x')
                    """)) {
                ps.setObject(1, TENANT_A);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            conn.commit();
        }
        assertThat(countAllRows(TENANT_A)).isEqualTo(2);
    }

    /** Counts as the schema owner, which RLS does not constrain - the control for the assertions above. */
    private static int countAllRows(UUID tenantId) throws SQLException {
        return AuditTestSchema.countAuditRows(tenantId);
    }
}
