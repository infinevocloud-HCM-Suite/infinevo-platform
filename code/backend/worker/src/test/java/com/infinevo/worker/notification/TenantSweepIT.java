package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.worker.InfinevoWorkerApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-20.2 §7 — Integration test for the multi-tenant sweep function:
 * <ul>
 *   <li>{@code core.list_tenants_for_sweep()} returns every tenant to {@code worker_user}</li>
 *   <li>A direct {@code SELECT FROM core.tenant} with no tenant bound returns none under RLS</li>
 *   <li>{@code app_user} cannot execute the function (SQL State 42501 permission denied)</li>
 * </ul>
 */
@SpringBootTest(classes = InfinevoWorkerApplication.class)
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, NotificationWorkerTestSchema.Initializer.class})
class TenantSweepIT extends AbstractIntegrationTest {

    private UUID tenantA;
    private UUID tenantB;

    @BeforeAll
    static void initSchema() {
        NotificationWorkerTestSchema.jdbcUrl();
    }

    @BeforeEach
    void seedTenants() throws SQLException {
        TenantContext.clear();
        tenantA = NotificationWorkerTestSchema.insertTenant("Sweep Tenant A " + UUID.randomUUID(), "Asia/Kolkata");
        tenantB = NotificationWorkerTestSchema.insertTenant("Sweep Tenant B " + UUID.randomUUID(), "UTC");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("core.list_tenants_for_sweep() returns every tenant to worker_user")
    void workerUserCanEnumerateAllTenantsViaSweepFunction() throws SQLException {
        try (Connection conn = NotificationWorkerTestSchema.workerConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT tenant_id, timezone FROM core.list_tenants_for_sweep()");
                ResultSet rs = ps.executeQuery()) {

            List<UUID> returnedTenantIds = new ArrayList<>();
            while (rs.next()) {
                returnedTenantIds.add(rs.getObject("tenant_id", UUID.class));
            }

            assertThat(returnedTenantIds).contains(tenantA, tenantB);
        }
    }

    @Test
    @DisplayName("Direct SELECT FROM core.tenant with no tenant bound returns 0 rows under RLS")
    void directQueryWithoutTenantContextReturnsEmptyUnderRls() throws SQLException {
        try (Connection conn = NotificationWorkerTestSchema.workerConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.tenant");
                ResultSet rs = ps.executeQuery()) {

            rs.next();
            long count = rs.getLong(1);
            assertThat(count)
                    .as("RLS tenant_isolation must hide all rows when app.current_tenant_id is unbound")
                    .isZero();
        }
    }

    @Test
    @DisplayName("app_user cannot execute core.list_tenants_for_sweep() (permission denied 42501)")
    void appUserCannotExecuteSweepFunction() {
        assertThatThrownBy(() -> {
                    try (Connection conn = NotificationWorkerTestSchema.appConnection();
                            PreparedStatement ps =
                                    conn.prepareStatement("SELECT * FROM core.list_tenants_for_sweep()")) {
                        ps.executeQuery();
                    }
                })
                .isInstanceOf(SQLException.class)
                .satisfies(ex -> {
                    SQLException sqlEx = (SQLException) ex;
                    assertThat(sqlEx.getSQLState()).isEqualTo("42501");
                });
    }
}
