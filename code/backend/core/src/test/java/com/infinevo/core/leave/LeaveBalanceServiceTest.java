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
    private LeaveBalanceService balanceService;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(LeaveAllocationRepository.class);
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        balanceService = new LeaveBalanceServiceImpl(allocationRepository, leaveTypeRepository);

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
}
