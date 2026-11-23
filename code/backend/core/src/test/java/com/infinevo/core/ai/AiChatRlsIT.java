package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.3 spec, Tests: row-level security on {@code core.ai_session}, {@code core.ai_message} and
 * {@code core.ai_action_intent} (V200), as {@code app_user}. Tenant A's rows are written as the schema owner; tenant
 * B, and a connection with no tenant bound, read none of them in any of the three tables.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AiChatRlsIT extends AbstractIntegrationTest {

    private static final List<String> TABLES = List.of("ai_session", "ai_message", "ai_action_intent");

    @Autowired
    private AiSessionRepository sessions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID sessionA;

    @BeforeEach
    void setUp() throws SQLException {
        tenantA = AuthzTestSchema.insertTenant("AiChat A " + UUID.randomUUID());
        tenantB = AuthzTestSchema.insertTenant("AiChat B " + UUID.randomUUID());
        userA = UUID.randomUUID();
        sessionA = AiChatRows.session(tenantA, userA);
        UUID message = AiChatRows.message(tenantA, sessionA, "ASSISTANT", "Shall I apply for leave?");
        AiChatRows.intent(tenantA, sessionA, userA, message);
    }

    @Test
    @DisplayName("as app_user bound to tenant A: one row in each table (the control)")
    void tenantA_readsOwnRows() throws SQLException {
        for (String table : TABLES) {
            assertThat(countAs(tenantA, table)).as(table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("as app_user bound to tenant B: no row of tenant A in any of the three tables")
    void tenantB_readsNothingOfTenantA() throws SQLException {
        for (String table : TABLES) {
            assertThat(countAs(tenantB, table)).as(table).isZero();
        }
    }

    @Test
    @DisplayName("as app_user with no tenant bound: no row in any of the three tables")
    void noTenant_readsNothing() throws SQLException {
        for (String table : TABLES) {
            assertThat(countAs(null, table)).as(table).isZero();
        }
    }

    @Test
    @DisplayName("through the repository, bound to tenant B, tenant A's session is not found even by its own keys")
    void repository_tenantB_findsNothing() {
        assertThat(findAs(tenantA, tenantA)).isPresent();
        assertThat(findAs(tenantB, tenantA)).isEmpty();
    }

    /** The read runs in a transaction: the tenant binding is transaction-local (see {@code AiUsageIT}). */
    private Optional<AiSession> findAs(UUID bound, UUID rowTenant) {
        TenantContext.set(bound);
        try {
            return new TransactionTemplate(transactionManager)
                    .execute(status -> sessions.findByIdAndTenantIdAndUserId(sessionA, rowTenant, userA));
        } finally {
            TenantContext.clear();
        }
    }

    /** Rows of tenant A in the table, counted as {@code app_user} bound to {@code bound} (none when null). */
    private long countAs(UUID bound, String table) throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            if (bound != null) {
                try (PreparedStatement bind =
                        conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                    bind.setString(1, bound.toString());
                    bind.execute();
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core." + table + " WHERE tenant_id = ?")) {
                ps.setObject(1, tenantA);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
