package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-20.1 spec section 7 — tenant A cannot read tenant B's notifications or templates as
 * {@code app_user}; a notification cannot be addressed across tenants; {@code app_user} cannot delete
 * one.
 */
@SpringBootTest(classes = NotificationTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            NotificationTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class NotificationRlsIT extends AbstractIntegrationTest {

    private static final Map<String, Object> REMINDER = Map.of("employee_name", "X", "week_start", "2026-09-21");

    @Autowired
    private NotificationService notificationService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID employeeB;

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        tenantA = NotificationTestSchema.insertTenant("A " + UUID.randomUUID());
        tenantB = NotificationTestSchema.insertTenant("B " + UUID.randomUUID());
        employeeA = NotificationTestSchema.insertEmployee(tenantA, "A-" + UUID.randomUUID());
        employeeB = NotificationTestSchema.insertEmployee(tenantB, "B-" + UUID.randomUUID());
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Row-level security alone hides tenant B's notifications and templates from app_user bound to A")
    void policyHidesTheOtherTenant() throws SQLException {
        TenantContext.set(tenantB);
        List<UUID> ofB = notificationService.compose(NotificationEvent.TIMESHEET_REMINDER, employeeB, REMINDER);
        TenantContext.set(tenantA);
        List<UUID> ofA = notificationService.compose(NotificationEvent.TIMESHEET_REMINDER, employeeA, REMINDER);
        TenantContext.clear();

        assertThat(countAsAppUser(tenantA, "SELECT count(*) FROM core.notification WHERE id = ?", ofA.get(0)))
                .as("the control: A sees its own")
                .isEqualTo(1);
        assertThat(countAsAppUser(tenantA, "SELECT count(*) FROM core.notification WHERE id = ?", ofB.get(0)))
                .as("A must not see B's")
                .isZero();
        assertThat(countAsAppUser(
                        tenantA, "SELECT count(*) FROM core.notification_template WHERE tenant_id = ?", tenantB))
                .as("A must not see B's templates")
                .isZero();
        assertThat(countAsAppUser(
                        tenantA, "SELECT count(*) FROM core.notification_template WHERE tenant_id = ?", tenantA))
                .as("the control: A's thirty-two seeded defaults")
                .isEqualTo(NotificationEvent.values().length * Channel.values().length);
    }

    @Test
    @DisplayName("Bound to A, a notification cannot be addressed to B's employee - the FK alone would allow it")
    void crossTenantRecipientIsRefused() throws SQLException {
        TenantContext.set(tenantA);

        assertThatThrownBy(() -> notificationService.compose(NotificationEvent.TIMESHEET_REMINDER, employeeB, REMINDER))
                .isInstanceOf(NotificationService.ValidationException.class);
        assertThat(NotificationTestSchema.count(
                        "SELECT count(*) FROM core.notification WHERE recipient_employee_id = ?", employeeB))
                .isZero();
    }

    @Test
    @DisplayName("app_user cannot DELETE a notification, even its own tenant's")
    void appUserCannotDelete() throws SQLException {
        TenantContext.set(tenantA);
        UUID id = notificationService
                .compose(NotificationEvent.TIMESHEET_REMINDER, employeeA, REMINDER)
                .get(0);
        TenantContext.clear();

        try (Connection conn = NotificationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            NotificationTestSchema.bindTenant(conn, tenantA);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.notification WHERE id = ?")) {
                ps.setObject(1, id);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            } finally {
                conn.rollback();
            }
        }
    }

    private static long countAsAppUser(UUID boundTenant, String sql, UUID param) throws SQLException {
        try (Connection conn = NotificationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                NotificationTestSchema.bindTenant(conn, boundTenant);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, param);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
