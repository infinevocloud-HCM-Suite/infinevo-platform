package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying append-only posture of leave consumption and LOP tables (W-16.4a, spec section 7 & 8).
 * App user must be refused UPDATE and DELETE.
 */
@SpringBootTest(classes = LeaveTestApp.class)
@org.springframework.test.context.ContextConfiguration(
        initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class LeaveConsumptionImmutabilityIT extends AbstractIntegrationTest {

    @BeforeAll
    static void setup() throws Exception {
        LeaveTestSchema.apply();
        LeaveTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanup() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @Test
    @DisplayName("app_user is refused UPDATE on core.leave_consumption")
    void appUserRefusedUpdateOnConsumption() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (Statement stmt = conn.createStatement()) {
                assertThatThrownBy(() -> stmt.executeUpdate("UPDATE core.leave_consumption SET consumed_days = 99"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @DisplayName("app_user is refused DELETE on core.leave_consumption")
    void appUserRefusedDeleteOnConsumption() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (Statement stmt = conn.createStatement()) {
                assertThatThrownBy(() -> stmt.executeUpdate("DELETE FROM core.leave_consumption"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @DisplayName("app_user is refused UPDATE on core.leave_monthly_lop")
    void appUserRefusedUpdateOnMonthlyLop() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (Statement stmt = conn.createStatement()) {
                assertThatThrownBy(() -> stmt.executeUpdate("UPDATE core.leave_monthly_lop SET lop_days = 99"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @DisplayName("app_user is refused DELETE on core.leave_monthly_lop")
    void appUserRefusedDeleteOnMonthlyLop() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (Statement stmt = conn.createStatement()) {
                assertThatThrownBy(() -> stmt.executeUpdate("DELETE FROM core.leave_monthly_lop"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }
}
