package com.infinevo.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving retention_user tenant listing security boundaries (W-22.2).
 *
 * <p>Proves that:
 * <ul>
 *   <li>{@code retention_user} can execute {@code core.list_tenants_for_sweep()} and read both
 *       {@code audit_retention_months} and {@code notification_retention_months}</li>
 *   <li>A direct {@code SELECT FROM core.tenant} as {@code retention_user} is refused with 42501</li>
 * </ul>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@EnabledIfDockerAvailable
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, RetentionWorkerTestSchema.Initializer.class})
class RetentionTenantListIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("retention_user can execute core.list_tenants_for_sweep() and reads both window columns")
    void retentionUserExecutesListTenantsForSweep() throws Exception {
        UUID testTenantId = UUID.randomUUID();
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, testTenantId, "Tenant Sweep Test", 84, 12);
        }

        try (Connection conn = RetentionWorkerTestSchema.retentionConnection();
                Statement st = conn.createStatement();
                ResultSet rs =
                        st.executeQuery("SELECT tenant_id, audit_retention_months, notification_retention_months "
                                + "FROM core.list_tenants_for_sweep() WHERE tenant_id = '" + testTenantId + "'")) {

            assertThat(rs.next())
                    .as("list_tenants_for_sweep() must return the inserted tenant")
                    .isTrue();
            assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(testTenantId);
            assertThat(rs.getInt("audit_retention_months")).isEqualTo(84);
            assertThat(rs.getInt("notification_retention_months")).isEqualTo(12);
        }
    }

    @Test
    @DisplayName("A direct SELECT FROM core.tenant as retention_user is refused with 42501")
    void directSelectFromCoreTenantRefused() {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = RetentionWorkerTestSchema.retentionConnection();
                    Statement st = conn.createStatement()) {
                st.executeQuery("SELECT * FROM core.tenant");
            }
        });
        assertEquals("42501", ex.getSQLState(), "SQLState must be 42501 (insufficient_privilege)");
    }
}
