package com.infinevo.core.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-39.2 §7 — row-level security on {@code core.overtime_request}, both through the API
 * ({@link OvertimeService}, bound by {@link TenantContext}) and through the repository's own
 * connection, as {@code app_user}.
 */
@SpringBootTest(classes = OvertimeTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            OvertimeTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class OvertimeRlsIT extends AbstractIntegrationTest {

    @Autowired
    private OvertimeService overtimeService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID entryIdOfA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = OvertimeTestSchema.insertTenant("RLS A " + UUID.randomUUID());
        tenantB = OvertimeTestSchema.insertTenant("RLS B " + UUID.randomUUID());
        employeeA = OvertimeTestSchema.insertEmployee(tenantA, "OT-RLS-A-" + UUID.randomUUID());

        TenantContext.set(tenantA);
        entryIdOfA = overtimeService
                .record(new OvertimeEntry(employeeA, LocalDate.of(2026, 4, 10), BigDecimal.ONE, null, null))
                .id();
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Through the API: tenant B's list never contains tenant A's entry; tenant A's does")
    void apiListNeverCrossesTenants() {
        TenantContext.set(tenantB);
        var fromB = overtimeService.list(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);
        TenantContext.clear();
        assertThat(fromB).isEmpty();

        TenantContext.set(tenantA);
        var fromA = overtimeService.list(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);
        TenantContext.clear();
        assertThat(fromA).extracting(OvertimeResponse::id).containsExactly(entryIdOfA);
    }

    @Test
    @DisplayName("Through the API: tenant B cannot cancel tenant A's entry — it is simply not found")
    void apiCancelNeverCrossesTenants() {
        TenantContext.set(tenantB);
        try {
            assertThatThrownBy(() -> overtimeService.cancel(entryIdOfA))
                    .isInstanceOf(OvertimeService.NotFoundException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Through the repository's own connection: tenant B cannot read tenant A's row; tenant A can")
    void repositoryConnectionRespectsRls() throws SQLException {
        try (Connection conn = OvertimeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            OvertimeTestSchema.bindTenant(conn, tenantB);
            assertThat(countRow(conn, entryIdOfA)).isZero();
        }
        try (Connection conn = OvertimeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            OvertimeTestSchema.bindTenant(conn, tenantA);
            assertThat(countRow(conn, entryIdOfA)).isEqualTo(1);
        }
    }

    private static long countRow(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.overtime_request WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
