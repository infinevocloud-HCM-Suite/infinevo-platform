package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link LeaveConsumptionServiceImpl} (W-16.4a, spec section 7).
 */
class LeaveConsumptionServiceTest {

    private LeaveConsumptionRepository leaveConsumptionRepository;
    private LeaveMonthlyLopRepository leaveMonthlyLopRepository;
    private LeaveAllocationRepository leaveAllocationRepository;
    private LeavePolicyRepository leavePolicyRepository;
    private LeaveBalanceService leaveBalanceService;
    private LopDerivationService lopDerivationService;
    private PayInputService payInputService;

    private LeaveConsumptionServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();
    private final UUID allocationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaveConsumptionRepository = mock(LeaveConsumptionRepository.class);
        leaveMonthlyLopRepository = mock(LeaveMonthlyLopRepository.class);
        leaveAllocationRepository = mock(LeaveAllocationRepository.class);
        leavePolicyRepository = mock(LeavePolicyRepository.class);
        leaveBalanceService = mock(LeaveBalanceService.class);
        lopDerivationService = mock(LopDerivationService.class);
        payInputService = mock(PayInputService.class);

        service = new LeaveConsumptionServiceImpl(
                leaveConsumptionRepository,
                leaveMonthlyLopRepository,
                leaveAllocationRepository,
                leavePolicyRepository,
                leaveBalanceService,
                lopDerivationService,
                payInputService);
    }

    private LeaveAllocation createAllocation() {
        LeaveAllocation alloc = new LeaveAllocation();
        alloc.setId(allocationId);
        alloc.setTenantId(tenantId);
        alloc.setEmployeeId(employeeId);
        alloc.setLeaveTypeId(leaveTypeId);
        alloc.setLeaveYear("2026");
        alloc.setYearStartDate(LocalDate.of(2026, 1, 1));
        alloc.setYearEndDate(LocalDate.of(2026, 12, 31));
        return alloc;
    }

    private LeaveRequest createRequest(UUID reqId, LocalDate from, LocalDate to, BigDecimal days) {
        return new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                from,
                to,
                false,
                null,
                days,
                "Trip",
                LeaveRequestStatus.APPROVED,
                null,
                false);
    }

    @Test
    @DisplayName("one approval writes exactly one consumption row")
    void singleApprovalWritesOneRow() {
        UUID reqId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 3);
        LeaveRequest req = createRequest(reqId, from, to, new BigDecimal("3.00"));
        req.setId(reqId);

        when(leaveConsumptionRepository.existsByTenantIdAndLeaveRequestIdAndReversesIdIsNull(tenantId, reqId))
                .thenReturn(false);

        LeaveAllocation alloc = createAllocation();
        when(leaveAllocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                eq(tenantId), eq(employeeId), eq(leaveTypeId), eq(from), eq(from)))
                .thenReturn(Optional.of(alloc));

        LeaveBalanceResponse bal = new LeaveBalanceResponse(
                employeeId,
                leaveTypeId,
                "AL",
                "Annual Leave",
                "2026",
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                null);
        when(leaveBalanceService.getBalance(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(Optional.of(bal));

        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, from))
                .thenReturn(Optional.empty());

        service.consume(req);

        ArgumentCaptor<LeaveConsumption> captor = ArgumentCaptor.forClass(LeaveConsumption.class);
        verify(leaveConsumptionRepository, times(1)).save(captor.capture());

        LeaveConsumption saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getEmployeeId()).isEqualTo(employeeId);
        assertThat(saved.getAllocationId()).isEqualTo(allocationId);
        assertThat(saved.getLeaveRequestId()).isEqualTo(reqId);
        assertThat(saved.getConsumedDays()).isEqualByComparingTo("3.00");
        assertThat(saved.getPeriod()).isEqualTo("2026-04");
        assertThat(saved.getReversesId()).isNull();
    }

    @Test
    @DisplayName("a repeat of the same approval writes no new rows (idempotency)")
    void repeatApprovalWritesNothing() {
        UUID reqId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 3);
        LeaveRequest req = createRequest(reqId, from, to, new BigDecimal("3.00"));
        req.setId(reqId);

        // Already exists!
        when(leaveConsumptionRepository.existsByTenantIdAndLeaveRequestIdAndReversesIdIsNull(tenantId, reqId))
                .thenReturn(true);

        service.consume(req);

        verify(leaveConsumptionRepository, never()).save(any());
        verify(leaveMonthlyLopRepository, never()).save(any());
        verify(payInputService, never()).record(any());
    }

    @Test
    @DisplayName("excess under markAsLOP writes LeaveMonthlyLop delta row and posts PayInput")
    void excessWritesMonthlyLopAndPayInput() {
        UUID reqId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 10);
        LocalDate to = LocalDate.of(2026, 4, 12);
        LeaveRequest req = createRequest(reqId, from, to, new BigDecimal("3.00"));
        req.setId(reqId);

        when(leaveConsumptionRepository.existsByTenantIdAndLeaveRequestIdAndReversesIdIsNull(tenantId, reqId))
                .thenReturn(false);

        LeaveAllocation alloc = createAllocation();
        when(leaveAllocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                eq(tenantId), eq(employeeId), eq(leaveTypeId), eq(from), eq(from)))
                .thenReturn(Optional.of(alloc));

        // Available balance is 1.00 -> excess is 2.00
        LeaveBalanceResponse bal = new LeaveBalanceResponse(
                employeeId,
                leaveTypeId,
                "AL",
                "Annual Leave",
                "2026",
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1.00"),
                null);
        when(leaveBalanceService.getBalance(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(Optional.of(bal));

        LeavePolicy policy = new LeavePolicy();
        policy.setExceedBalanceMode(ExceedBalanceMode.MARK_AS_LOP);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, from))
                .thenReturn(Optional.of(policy));

        when(lopDerivationService.splitExcessDays(
                        eq(from), eq(to), eq(false), eq(ExceedBalanceMode.MARK_AS_LOP), eq(new BigDecimal("2.00"))))
                .thenReturn(Map.of(YearMonth.of(2026, 4), new BigDecimal("2.00")));

        UUID lopId = UUID.randomUUID();
        when(leaveMonthlyLopRepository.save(any(LeaveMonthlyLop.class))).thenAnswer(inv -> {
            LeaveMonthlyLop l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(lopId);
            }
            return l;
        });

        UUID payInputId = UUID.randomUUID();
        PayInputResponse mockPayResp = new PayInputResponse(
                payInputId,
                employeeId,
                YearMonth.of(2026, 4),
                PayInputKind.LOP_DAYS,
                new BigDecimal("2.00"),
                null,
                "core",
                lopId.toString(),
                null,
                null,
                Instant.now());
        when(payInputService.record(any(PayInputCommand.class))).thenReturn(mockPayResp);

        service.consume(req);

        // Verify consumption row
        verify(leaveConsumptionRepository, times(1)).save(any(LeaveConsumption.class));

        // Verify LOP row saved with payInputId (single INSERT due to REVOKE UPDATE)
        ArgumentCaptor<LeaveMonthlyLop> lopCaptor = ArgumentCaptor.forClass(LeaveMonthlyLop.class);
        verify(leaveMonthlyLopRepository, times(1)).save(lopCaptor.capture());
        LeaveMonthlyLop savedLop = lopCaptor.getValue();

        // Verify PayInputService called with LOP_DAYS and savedLop's id as sourceRef
        ArgumentCaptor<PayInputCommand> payCaptor = ArgumentCaptor.forClass(PayInputCommand.class);
        verify(payInputService, times(1)).record(payCaptor.capture());
        PayInputCommand posted = payCaptor.getValue();
        assertThat(posted.employeeId()).isEqualTo(employeeId);
        assertThat(posted.kind()).isEqualTo(PayInputKind.LOP_DAYS);
        assertThat(posted.quantity()).isEqualByComparingTo("2.00");
        assertThat(posted.sourceRef()).isEqualTo(savedLop.getId().toString());
    }

    @Test
    @DisplayName("cancellation writes reversing consumption and LOP rows and reverses pay input")
    void cancellationReversesConsumptionAndLop() {
        UUID reqId = UUID.randomUUID();
        LeaveRequest req =
                createRequest(reqId, LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 12), new BigDecimal("3.00"));
        req.setId(reqId);

        UUID origConsId = UUID.randomUUID();
        LeaveConsumption origCons = new LeaveConsumption(
                tenantId,
                employeeId,
                allocationId,
                reqId,
                new BigDecimal("3.00"),
                LocalDate.of(2026, 4, 1),
                "2026-04",
                null,
                "Vacation");
        origCons.setId(origConsId);

        when(leaveConsumptionRepository.findByTenantIdAndLeaveRequestId(tenantId, reqId))
                .thenReturn(List.of(origCons));

        UUID origLopId = UUID.randomUUID();
        UUID payInputId = UUID.randomUUID();
        LeaveMonthlyLop origLop = new LeaveMonthlyLop(
                tenantId, employeeId, "2026-04", leaveTypeId, reqId, new BigDecimal("2.00"), null, payInputId);
        origLop.setId(origLopId);

        when(leaveMonthlyLopRepository.findByTenantIdAndLeaveRequestIdAndReversesIdIsNull(tenantId, reqId))
                .thenReturn(List.of(origLop));

        PayInputResponse reversedPayResp = new PayInputResponse(
                UUID.randomUUID(),
                employeeId,
                YearMonth.of(2026, 4),
                PayInputKind.LOP_DAYS,
                new BigDecimal("2.00"),
                null,
                "core",
                null,
                null,
                payInputId,
                Instant.now());
        when(payInputService.reverse(eq(payInputId), any())).thenReturn(reversedPayResp);

        service.cancel(req, "Changed travel plans");

        // Verify reversing consumption row
        ArgumentCaptor<LeaveConsumption> consCaptor = ArgumentCaptor.forClass(LeaveConsumption.class);
        verify(leaveConsumptionRepository, times(1)).save(consCaptor.capture());
        LeaveConsumption revCons = consCaptor.getValue();
        assertThat(revCons.getConsumedDays()).isEqualByComparingTo("-3.00");
        assertThat(revCons.getReversesId()).isEqualTo(origConsId);

        // Verify reversing monthly LOP row
        ArgumentCaptor<LeaveMonthlyLop> lopCaptor = ArgumentCaptor.forClass(LeaveMonthlyLop.class);
        verify(leaveMonthlyLopRepository, times(1)).save(lopCaptor.capture());
        LeaveMonthlyLop revLop = lopCaptor.getValue();
        assertThat(revLop.getLopDays()).isEqualByComparingTo("-2.00");
        assertThat(revLop.getReversesId()).isEqualTo(origLopId);

        // Verify pay input reversal
        verify(payInputService, times(1)).reverse(eq(payInputId), eq("Changed travel plans"));
    }
}
