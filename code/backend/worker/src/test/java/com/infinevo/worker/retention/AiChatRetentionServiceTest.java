package com.infinevo.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** W-114.10: the AI chat retention sweep without a database. */
class AiChatRetentionServiceTest {

    // Late in the day, so a cutoff taken from "now" rather than from midnight would show.
    private static final Instant NOW = Instant.parse("2026-11-25T23:45:00Z");

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private RetentionRunStore runs;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private final List<String> preparedSql = new ArrayList<>();
    private AiChatRetentionService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        runs = mock(RetentionRunStore.class);
        Connection connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            preparedSql.add(invocation.getArgument(0));
            return statement;
        });
        when(statement.executeQuery()).thenReturn(resultSet);
        when(runs.findRunning(any(), anyString(), any(), anyBoolean())).thenReturn(Optional.empty());
        when(runs.insert(any(RetentionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = newService(30);
    }

    private AiChatRetentionService newService(int days) {
        return new AiChatRetentionService(dataSource, jdbcTemplate, runs, days, 500, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("The cutoff is midnight UTC, 30 days back")
    void cutoffIsMidnightUtcThirtyDaysBack() throws Exception {
        assertThat(service.calculateCutoffDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(AiChatRetentionService.cutoffInstant(service.calculateCutoffDate()))
                .isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));

        when(statement.executeUpdate()).thenReturn(0);
        RetentionRun run = service.sweepTarget(UUID.randomUUID(), AiChatRetentionService.TARGET_AI_MESSAGE, false);

        assertThat(run.getCutoffDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        verify(statement).setTimestamp(2, java.sql.Timestamp.from(Instant.parse("2026-10-26T00:00:00Z")));
    }

    @Test
    @DisplayName("The three tables are swept in order: messages, intents, sessions")
    void tablesSweptInOrder() throws Exception {
        when(statement.executeUpdate()).thenReturn(0);

        List<RetentionRun> result = service.sweepTenant(UUID.randomUUID(), false);

        assertThat(result)
                .extracting(RetentionRun::getTargetTable)
                .containsExactly("core.ai_message", "core.ai_action_intent", "core.ai_session");
        assertThat(preparedSql)
                .filteredOn(sql -> sql.startsWith("DELETE"))
                .extracting(sql -> sql.substring(0, sql.indexOf(" WHERE")))
                .containsExactly(
                        "DELETE FROM core.ai_message",
                        "DELETE FROM core.ai_action_intent",
                        "DELETE FROM core.ai_session");
        assertThat(result).extracting(RetentionRun::getStatus).containsOnly("COMPLETED");
    }

    @Test
    @DisplayName("A session is aged by its last message, or by when it was made if it has none")
    void sessionAgedByLastActivity() throws Exception {
        when(statement.executeUpdate()).thenReturn(0);

        service.sweepTarget(UUID.randomUUID(), AiChatRetentionService.TARGET_AI_SESSION, false);

        assertThat(preparedSql).anySatisfy(sql -> assertThat(sql)
                .startsWith("DELETE FROM core.ai_session")
                .contains("COALESCE(last_message_at, created_at) < ?"));
    }

    @Test
    @DisplayName("A failing customer is logged and the next one still runs")
    void failingCustomerDoesNotStopTheNext() {
        UUID failing = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of(failing, next));
        when(runs.insert(argThat(run -> run != null && failing.equals(run.getTenantId()))))
                .thenThrow(new IllegalStateException("database unavailable"));

        List<RetentionRun> result = service.runSweep(false);

        assertThat(result).hasSize(3).extracting(RetentionRun::getTenantId).containsOnly(next);
    }

    @Test
    @DisplayName("A dry run deletes nothing and records the count")
    void dryRunDeletesNothingAndRecordsTheCount() throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(17L);

        RetentionRun run = service.sweepTarget(UUID.randomUUID(), AiChatRetentionService.TARGET_AI_MESSAGE, true);

        verify(statement, never()).executeUpdate();
        assertThat(run.isDryRun()).isTrue();
        assertThat(run.getRowsDeleted()).isEqualTo(17L);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<RetentionRun> recorded = ArgumentCaptor.forClass(RetentionRun.class);
        verify(runs).update(recorded.capture(), any(Instant.class));
        assertThat(recorded.getValue().getRowsDeleted()).isEqualTo(17L);
        assertThat(preparedSql).noneMatch(sql -> sql.startsWith("DELETE"));
    }

    @Test
    @DisplayName("Batches repeat until one comes back short")
    void batchesRepeatUntilShort() throws Exception {
        when(statement.executeUpdate()).thenReturn(500, 500, 3);

        RetentionRun run =
                service.sweepTarget(UUID.randomUUID(), AiChatRetentionService.TARGET_AI_ACTION_INTENT, false);

        assertThat(run.getRowsDeleted()).isEqualTo(1003L);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("days below 1 is refused")
    void daysBelowOneRefused() {
        assertThatThrownBy(() -> newService(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newService(-30)).isInstanceOf(IllegalArgumentException.class);
        assertThat(newService(1).getRetentionDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("A table outside the three is refused")
    void unknownTableRefused() {
        assertThatThrownBy(() -> service.sweepTarget(UUID.randomUUID(), "core.audit_log", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
