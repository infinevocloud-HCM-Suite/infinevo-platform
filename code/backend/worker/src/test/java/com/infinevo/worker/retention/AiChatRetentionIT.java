package com.infinevo.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-114.10: the AI chat retention sweep, run as {@code retention_user} against real Postgres with row-level
 * security on.
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@EnabledIfDockerAvailable
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, RetentionWorkerTestSchema.Initializer.class})
class AiChatRetentionIT extends AbstractIntegrationTest {

    @Autowired
    private AiChatRetentionService retentionService;

    @Autowired
    @Qualifier("retentionJdbcTemplate")
    private JdbcTemplate retentionJdbcTemplate;

    @Test
    @DisplayName("Old chat rows of customer A go, recent ones stay, customer B is untouched, a second run deletes 0")
    void sweepDeletesOldChatsOnly() throws Exception {
        assertThat(retentionJdbcTemplate.queryForObject("SELECT current_user", String.class))
                .as("the sweep connects as retention_user")
                .isEqualTo(PostgresTestContainerInitializer.RETENTION_USER);

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Instant now = Instant.now();
        Instant days31 = now.minus(31, ChronoUnit.DAYS);
        Instant days29 = now.minus(29, ChronoUnit.DAYS);
        Instant days60 = now.minus(60, ChronoUnit.DAYS);
        Instant days1 = now.minus(1, ChronoUnit.DAYS);

        UUID idleSession;
        UUID idleOldMessage;
        UUID idleRecentMessage;
        UUID idleRecentIntent;
        UUID activeSession;
        UUID activeOldMessage;
        UUID activeRecentMessage;
        UUID activeOldIntent;
        UUID activeRecentIntent;
        UUID bSession;
        UUID bOldMessage;
        UUID bOldIntent;

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenantA, "AI Retention Tenant A", 84, 12);
            RetentionWorkerTestSchema.insertTenant(conn, tenantB, "AI Retention Tenant B", 84, 12);

            // Idle 31 days. Its 29-day-old message and intent are younger than the cutoff, so only the session's
            // own deletion, through ON DELETE CASCADE, can remove them.
            idleSession = RetentionWorkerTestSchema.insertAiSession(conn, tenantA, days60, days31);
            idleOldMessage = RetentionWorkerTestSchema.insertAiMessage(conn, tenantA, idleSession, days31);
            idleRecentMessage = RetentionWorkerTestSchema.insertAiMessage(conn, tenantA, idleSession, days29);
            idleRecentIntent = RetentionWorkerTestSchema.insertAiActionIntent(conn, tenantA, idleSession, days29);

            // Active yesterday, but started 60 days ago.
            activeSession = RetentionWorkerTestSchema.insertAiSession(conn, tenantA, days60, days1);
            activeOldMessage = RetentionWorkerTestSchema.insertAiMessage(conn, tenantA, activeSession, days31);
            activeRecentMessage = RetentionWorkerTestSchema.insertAiMessage(conn, tenantA, activeSession, days29);
            activeOldIntent = RetentionWorkerTestSchema.insertAiActionIntent(conn, tenantA, activeSession, days31);
            activeRecentIntent = RetentionWorkerTestSchema.insertAiActionIntent(conn, tenantA, activeSession, days29);

            // Customer B: everything old and idle, so a sweep of B would delete all of it.
            bSession = RetentionWorkerTestSchema.insertAiSession(conn, tenantB, days60, null);
            bOldMessage = RetentionWorkerTestSchema.insertAiMessage(conn, tenantB, bSession, days31);
            bOldIntent = RetentionWorkerTestSchema.insertAiActionIntent(conn, tenantB, bSession, days31);
        }

        List<RetentionRun> first = retentionService.sweepTenant(tenantA, false);

        assertThat(first)
                .extracting(RetentionRun::getTargetTable)
                .containsExactly(
                        AiChatRetentionService.TARGET_AI_MESSAGE,
                        AiChatRetentionService.TARGET_AI_ACTION_INTENT,
                        AiChatRetentionService.TARGET_AI_SESSION);
        assertThat(first).extracting(RetentionRun::getStatus).containsOnly("COMPLETED");
        assertThat(first).extracting(RetentionRun::getRowsDeleted).containsExactly(2L, 1L, 1L);

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_session", idleSession))
                    .as("idle 31-day-old session goes")
                    .isFalse();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", idleOldMessage))
                    .isFalse();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", idleRecentMessage))
                    .as("the idle session's messages go with it")
                    .isFalse();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_action_intent", idleRecentIntent))
                    .as("the idle session's intents go with it")
                    .isFalse();

            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_session", activeSession))
                    .as("an active session stays")
                    .isTrue();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", activeOldMessage))
                    .as("a 31-day-old message goes")
                    .isFalse();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", activeRecentMessage))
                    .as("a 29-day-old message stays")
                    .isTrue();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_action_intent", activeOldIntent))
                    .as("a 31-day-old intent goes, whatever its status")
                    .isFalse();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_action_intent", activeRecentIntent))
                    .isTrue();

            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_session", bSession))
                    .as("customer B untouched")
                    .isTrue();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", bOldMessage))
                    .isTrue();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_action_intent", bOldIntent))
                    .isTrue();

            assertThat(completedRuns(conn, tenantA))
                    .as("one retention_run row per table")
                    .isEqualTo(3);
        }

        List<RetentionRun> second = retentionService.sweepTenant(tenantA, false);
        assertThat(second).extracting(RetentionRun::getRowsDeleted).containsExactly(0L, 0L, 0L);
        assertThat(second).extracting(RetentionRun::getStatus).containsOnly("COMPLETED");
    }

    @Test
    @DisplayName("A dry run counts the old rows and deletes none")
    void dryRunDeletesNothing() throws Exception {
        UUID tenant = UUID.randomUUID();
        Instant days31 = Instant.now().minus(31, ChronoUnit.DAYS);
        UUID session;
        UUID message;
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenant, "AI Retention Dry Run", 84, 12);
            session = RetentionWorkerTestSchema.insertAiSession(conn, tenant, days31, days31);
            message = RetentionWorkerTestSchema.insertAiMessage(conn, tenant, session, days31);
        }

        List<RetentionRun> runs = retentionService.sweepTenant(tenant, true);

        assertThat(runs).extracting(RetentionRun::getRowsDeleted).containsExactly(1L, 0L, 1L);
        assertThat(runs).extracting(RetentionRun::isDryRun).containsOnly(true);
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_session", session))
                    .isTrue();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", message))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("retention_user cannot UPDATE a chat row")
    void retentionUserCannotUpdate() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID session;
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenant, "AI Retention No Update", 84, 12);
            session = RetentionWorkerTestSchema.insertAiSession(conn, tenant, Instant.now(), null);
            RetentionWorkerTestSchema.insertAiMessage(conn, tenant, session, Instant.now());
        }

        for (String sql : new String[] {
            "UPDATE core.ai_message SET content = 'changed' WHERE session_id = ?",
            "UPDATE core.ai_session SET title = 'changed' WHERE id = ?",
            "UPDATE core.ai_action_intent SET status = 'EXPIRED' WHERE session_id = ?"
        }) {
            try (Connection conn = RetentionWorkerTestSchema.retentionConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement bind =
                        conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                    bind.setString(1, tenant.toString());
                    bind.execute();
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, session);
                    assertThatThrownBy(ps::executeUpdate)
                            .as(sql)
                            .isInstanceOf(SQLException.class)
                            .extracting(e -> ((SQLException) e).getSQLState())
                            .isEqualTo("42501");
                }
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Deleting an old message sets message_id NULL on a recent intent it proposed; the run completes")
    void oldMessageDeletionNullsRecentIntentReference() throws Exception {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        UUID session;
        UUID oldMessage;
        UUID intent;
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenant, "AI Retention Message Reference", 84, 12);
            session = RetentionWorkerTestSchema.insertAiSession(
                    conn, tenant, now.minus(60, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
            oldMessage =
                    RetentionWorkerTestSchema.insertAiMessage(conn, tenant, session, now.minus(31, ChronoUnit.DAYS));
            intent = RetentionWorkerTestSchema.insertAiActionIntent(
                    conn, tenant, session, now.minus(29, ChronoUnit.DAYS), oldMessage, "PROPOSED");
        }

        List<RetentionRun> runs = retentionService.sweepTenant(tenant, false);

        assertThat(runs).extracting(RetentionRun::getStatus).containsOnly("COMPLETED");
        assertThat(runs).extracting(RetentionRun::getRowsDeleted).containsExactly(1L, 0L, 0L);
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_message", oldMessage))
                    .as("the 31-day-old message goes")
                    .isFalse();
            assertThat(RetentionWorkerTestSchema.rowExists(conn, "core.ai_action_intent", intent))
                    .as("the 29-day-old intent stays")
                    .isTrue();
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT message_id FROM core.ai_action_intent WHERE id = ?")) {
                ps.setObject(1, intent);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("message_id"))
                            .as("ON DELETE SET NULL applied")
                            .isNull();
                }
            }
        }
    }

    private static int completedRuns(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.retention_run"
                + " WHERE tenant_id = ? AND status = 'COMPLETED' AND NOT is_dry_run"
                + " AND target_table IN ('core.ai_message', 'core.ai_action_intent', 'core.ai_session')")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
