package com.infinevo.core.employeeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.org.DepartmentRepository;
import com.infinevo.core.org.DesignationRepository;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** W-73.7: queueing refusals, the job's input never leaking into the history, the run's actor and summary. */
class EmployeeImportServiceTest {

    private static final String HEADER = String.join(",", EmployeeImportParser.COLUMNS) + "\n";
    private static final String GOOD = "E1,Asha,,asha@x.test,,2026-04-01,,,,N,\n";
    private static final String BAD = "E2,,,,,2026-04-01,,,,N,\n";

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final InvitationService invitationService = mock(InvitationService.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final JobService jobService = mock(JobService.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final QueueProducer producer = mock(QueueProducer.class);
    private final AtomicReference<QueueProducer> configuredProducer = new AtomicReference<>(producer);

    private EmployeeImportServiceImpl service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<QueueProducer> producers = mock(ObjectProvider.class);
        when(producers.getIfAvailable()).thenAnswer(i -> configuredProducer.get());
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new EmployeeImportServiceImpl(
                employeeService,
                invitationService,
                employeeRepository,
                mock(DepartmentRepository.class),
                mock(DesignationRepository.class),
                mock(WorkLocationRepository.class),
                mock(RoleRepository.class),
                jobService,
                documentService,
                producers,
                tx);
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a file with errors is refused unless validOnly; a file with no clean row always is")
    void enqueueRefusals() {
        String mixed = HEADER + GOOD + BAD;
        assertThatThrownBy(() -> service.enqueueImport(mixed, EmployeeImportParser.parse(mixed), false, actor))
                .isInstanceOf(EmployeeImportFileException.class)
                .hasMessageContaining("1 of 2 rows have errors");

        String allBad = HEADER + BAD;
        assertThatThrownBy(() -> service.enqueueImport(allBad, EmployeeImportParser.parse(allBad), true, actor))
                .isInstanceOf(EmployeeImportFileException.class)
                .hasMessageContaining("No row can be imported");
        verify(jobService, never()).createJob(anyString(), any(), anyString(), anyString());

        String jobId = service.enqueueImport(mixed, EmployeeImportParser.parse(mixed), true, actor);
        verify(jobService).createJob(eq(jobId), eq(tenant), eq("import"), anyString());
    }

    @Test
    @DisplayName("the job row holds the file; the queue message carries only the kind")
    void queuedMessageIsSmall() throws Exception {
        String csv = HEADER + GOOD;
        String jobId = service.enqueueImport(csv, EmployeeImportParser.parse(csv), false, actor);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(jobService).createJob(eq(jobId), eq(tenant), eq("import"), stored.capture());
        JsonNode input = new ObjectMapper().readTree(stored.getValue());
        assertThat(input.get("kind").asText()).isEqualTo("IMPORT");
        assertThat(input.get("csv").asText()).isEqualTo(csv);
        assertThat(input.get("actorUserId").asText()).isEqualTo(actor.toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueueMessage<String>> sent = ArgumentCaptor.forClass(QueueMessage.class);
        verify(producer).send(eq("import"), sent.capture());
        assertThat(sent.getValue().getJobId()).isEqualTo(jobId);
        assertThat(sent.getValue().getPayload()).isEqualTo("IMPORT");
    }

    @Test
    @DisplayName("no queue: 503 before any job row; a send that fails marks the job FAILED")
    void noQueue() {
        configuredProducer.set(null);
        assertThatThrownBy(() -> service.enqueueInviteAll(actor))
                .isInstanceOf(EmployeeImportService.ImportUnavailableException.class);
        verify(jobService, never()).createJob(anyString(), any(), anyString(), anyString());

        configuredProducer.set(producer);
        doThrow(new IllegalStateException("down")).when(producer).send(anyString(), any());
        assertThatThrownBy(() -> service.enqueueInviteAll(actor))
                .isInstanceOf(EmployeeImportService.ImportUnavailableException.class);
        verify(jobService).markFailed(anyString(), eq("The job could not be queued"));
    }

    @Test
    @DisplayName("no actor: refused before anything is queued")
    void actorRequired() {
        assertThatThrownBy(() -> service.enqueueInviteAll(null)).isInstanceOf(IllegalStateException.class);
        verify(jobService, never()).createJob(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("history: a queued job shows its kind but never its file; a completed one shows its counts")
    void historyNeverEchoesTheFile() {
        Instant now = Instant.now();
        JobStatusResponseDTO queued = new JobStatusResponseDTO(
                "j1",
                "import",
                JobState.QUEUED,
                0,
                "{\"kind\":\"IMPORT\",\"actorUserId\":\"" + actor + "\",\"csv\":\"secret,row\"}",
                null,
                now,
                now);
        UUID doc = UUID.randomUUID();
        JobStatusResponseDTO done = new JobStatusResponseDTO(
                "j2",
                "import",
                JobState.COMPLETED,
                100,
                "{\"kind\":\"INVITE_ALL\",\"totalCount\":3,\"createdCount\":0,\"invitedCount\":2,"
                        + "\"failedCount\":1,\"resultDocumentId\":\"" + doc + "\"}",
                null,
                now,
                now);
        when(jobService.recentJobs(tenant, "import")).thenReturn(List.of(queued, done));

        List<EmployeeImportJobResponse> jobs = service.recentJobs();

        assertThat(jobs.get(0).kind()).isEqualTo("IMPORT");
        assertThat(jobs.get(0).totalCount()).isNull();
        assertThat(jobs.get(0).resultDocumentId()).isNull();
        assertThat(jobs.get(0).toString()).doesNotContain("secret");
        assertThat(jobs.get(1).invitedCount()).isEqualTo(2);
        assertThat(jobs.get(1).failedCount()).isEqualTo(1);
        assertThat(jobs.get(1).resultDocumentId()).isEqualTo(doc);
    }

    @Test
    @DisplayName("a run creates as the requester, not as system, and completes with the summary")
    void runJobActsAsTheRequester() throws Exception {
        String csv = HEADER + GOOD + BAD;
        when(jobService.getJobStatus("j1", tenant))
                .thenReturn(Optional.of(new JobStatusResponseDTO(
                        "j1",
                        "import",
                        JobState.RUNNING,
                        0,
                        new ObjectMapper().writeValueAsString(new EmployeeImportJob.Input("IMPORT", actor, true, csv)),
                        null,
                        Instant.now(),
                        Instant.now())));
        AtomicReference<String> actingAs = new AtomicReference<>();
        UUID employeeId = UUID.randomUUID();
        when(employeeService.create(any(EmployeeRequest.class))).thenAnswer(i -> {
            actingAs.set(SecurityContextHolder.getContext().getAuthentication().getName());
            return employee(employeeId);
        });
        UUID doc = UUID.randomUUID();
        AtomicReference<String> storedAs = new AtomicReference<>();
        when(documentService.store(any(), any(), anyString(), any())).thenAnswer(i -> {
            storedAs.set(SecurityContextHolder.getContext().getAuthentication().getName());
            return doc;
        });

        JsonNode summary = new ObjectMapper().readTree(service.runJob("j1"));

        assertThat(actingAs.get()).isEqualTo(actor.toString());
        assertThat(storedAs.get())
                .as("the result file is stored as the requester")
                .isEqualTo(actor.toString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(summary.get("totalCount").asInt()).isEqualTo(2);
        assertThat(summary.get("createdCount").asInt()).isEqualTo(1);
        assertThat(summary.get("invitedCount").asInt()).isZero();
        assertThat(summary.get("failedCount").asInt()).isEqualTo(1);
        assertThat(summary.get("resultDocumentId").asText()).isEqualTo(doc.toString());
        verify(jobService, org.mockito.Mockito.atLeastOnce()).updateProgress(eq("j1"), anyInt());
    }

    @Test
    @DisplayName("a result file that cannot be stored leaves the job without one rather than failing it")
    void storeFailureIsNotFatal() {
        when(documentService.store(any(), any(), anyString(), any()))
                .thenThrow(new DocumentService.StorageUnavailableException("no blob storage"));

        assertThat(service.storeResult("j1", "IMPORT", List.of())).isNull();
    }

    @Test
    @DisplayName("result cells: commas and quotes are quoted, formula starts are neutralised")
    void resultCells() {
        assertThat(EmployeeImportServiceImpl.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(EmployeeImportServiceImpl.cell("say \"x\"")).isEqualTo("\"say \"\"x\"\"\"");
        assertThat(EmployeeImportServiceImpl.cell("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(EmployeeImportServiceImpl.cell(null)).isEmpty();
    }

    private EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id, tenant, "E1", "Asha", null, null, null, null, null, null, null, null, true, null, null, null, null,
                null, null);
    }
}
