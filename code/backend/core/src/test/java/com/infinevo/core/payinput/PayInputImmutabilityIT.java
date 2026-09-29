package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-19 §7 — {@code app_user} holds no {@code UPDATE} or {@code DELETE} on either table
 * ({@code V031}, {@code V032}): a permission failure ({@code 42501}), not a row-count-zero UPDATE
 * that would be indistinguishable from RLS hiding the row.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputImmutabilityIT extends AbstractIntegrationTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID payInputId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("Immutable " + UUID.randomUUID());
        UUID employee = PayInputTestSchema.insertEmployee(tenant, "IMM-" + UUID.randomUUID());
        TenantContext.set(tenant);
        payInputId = payInputService
                .record(new PayInputCommand(
                        employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "imm-1"))
                .id();
        payInputService.lock(YearMonth.of(2026, 5));
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("app_user cannot UPDATE core.pay_input")
    void appUserCannotUpdatePayInput() throws SQLException {
        assertPermissionDenied(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE core.pay_input SET quantity = 2 WHERE id = ?")) {
                ps.setObject(1, payInputId);
                ps.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("app_user cannot DELETE from core.pay_input")
    void appUserCannotDeletePayInput() throws SQLException {
        assertPermissionDenied(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.pay_input WHERE id = ?")) {
                ps.setObject(1, payInputId);
                ps.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("app_user cannot UPDATE core.pay_input_period_lock")
    void appUserCannotUpdateLock() throws SQLException {
        assertPermissionDenied(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE core.pay_input_period_lock SET locked_by = 'someone else' WHERE tenant_id = ?")) {
                ps.setObject(1, tenant);
                ps.executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("app_user cannot DELETE from core.pay_input_period_lock")
    void appUserCannotDeleteLock() throws SQLException {
        assertPermissionDenied(conn -> {
            try (PreparedStatement ps =
                    conn.prepareStatement("DELETE FROM core.pay_input_period_lock WHERE tenant_id = ?")) {
                ps.setObject(1, tenant);
                ps.executeUpdate();
            }
        });
    }

    private void assertPermissionDenied(SqlAction action) throws SQLException {
        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            SQLException thrown = null;
            try {
                action.run(conn);
            } catch (SQLException e) {
                thrown = e;
            }
            assertThat((Throwable) thrown)
                    .as("expected a permission-denied SQLException")
                    .isNotNull();
            assertThat(thrown.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection conn) throws SQLException;
    }
}
