package com.infinevo.core.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for overtime request state transitions and refusals (W-40.5 §4, §7).
 */
class OvertimeRequestStatesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-04-15T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OvertimeRequestRepository overtimeRequests;
    private EmployeeRepository employees;
    private PayInputService payInputService;
    private OvertimeServiceImpl service;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        overtimeRequests = mock(OvertimeRequestRepository.class);
        employees = mock(EmployeeRepository.class);
        payInputService = mock(PayInputService.class);
        service = new OvertimeServiceImpl(overtimeRequests, employees, payInputService, CLOCK);
        TenantContext.set(TENANT);

        employeeId = UUID.randomUUID();
        when(employees.findByIdAndTenantIdAndDeletedFalse(eq(employeeId), eq(TENANT)))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(overtimeRequests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(overtimeRequests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── submit ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("submit saves PENDING row with source REQUEST and never touches pay input ledger")
    void submit_savesPendingRequest_andNeverTouchesLedger() {
        OvertimeEntry entry = new OvertimeEntry(
                employeeId, LocalDate.of(2026, 4, 14), new BigDecimal("2.50"), null, "Covered night shift");

        OvertimeResponse response = service.submit(entry);

        assertThat(response.status()).isEqualTo(OvertimeStatus.PENDING);
        assertThat(response.source()).isEqualTo(OvertimeSource.REQUEST);
        assertThat(response.hours()).isEqualByComparingTo("2.50");
        assertThat(response.amount()).isNull();
        assertThat(response.remarks()).isEqualTo("Covered night shift");
        assertThat(response.payInputId()).isNull();
        assertThat(response.postedPeriod()).isNull();

        verifyNoInteractions(payInputService);

        ArgumentCaptor<OvertimeRequest> captor = ArgumentCaptor.forClass(OvertimeRequest.class);
        verify(overtimeRequests).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OvertimeStatus.PENDING);
        assertThat(captor.getValue().getSource()).isEqualTo(OvertimeSource.REQUEST);
    }

    @Test
    @DisplayName("submit with amount present is refused with ValidationException")
    void submit_withAmount_throwsValidationException() {
        OvertimeEntry entry = new OvertimeEntry(
                employeeId,
                LocalDate.of(2026, 4, 14),
                new BigDecimal("2.00"),
                new BigDecimal("500.00"),
                "Request with money");

        assertThatThrownBy(() -> service.submit(entry))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("amount"));

        verifyNoInteractions(payInputService);
        verify(overtimeRequests, never()).save(any());
    }

    @Test
    @DisplayName("submit with remarks over 255 chars is refused with ValidationException")
    void submit_withTooLongRemarks_throwsValidationException() {
        String longRemarks = "a".repeat(256);
        OvertimeEntry entry =
                new OvertimeEntry(employeeId, LocalDate.of(2026, 4, 14), new BigDecimal("2.00"), null, longRemarks);

        assertThatThrownBy(() -> service.submit(entry))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("remarks"));

        verifyNoInteractions(payInputService);
    }

    @Test
    @DisplayName("submit with future date or missing employee is refused")
    void submit_validatesEmployeeAndDate() {
        OvertimeEntry futureEntry =
                new OvertimeEntry(employeeId, LocalDate.of(2026, 4, 16), new BigDecimal("2.00"), null, "Future");
        assertThatThrownBy(() -> service.submit(futureEntry))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("overtimeDate"));

        UUID unknownEmployee = UUID.randomUUID();
        OvertimeEntry unknownEmpEntry =
                new OvertimeEntry(unknownEmployee, LocalDate.of(2026, 4, 14), new BigDecimal("2.00"), null, "Unknown");
        assertThatThrownBy(() -> service.submit(unknownEmpEntry))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("employeeId"));
    }

    // ── approve ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("approve transitions PENDING to APPROVED and posts to pay input ledger")
    void approve_fromPending_postsToLedger_andMarksApproved() {
        UUID id = UUID.randomUUID();
        OvertimeRequest pending = OvertimeRequest.pending(
                TENANT, employeeId, LocalDate.of(2026, 4, 10), new BigDecimal("3.00"), "Pending work", "requester");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(pending));

        UUID payInputId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 4);
        when(payInputService.record(any()))
                .thenReturn(new PayInputResponse(
                        payInputId,
                        employeeId,
                        period,
                        PayInputKind.OVERTIME,
                        new BigDecimal("3.00"),
                        null,
                        "core",
                        "overtime_request:" + id,
                        null,
                        null,
                        Instant.now()));

        OvertimeResponse response = service.approve(id);

        assertThat(response.status()).isEqualTo(OvertimeStatus.APPROVED);
        assertThat(response.payInputId()).isEqualTo(payInputId);
        assertThat(response.postedPeriod()).isEqualTo(period);

        ArgumentCaptor<PayInputCommand> cmdCaptor = ArgumentCaptor.forClass(PayInputCommand.class);
        verify(payInputService).record(cmdCaptor.capture());
        PayInputCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.employeeId()).isEqualTo(employeeId);
        assertThat(cmd.period()).isEqualTo(period);
        assertThat(cmd.kind()).isEqualTo(PayInputKind.OVERTIME);
        assertThat(cmd.quantity()).isEqualByComparingTo("3.00");
        assertThat(cmd.amount()).isNull();
        assertThat(cmd.sourceModule()).isEqualTo("core");
        assertThat(cmd.sourceRef()).isEqualTo("overtime_request:" + pending.getId());
    }

    @Test
    @DisplayName("approve on an already APPROVED entry is idempotent and posts nothing")
    void approve_whenAlreadyApproved_isIdempotent() {
        UUID id = UUID.randomUUID();
        OvertimeRequest approved = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeSource.ADMIN,
                "Already approved",
                "admin");
        approved.markPosted(UUID.randomUUID(), YearMonth.of(2026, 4), "admin");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(approved));

        OvertimeResponse response = service.approve(id);

        assertThat(response.status()).isEqualTo(OvertimeStatus.APPROVED);
        verifyNoInteractions(payInputService);
    }

    @Test
    @DisplayName("approve on REJECTED or CANCELLED throws IllegalStateException")
    void approve_fromInvalidStates_throwsIllegalStateException() {
        UUID rejectedId = UUID.randomUUID();
        OvertimeRequest rejected = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.REJECTED,
                OvertimeSource.REQUEST,
                "Rejected",
                "manager");
        when(overtimeRequests.findByIdAndTenantId(rejectedId, TENANT)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.approve(rejectedId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");

        UUID cancelledId = UUID.randomUUID();
        OvertimeRequest cancelled = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.CANCELLED,
                OvertimeSource.REQUEST,
                "Cancelled",
                "manager");
        when(overtimeRequests.findByIdAndTenantId(cancelledId, TENANT)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.approve(cancelledId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");

        verifyNoInteractions(payInputService);
    }

    // ── reject ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("reject transitions PENDING to REJECTED with no ledger interaction")
    void reject_fromPending_setsStatusRejected_andNoLedgerCall() {
        UUID id = UUID.randomUUID();
        OvertimeRequest pending = OvertimeRequest.pending(
                TENANT, employeeId, LocalDate.of(2026, 4, 10), new BigDecimal("3.00"), "Pending work", "requester");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(pending));

        OvertimeResponse response = service.reject(id);

        assertThat(response.status()).isEqualTo(OvertimeStatus.REJECTED);
        verifyNoInteractions(payInputService);
    }

    @Test
    @DisplayName("reject when already REJECTED is idempotent")
    void reject_whenAlreadyRejected_isIdempotent() {
        UUID id = UUID.randomUUID();
        OvertimeRequest rejected = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.REJECTED,
                OvertimeSource.REQUEST,
                "Already rejected",
                "manager");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(rejected));

        OvertimeResponse response = service.reject(id);

        assertThat(response.status()).isEqualTo(OvertimeStatus.REJECTED);
        verifyNoInteractions(payInputService);
    }

    @Test
    @DisplayName("reject on APPROVED or CANCELLED throws IllegalStateException")
    void reject_fromInvalidStates_throwsIllegalStateException() {
        UUID approvedId = UUID.randomUUID();
        OvertimeRequest approved = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.APPROVED,
                OvertimeSource.ADMIN,
                "Approved",
                "admin");
        when(overtimeRequests.findByIdAndTenantId(approvedId, TENANT)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.reject(approvedId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");

        UUID cancelledId = UUID.randomUUID();
        OvertimeRequest cancelled = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.CANCELLED,
                OvertimeSource.REQUEST,
                "Cancelled",
                "manager");
        when(overtimeRequests.findByIdAndTenantId(cancelledId, TENANT)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.reject(cancelledId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    // ── cancel ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel on PENDING cancels row and makes no ledger reversal call")
    void cancel_onPending_cancelsWithoutLedgerReversal() {
        UUID id = UUID.randomUUID();
        OvertimeRequest pending = OvertimeRequest.pending(
                TENANT, employeeId, LocalDate.of(2026, 4, 10), new BigDecimal("3.00"), "Pending work", "requester");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(pending));

        OvertimeResponse response = service.cancel(id);

        assertThat(response.status()).isEqualTo(OvertimeStatus.CANCELLED);
        verifyNoInteractions(payInputService);
    }

    @Test
    @DisplayName("cancel on APPROVED cancels row and reverses ledger row")
    void cancel_onApproved_reversesLedgerRow() {
        UUID id = UUID.randomUUID();
        UUID payInputId = UUID.randomUUID();
        OvertimeRequest approved = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.APPROVED,
                OvertimeSource.ADMIN,
                "Approved work",
                "admin");
        approved.markPosted(payInputId, YearMonth.of(2026, 4), "admin");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(approved));

        OvertimeResponse response = service.cancel(id);

        assertThat(response.status()).isEqualTo(OvertimeStatus.CANCELLED);
        verify(payInputService).reverse(payInputId, "overtime cancelled");
    }

    @Test
    @DisplayName("cancel on REJECTED throws NotCancellableException")
    void cancel_onRejected_throwsNotCancellableException() {
        UUID id = UUID.randomUUID();
        OvertimeRequest rejected = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.REJECTED,
                OvertimeSource.REQUEST,
                "Rejected work",
                "manager");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(OvertimeService.NotCancellableException.class);

        verifyNoInteractions(payInputService);
    }

    @Test
    @DisplayName("cancel on CANCELLED throws AlreadyCancelledException")
    void cancel_onCancelled_throwsAlreadyCancelledException() {
        UUID id = UUID.randomUUID();
        OvertimeRequest cancelled = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeStatus.CANCELLED,
                OvertimeSource.REQUEST,
                "Cancelled work",
                "requester");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(OvertimeService.AlreadyCancelledException.class);

        verifyNoInteractions(payInputService);
    }
}
