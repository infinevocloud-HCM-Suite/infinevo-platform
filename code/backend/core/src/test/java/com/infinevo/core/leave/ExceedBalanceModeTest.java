package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying behavior of different {@link ExceedBalanceMode}s (W-16.4a, spec section 7).
 */
class ExceedBalanceModeTest {

    private LopDerivationService lopDerivationService;
    private LeaveConsumptionRepository leaveConsumptionRepository;
    private LeaveMonthlyLopRepository leaveMonthlyLopRepository;
    private LeaveAllocationRepository leaveAllocationRepository;
    private LeavePolicyRepository leavePolicyRepository;
    private LeaveBalanceService leaveBalanceService;
    private PayInputService payInputService;
    private LeaveConsumptionServiceImpl consumptionService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();
    private final UUID allocationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lopDerivationService = new LopDerivationServiceImpl();
        leaveConsumptionRepository = org.mockito.Mockito.mock(LeaveConsumptionRepository.class);
        leaveMonthlyLopRepository = org.mockito.Mockito.mock(LeaveMonthlyLopRepository.class);
        leaveAllocationRepository = org.mockito.Mockito.mock(LeaveAllocationRepository.class);
        leavePolicyRepository = org.mockito.Mockito.mock(LeavePolicyRepository.class);
        leaveBalanceService = org.mockito.Mockito.mock(LeaveBalanceService.class);
        payInputService = org.mockito.Mockito.mock(PayInputService.class);

        consumptionService = new LeaveConsumptionServiceImpl(
                leaveConsumptionRepository,
                leaveMonthlyLopRepository,
                leaveAllocationRepository,
                leavePolicyRepository,
                leaveBalanceService,
                lopDerivationService,
                payInputService);

        LeaveAllocation alloc = new LeaveAllocation(
                tenantId,
                employeeId,
                leaveTypeId,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("2.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                null);
        alloc.setId(allocationId);

        org.mockito.Mockito.when(leaveAllocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                org.mockito.ArgumentMatchers.eq(tenantId),
                                org.mockito.ArgumentMatchers.eq(employeeId),
                                org.mockito.ArgumentMatchers.eq(leaveTypeId),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(alloc));

        org.mockito.Mockito.when(leaveBalanceService.getBalance(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(employeeId),
                        org.mockito.ArgumentMatchers.eq(leaveTypeId),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(new LeaveBalanceResponse(
                        employeeId,
                        leaveTypeId,
                        "AL",
                        "Annual Leave",
                        "2026",
                        new BigDecimal("2.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("2.00"),
                        null)));

        org.mockito.Mockito.when(leaveMonthlyLopRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    LeaveMonthlyLop lop = inv.getArgument(0);
                    lop.setId(UUID.randomUUID());
                    return lop;
                });

        org.mockito.Mockito.when(payInputService.record(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PayInputResponse(
                        UUID.randomUUID(),
                        employeeId,
                        YearMonth.of(2026, 5),
                        PayInputKind.LOP_DAYS,
                        new BigDecimal("3.00"),
                        null,
                        "core",
                        "ref",
                        null,
                        null,
                        Instant.now()));
    }

    @Test
    @DisplayName("the same excess under markAsLOP yields loss of pay; under noLimit and yearEndLimit it yields none")
    void compareExceedBalanceModes() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 5);
        BigDecimal requested = new BigDecimal("5.00");
        BigDecimal balance = new BigDecimal("2.00"); // excess = 3.00

        // 1. markAsLOP -> produces 3.00 days LOP
        Map<YearMonth, BigDecimal> lopMark =
                lopDerivationService.deriveLop(from, to, false, ExceedBalanceMode.MARK_AS_LOP, requested, balance);
        assertThat(lopMark).isNotEmpty();
        assertThat(lopMark.get(YearMonth.of(2026, 5))).isEqualByComparingTo("3.00");

        // 2. noLimit -> produces 0 LOP (balance goes negative)
        Map<YearMonth, BigDecimal> lopNoLimit =
                lopDerivationService.deriveLop(from, to, false, ExceedBalanceMode.NO_LIMIT, requested, balance);
        assertThat(lopNoLimit).isEmpty();

        // 3. yearEndLimit -> produces 0 LOP (balance goes negative up to limit)
        Map<YearMonth, BigDecimal> lopYearEnd =
                lopDerivationService.deriveLop(from, to, false, ExceedBalanceMode.YEAR_END_LIMIT, requested, balance);
        assertThat(lopYearEnd).isEmpty();
    }

    @Test
    @DisplayName("consumption under MARK_AS_LOP saves consumption and records monthly LOP while balance goes negative")
    void consumeUnderMarkAsLop() {
        LeavePolicy policy = new LeavePolicy();
        policy.setExceedBalanceMode(ExceedBalanceMode.MARK_AS_LOP);
        org.mockito.Mockito.when(
                        leavePolicyRepository
                                .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                        org.mockito.ArgumentMatchers.eq(tenantId),
                                        org.mockito.ArgumentMatchers.eq(leaveTypeId),
                                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(policy));

        LeaveRequest req = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                false,
                null,
                new BigDecimal("5.00"),
                "Trip",
                LeaveRequestStatus.APPROVED,
                null,
                false);
        req.setId(UUID.randomUUID());

        consumptionService.consume(req);

        // Verify LeaveConsumption was saved with 5.00 days
        org.mockito.ArgumentCaptor<LeaveConsumption> consumptionCaptor =
                org.mockito.ArgumentCaptor.forClass(LeaveConsumption.class);
        org.mockito.Mockito.verify(leaveConsumptionRepository).save(consumptionCaptor.capture());
        assertThat(consumptionCaptor.getValue().getConsumedDays()).isEqualByComparingTo("5.00");

        // Remaining balance after consuming 5.00 with 2.00 entitlement is negative 3.00
        BigDecimal newRemaining =
                new BigDecimal("2.00").subtract(consumptionCaptor.getValue().getConsumedDays());
        assertThat(newRemaining).isEqualByComparingTo("-3.00");

        org.mockito.ArgumentCaptor<LeaveMonthlyLop> lopCaptor =
                org.mockito.ArgumentCaptor.forClass(LeaveMonthlyLop.class);
        org.mockito.Mockito.verify(leaveMonthlyLopRepository, org.mockito.Mockito.atLeastOnce())
                .save(lopCaptor.capture());
        assertThat(lopCaptor.getValue().getLopDays()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("consumption under NO_LIMIT allows negative balance and records zero LOP")
    void consumeUnderNoLimit() {
        LeavePolicy policy = new LeavePolicy();
        policy.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
        org.mockito.Mockito.when(
                        leavePolicyRepository
                                .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                        org.mockito.ArgumentMatchers.eq(tenantId),
                                        org.mockito.ArgumentMatchers.eq(leaveTypeId),
                                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(policy));

        LeaveRequest req = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5),
                false,
                null,
                new BigDecimal("5.00"),
                "Trip",
                LeaveRequestStatus.APPROVED,
                null,
                false);
        req.setId(UUID.randomUUID());

        consumptionService.consume(req);

        org.mockito.ArgumentCaptor<LeaveConsumption> consumptionCaptor =
                org.mockito.ArgumentCaptor.forClass(LeaveConsumption.class);
        org.mockito.Mockito.verify(leaveConsumptionRepository).save(consumptionCaptor.capture());
        assertThat(consumptionCaptor.getValue().getConsumedDays()).isEqualByComparingTo("5.00");

        BigDecimal newRemaining =
                new BigDecimal("2.00").subtract(consumptionCaptor.getValue().getConsumedDays());
        assertThat(newRemaining).isEqualByComparingTo("-3.00");

        // Under NO_LIMIT, no monthly LOP is recorded
        org.mockito.Mockito.verify(leaveMonthlyLopRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }
}
