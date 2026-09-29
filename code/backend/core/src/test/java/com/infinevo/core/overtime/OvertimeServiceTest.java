package com.infinevo.core.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
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
 * Unit test for {@link OvertimeServiceImpl} (W-39.2 §7). No calculation is exercised: the service
 * exposes no rate and does no arithmetic (spec §4) — {@code quantity} and {@code amount} on the
 * {@code PayInputCommand} it builds are exactly {@code hours} and {@code amount}, unchanged.
 *
 * <p>The generated id a real insert would assign is not available under a mock ({@code
 * PayInputServiceImplTest} accepts the same limit) — {@code OvertimeLedgerIT} is what proves
 * {@code sourceRef} carries a real, non-null id end to end. This class asserts the prefix only.
 */
class OvertimeServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-04-15T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OvertimeRequestRepository overtimeRequests;
    private EmployeeRepository employees;
    private com.infinevo.core.payinput.PayInputService payInputService;
    private OvertimeServiceImpl service;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        overtimeRequests = mock(OvertimeRequestRepository.class);
        employees = mock(EmployeeRepository.class);
        payInputService = mock(com.infinevo.core.payinput.PayInputService.class);
        service = new OvertimeServiceImpl(overtimeRequests, employees, payInputService, CLOCK);
        TenantContext.set(TENANT);

        employeeId = UUID.randomUUID();
        when(employees.findByIdAndTenantIdAndDeletedFalse(eq(employeeId), eq(TENANT)))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(overtimeRequests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payInputService.record(any()))
                .thenReturn(new com.infinevo.core.payinput.PayInputResponse(
                        UUID.randomUUID(),
                        employeeId,
                        YearMonth.of(2026, 4),
                        com.infinevo.core.payinput.PayInputKind.OVERTIME,
                        new BigDecimal("3.00"),
                        null,
                        "core",
                        "overtime_request:null",
                        null,
                        null,
                        NOW));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("record builds a PayInputCommand with kind OVERTIME, quantity = hours, amount unchanged, and posts it")
    void recordBuildsTheLedgerCommandAndPostsIt() {
        service.record(
                new OvertimeEntry(employeeId, LocalDate.of(2026, 4, 10), new BigDecimal("3.00"), null, "late shift"));

        ArgumentCaptor<com.infinevo.core.payinput.PayInputCommand> captor =
                ArgumentCaptor.forClass(com.infinevo.core.payinput.PayInputCommand.class);
        verify(payInputService).record(captor.capture());
        com.infinevo.core.payinput.PayInputCommand command = captor.getValue();

        assertThat(command.employeeId()).isEqualTo(employeeId);
        assertThat(command.period()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(command.kind()).isEqualTo(com.infinevo.core.payinput.PayInputKind.OVERTIME);
        assertThat(command.quantity()).isEqualByComparingTo("3.00");
        assertThat(command.amount()).isNull();
        assertThat(command.sourceModule()).isEqualTo("core");
        assertThat(command.sourceRef()).startsWith("overtime_request:");
    }

    @Test
    @DisplayName("An amount, when given, is passed through unchanged: the service does no arithmetic")
    void amountIsPassedThroughUnchanged() {
        service.record(new OvertimeEntry(
                employeeId, LocalDate.of(2026, 4, 10), new BigDecimal("2.50"), new BigDecimal("450.00"), null));

        ArgumentCaptor<com.infinevo.core.payinput.PayInputCommand> captor =
                ArgumentCaptor.forClass(com.infinevo.core.payinput.PayInputCommand.class);
        verify(payInputService).record(captor.capture());
        assertThat(captor.getValue().amount().toAmount()).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("An unknown or deleted employee is refused before the ledger is ever called")
    void unknownEmployeeIsRefused() {
        UUID unknown = UUID.randomUUID();
        when(employees.findByIdAndTenantIdAndDeletedFalse(eq(unknown), eq(TENANT)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(
                        new OvertimeEntry(unknown, LocalDate.of(2026, 4, 10), BigDecimal.ONE, null, null)))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("employeeId"));
        verify(payInputService, never()).record(any());
    }

    @Test
    @DisplayName("A future date is refused")
    void futureDateIsRefused() {
        assertThatThrownBy(() -> service.record(
                        new OvertimeEntry(employeeId, LocalDate.of(2026, 4, 16), BigDecimal.ONE, null, null)))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("overtimeDate"));
    }

    @Test
    @DisplayName("hours missing, zero, negative or over 24 are all refused")
    void hoursOutOfRangeIsRefused() {
        for (BigDecimal bad : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("24.01")}) {
            assertThatThrownBy(() ->
                            service.record(new OvertimeEntry(employeeId, LocalDate.of(2026, 4, 10), bad, null, null)))
                    .isInstanceOf(OvertimeService.ValidationException.class)
                    .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                            .containsKey("hours"));
        }
    }

    @Test
    @DisplayName("Exactly 24 hours is allowed; the boundary is inclusive")
    void twentyFourHoursIsAllowed() {
        service.record(new OvertimeEntry(employeeId, LocalDate.of(2026, 4, 10), new BigDecimal("24"), null, null));
        verify(payInputService).record(any());
    }

    @Test
    @DisplayName("A zero or negative amount is refused")
    void nonPositiveAmountIsRefused() {
        assertThatThrownBy(() -> service.record(new OvertimeEntry(
                        employeeId, LocalDate.of(2026, 4, 10), BigDecimal.ONE, new BigDecimal("-5"), null)))
                .isInstanceOf(OvertimeService.ValidationException.class)
                .satisfies(e -> assertThat(((OvertimeService.ValidationException) e).fieldErrors())
                        .containsKey("amount"));
    }

    @Test
    @DisplayName("cancel reverses the ledger row and flips status to CANCELLED")
    void cancelReversesTheLedgerRow() {
        UUID id = UUID.randomUUID();
        UUID payInputId = UUID.randomUUID();
        OvertimeRequest overtime = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                new BigDecimal("3.00"),
                null,
                OvertimeSource.ADMIN,
                null,
                "admin");
        overtime.markPosted(payInputId, YearMonth.of(2026, 4), "admin");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(overtime));

        service.cancel(id);

        verify(payInputService).reverse(payInputId, "overtime cancelled");
        assertThat(overtime.getStatus()).isEqualTo(OvertimeStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel on an unknown id throws NotFoundException")
    void cancelUnknownIdThrows() {
        UUID id = UUID.randomUUID();
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(OvertimeService.NotFoundException.class);
    }

    @Test
    @DisplayName("cancel on an already-cancelled entry throws AlreadyCancelledException, and does not reverse again")
    void cancelTwiceThrows() {
        UUID id = UUID.randomUUID();
        OvertimeRequest overtime = new OvertimeRequest(
                TENANT,
                employeeId,
                LocalDate.of(2026, 4, 10),
                BigDecimal.ONE,
                null,
                OvertimeSource.ADMIN,
                null,
                "admin");
        overtime.markPosted(UUID.randomUUID(), YearMonth.of(2026, 4), "admin");
        overtime.cancel("admin");
        when(overtimeRequests.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(overtime));

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(OvertimeService.AlreadyCancelledException.class);
        verify(payInputService, never()).reverse(any(), any());
    }

    @Test
    @DisplayName("list refuses from after to, and a span over 93 days")
    void listRefusesBadRanges() {
        assertThatThrownBy(() -> service.list(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 1), null))
                .isInstanceOf(OvertimeService.ValidationException.class);
        assertThatThrownBy(() -> service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 10), null))
                .isInstanceOf(OvertimeService.ValidationException.class);
    }
}
