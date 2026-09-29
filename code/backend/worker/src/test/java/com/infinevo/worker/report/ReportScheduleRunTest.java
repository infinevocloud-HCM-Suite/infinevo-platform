package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.PermissionReadService;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.core.report.ExportService;
import com.infinevo.core.report.ReportDefinition;
import com.infinevo.core.report.ReportDefinitionRepository;
import com.infinevo.core.report.ReportSchedule;
import com.infinevo.core.report.ReportScheduleRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-23.2 — one due schedule's run: claimed before anything is produced, exported without a caller
 * check, linked for seven days, composed through W-20.1 per recipient, and its outcome recorded.
 */
class ReportScheduleRunTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-28T09:30:00Z");

    private static final String REQUIRED_ACTION = "core.employee.export";

    private ReportScheduleRepository schedules;
    private ReportDefinitionRepository definitions;
    private ExportService exportService;
    private DocumentLinkService linkService;
    private NotificationService notificationService;
    private PermissionReadService permissionReadService;
    private ReportScheduleEvaluator evaluator;
    private ReportSchedule schedule;
    private UUID ownerId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        schedules = mock(ReportScheduleRepository.class);
        definitions = mock(ReportDefinitionRepository.class);
        exportService = mock(ExportService.class);
        linkService = mock(DocumentLinkService.class);
        notificationService = mock(NotificationService.class);
        permissionReadService = mock(PermissionReadService.class);
        evaluator = new ReportScheduleEvaluator(
                mock(JdbcTemplate.class),
                schedules,
                definitions,
                exportService,
                linkService,
                notificationService,
                permissionReadService,
                mock(PlatformTransactionManager.class),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        UUID definitionId = UUID.randomUUID();
        schedule = new ReportSchedule(
                TENANT,
                definitionId,
                ReportSchedule.DAILY,
                null,
                LocalTime.of(9, 0),
                "{\"status\":\"ACTIVE\"}",
                "finance@acme.test, audit@outside.test",
                true,
                "test");
        ownerId = UUID.randomUUID();
        schedule.setOwnerUserAccountId(ownerId);
        ReportDefinition definition = mock(ReportDefinition.class);
        when(definition.getName()).thenReturn("Employees");
        when(definition.getRequiredAction()).thenReturn(REQUIRED_ACTION);
        when(definitions.findByIdAndTenantId(definitionId, TENANT)).thenReturn(Optional.of(definition));
        when(schedules.findByIdAndTenantId(schedule.getId(), TENANT)).thenReturn(Optional.of(schedule));
        when(permissionReadService.actionsOf(ownerId)).thenReturn(Set.of(REQUIRED_ACTION));

        documentId = UUID.randomUUID();
        when(exportService.exportForJob(eq(definitionId), anyMap()))
                .thenReturn(new ExportService.ExportResponse(
                        documentId, "employees.xlsx", ExportFormat.XLSX, 3, "/interactive", NOW.plusSeconds(900)));
        when(linkService.signedLink(documentId, ReportScheduleEvaluator.LINK_TTL))
                .thenReturn(new DocumentLinkService.SignedLink(
                        "/api/v1/documents/download?t=seven-days", NOW.plus(ReportScheduleEvaluator.LINK_TTL)));
        when(notificationService.compose(eq(NotificationEvent.SCHEDULED_REPORT), isNull(), anyMap()))
                .thenReturn(List.of(UUID.randomUUID()));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Already claimed for this period: nothing is exported, sent or recorded")
    void unclaimedScheduleDoesNothing() {
        when(schedules.claim(schedule.getId(), TENANT, null, NOW)).thenReturn(0);

        evaluator.executeSchedule(schedule);

        verify(exportService, never()).exportForJob(any(), any());
        verify(notificationService, never()).compose(any(), any(), any());
        verify(schedules, never()).save(any());
    }

    @Test
    @DisplayName("A run: exportForJob, a seven-day link, one SCHEDULED_REPORT per recipient, then SUCCESS")
    void runExportsLinksNotifiesAndRecords() {
        when(schedules.claim(schedule.getId(), TENANT, null, NOW)).thenReturn(1);

        evaluator.executeSchedule(schedule);

        verify(exportService).exportForJob(any(), eq(Map.of("status", "ACTIVE")));
        verify(linkService).signedLink(documentId, ReportScheduleEvaluator.LINK_TTL);
        verify(notificationService, times(2))
                .compose(
                        eq(NotificationEvent.SCHEDULED_REPORT),
                        isNull(),
                        argThat(data -> "Employees".equals(data.get("report_name"))
                                && "/api/v1/documents/download?t=seven-days".equals(data.get("link"))
                                && data.containsKey(NotificationService.RECIPIENT_EMAIL)));
        verify(schedules)
                .save(argThat(s -> ReportSchedule.STATUS_SUCCESS.equals(s.getLastRunStatus())
                        && documentId.equals(s.getLastDocumentId())
                        && NOW.equals(s.getLastRunAt())));
    }

    @Test
    @DisplayName("B-4: the owner no longer holding the definition's required_action refuses the run before exporting")
    void ownerLosingTheRequiredActionRefusesTheRun() {
        when(schedules.claim(schedule.getId(), TENANT, null, NOW)).thenReturn(1);
        when(permissionReadService.actionsOf(ownerId))
                .thenReturn(Set.of("core.employee.read")); // not the export action

        evaluator.executeSchedule(schedule);

        verify(exportService, never()).exportForJob(any(), any());
        verify(notificationService, never()).compose(any(), any(), any());
        verify(schedules).save(argThat(s -> ReportSchedule.STATUS_FAILED.equals(s.getLastRunStatus())));
    }

    @Test
    @DisplayName("B-4: a schedule with no owner on record is refused, not run as if nobody needed checking")
    void noOwnerOnRecordRefusesTheRun() {
        schedule.setOwnerUserAccountId(null);
        when(schedules.claim(schedule.getId(), TENANT, null, NOW)).thenReturn(1);

        evaluator.executeSchedule(schedule);

        verify(exportService, never()).exportForJob(any(), any());
        verify(notificationService, never()).compose(any(), any(), any());
        verify(schedules).save(argThat(s -> ReportSchedule.STATUS_FAILED.equals(s.getLastRunStatus())));
    }

    @Test
    @DisplayName("An export that fails is recorded FAILED, and nobody is emailed")
    void failedExportIsRecorded() {
        when(schedules.claim(schedule.getId(), TENANT, null, NOW)).thenReturn(1);
        when(exportService.exportForJob(any(), anyMap())).thenThrow(new IllegalStateException("source down"));

        evaluator.executeSchedule(schedule);

        verify(notificationService, never()).compose(any(), any(), any());
        verify(schedules).save(argThat(s -> ReportSchedule.STATUS_FAILED.equals(s.getLastRunStatus())));
    }

    @Test
    @DisplayName("One recipient that cannot be notified does not stop the others; the run is recorded FAILED")
    void oneFailedRecipientDoesNotStopTheRest() {
        when(schedules.claim(schedule.getId(), TENANT, null, NOW)).thenReturn(1);
        when(notificationService.compose(
                        eq(NotificationEvent.SCHEDULED_REPORT), isNull(), argThat(data -> "finance@acme.test"
                                .equals(data.get(NotificationService.RECIPIENT_EMAIL)))))
                .thenThrow(new IllegalStateException("refused"));

        evaluator.executeSchedule(schedule);

        verify(notificationService, times(2)).compose(eq(NotificationEvent.SCHEDULED_REPORT), isNull(), anyMap());
        verify(schedules)
                .save(argThat(s -> ReportSchedule.STATUS_FAILED.equals(s.getLastRunStatus())
                        && documentId.equals(s.getLastDocumentId())));
        assertThat(schedule.getLastDocumentId()).isEqualTo(documentId);
    }
}
