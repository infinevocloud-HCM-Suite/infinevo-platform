package com.infinevo.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditRetentionServiceTest {

    private DataSource mockDataSource;
    private JdbcTemplate mockJdbcTemplate;
    private RetentionRunStore mockRuns;
    private Connection mockConnection;
    private PreparedStatement mockPreparedStatement;
    private ResultSet mockResultSet;
    private Clock fixedClock;
    private AuditRetentionService service;

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        mockDataSource = mock(DataSource.class);
        mockJdbcTemplate = mock(JdbcTemplate.class);
        mockRuns = mock(RetentionRunStore.class);
        mockConnection = mock(Connection.class);
        mockPreparedStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        when(mockRuns.insert(any(RetentionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new AuditRetentionService(mockDataSource, mockJdbcTemplate, mockRuns, 500, fixedClock);
    }

    @Test
    @DisplayName("Cutoff arithmetic for default 84 months (7 years) is exact")
    void cutoffArithmeticDefault84Months() {
        LocalDate cutoff = service.calculateCutoffDate(84);
        assertThat(cutoff).isEqualTo(LocalDate.of(2019, 9, 27));
    }

    @Test
    @DisplayName("Tenant override for retention window is honoured")
    void tenantOverrideHonoured() {
        LocalDate cutoff24 = service.calculateCutoffDate(24);
        assertThat(cutoff24).isEqualTo(LocalDate.of(2024, 9, 27));

        LocalDate cutoff120 = service.calculateCutoffDate(120);
        assertThat(cutoff120).isEqualTo(LocalDate.of(2016, 9, 27));
    }

    @Test
    @DisplayName("Audit retention window floor of 12 months is enforced against under-retention")
    void auditRetentionFloorEnforced() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(mockRuns.findRunning(
                        eq(tenantId), eq(AuditRetentionService.TARGET_AUDIT_LOG), any(LocalDate.class), eq(true)))
                .thenReturn(Optional.empty());
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(0L);

        // Requested 3 months (< 12-month statutory floor)
        RetentionRun run = service.sweepTarget(tenantId, AuditRetentionService.TARGET_AUDIT_LOG, 3, true);

        // Cutoff must be clamped to 12 months (2025-09-27), not 3 months (2026-06-27)
        assertThat(run.getCutoffDate()).isEqualTo(LocalDate.of(2025, 9, 27));
    }

    @Test
    @DisplayName("A dry run counts matches and deletes nothing")
    void dryRunDeletesNothing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(mockRuns.findRunning(
                        eq(tenantId), eq(AuditRetentionService.TARGET_AUDIT_LOG), any(LocalDate.class), eq(true)))
                .thenReturn(Optional.empty());
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(42L);

        RetentionRun run = service.sweepTarget(tenantId, AuditRetentionService.TARGET_AUDIT_LOG, 84, true);

        assertThat(run.isDryRun()).isTrue();
        assertThat(run.getRowsDeleted()).isEqualTo(42L);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");

        // Verified that executeUpdate() (i.e. DELETE) was never invoked
        verify(mockPreparedStatement, never()).executeUpdate();
        verify(mockPreparedStatement).executeQuery();
    }

    @Test
    @DisplayName("A dry run looks only for an unfinished dry run - it never takes over an interrupted real run")
    void dryRunNeverResumesARealRun() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong(1)).thenReturn(7L);

        service.sweepTarget(tenantId, AuditRetentionService.TARGET_AUDIT_LOG, 84, true);

        verify(mockRuns)
                .findRunning(eq(tenantId), eq(AuditRetentionService.TARGET_AUDIT_LOG), any(LocalDate.class), eq(true));
        verify(mockRuns, never())
                .findRunning(eq(tenantId), eq(AuditRetentionService.TARGET_AUDIT_LOG), any(LocalDate.class), eq(false));
    }
}
