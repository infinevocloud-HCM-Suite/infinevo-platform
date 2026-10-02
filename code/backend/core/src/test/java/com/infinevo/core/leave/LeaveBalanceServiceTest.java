package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link LeaveBalanceService} (W-16.2, spec section 7).
 * Covers:
 * - remaining = entitlement + accrued + carried_forward - consumed
 * - half-days survive precision calculations without truncation
 * - carry-forward days stop counting after carry_forward_expires_on
 */
class LeaveBalanceServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID LEAVE_TYPE_ID = UUID.randomUUID();

    private LeaveAllocationRepository allocationRepository;
    private LeaveTypeRepository leaveTypeRepository;
    private LeaveConsumptionRepository consumptionRepository;
    private LeaveBalanceService balanceService;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(LeaveAllocationRepository.class);
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        consumptionRepository = mock(LeaveConsumptionRepository.class);
        balanceService = new LeaveBalanceServiceImpl(allocationRepository, leaveTypeRepository, consumptionRepository);

        LeaveType leaveType = new LeaveType(
                TENANT_ID, "AL", "Annual Leave", true, LeaveUnit.DAYS, true, LocalDate.of(2026, 1, 1), null, true);
        when(leaveTypeRepository.findByTenantIdAndId(TENANT_ID, LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
    }

    @Test
    @DisplayName("Remaining balance matches entitlement + accrued + carried_forward - consumed, half-days survive")
    void balanceCalculationFormulaAndHalfDayPrecision() {
        LocalDate evalDate = LocalDate.of(2026, 6, 15);

        // entitlement 12.50, accrued 2.50, carried 5.50
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("12.50"),
                new BigDecimal("2.50"),
                new BigDecimal("5.50"),
                LocalDate.of(2026, 6, 30), // expires June 30 (not expired yet!)
                BigDecimal.ONE,
                UUID.randomUUID());

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                TENANT_ID, EMPLOYEE_ID, LEAVE_TYPE_ID, evalDate, evalDate))
                .thenReturn(Optional.of(allocation));

        Optional<LeaveBalanceResponse> balanceOpt =
                balanceService.getBalance(TENANT_ID, EMPLOYEE_ID, LEAVE_TYPE_ID, evalDate);
        assertThat(balanceOpt).isPresent();
        LeaveBalanceResponse balance = balanceOpt.get();

        assertThat(balance.entitlementDays()).isEqualByComparingTo("12.50");
        assertThat(balance.accruedDays()).isEqualByComparingTo("2.50");
        assertThat(balance.carriedForwardDays()).isEqualByComparingTo("5.50");
        assertThat(balance.consumedDays()).isEqualByComparingTo("0.00");

        // 12.50 + 2.50 + 5.50 - 0 = 20.50
        assertThat(balance.remainingDays()).isEqualByComparingTo("20.50");
    }

    @Test
    @DisplayName("Carried-forward days stop counting towards balance after carry_forward_expires_on")
    void carriedForwardExpiresOnEnforced() {
        LocalDate evalDateAfterExpiry = LocalDate.of(2026, 7, 1);

        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("5.00"),
                LocalDate.of(2026, 6, 30), // expired yesterday!
                BigDecimal.ONE,
                UUID.randomUUID());

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                TENANT_ID, EMPLOYEE_ID, LEAVE_TYPE_ID, evalDateAfterExpiry, evalDateAfterExpiry))
                .thenReturn(Optional.of(allocation));

        Optional<LeaveBalanceResponse> balanceOpt =
                balanceService.getBalance(TENANT_ID, EMPLOYEE_ID, LEAVE_TYPE_ID, evalDateAfterExpiry);
        assertThat(balanceOpt).isPresent();
        LeaveBalanceResponse balance = balanceOpt.get();

        // Carried forward stops counting after expiry date
        assertThat(balance.carriedForwardDays()).isEqualByComparingTo("0.00");
        // 15.00 + 0 + 0 - 0 = 15.00
        assertThat(balance.remainingDays()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Leave consumed after carry_forward_expires_on does not revive lapsed days (W-16.2)")
    void expiredCarryForwardNotRevivedByLaterConsumption() {
        UUID allocId = UUID.randomUUID();
        LocalDate expiry = LocalDate.of(2026, 6, 30);
        LocalDate evalDate = LocalDate.of(2026, 8, 1);

        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("20.00"),
                new BigDecimal("0.00"),
                new BigDecimal("5.00"),
                expiry,
                BigDecimal.ONE,
                UUID.randomUUID());
        allocation.setId(allocId);

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                TENANT_ID, EMPLOYEE_ID, LEAVE_TYPE_ID, evalDate, evalDate))
                .thenReturn(Optional.of(allocation));

        // 2 days consumed before expiry (on or before 2026-06-30)
        when(consumptionRepository.sumConsumedDaysByAllocationAndConsumedOnLessThanEqual(TENANT_ID, allocId, expiry))
                .thenReturn(new BigDecimal("2.00"));
        // Total 6 days consumed as of 2026-08-01 (4 days taken after expiry)
        when(consumptionRepository.sumConsumedDaysByAllocation(TENANT_ID, allocId))
                .thenReturn(new BigDecimal("6.00"));

        Optional<LeaveBalanceResponse> balanceOpt =
                balanceService.getBalance(TENANT_ID, EMPLOYEE_ID, LEAVE_TYPE_ID, evalDate);
        assertThat(balanceOpt).isPresent();
        LeaveBalanceResponse balance = balanceOpt.get();

        // Effective carried forward is only the 2 days taken before expiry; the other 3 lapsed
        assertThat(balance.carriedForwardDays()).isEqualByComparingTo("2.00");
        assertThat(balance.consumedDays()).isEqualByComparingTo("6.00");
        // Remaining: 20 entitlement + 2 effective carried - 6 consumed = 16.00 (NOT 19.00!)
        assertThat(balance.remainingDays()).isEqualByComparingTo("16.00");
    }
}
