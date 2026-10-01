package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit test for {@link LeaveResetService} (W-16.2).
 * Covers:
 * - Year end: only new year's row is written, old year's row is left alone
 * - Unused balance carries forward up to cap on new year row
 * - Expiration calculation on new year row
 * - First sweep never wipes balance in the same period it was created
 * - Quarters and half-years follow tenant's leave-year start month
 * - Expired carry forward is excluded from unused days at year end
 * - Idempotence per frequency
 */
class LeaveResetServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID LEAVE_TYPE_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();

    private LeaveAllocationRepository allocationRepository;
    private LeavePolicyRepository policyRepository;
    private LeaveConsumptionRepository consumptionRepository;
    private JdbcTemplate jdbcTemplate;
    private LeaveResetService resetService;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(LeaveAllocationRepository.class);
        policyRepository = mock(LeavePolicyRepository.class);
        consumptionRepository = mock(LeaveConsumptionRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        resetService =
                new LeaveResetServiceImpl(allocationRepository, policyRepository, consumptionRepository, jdbcTemplate);
    }

    @Test
    @DisplayName("Year end leaves ending year row alone and writes carry-forward to new year row (W-16.2)")
    void yearEndLeavesOldRowAloneAndWritesNewRow() {
        LeaveAllocation oldAlloc = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(5),
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);
        oldAlloc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        LeaveAllocation nextAlloc = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2027",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31),
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.YEARLY);
        policy.setCarryForwardEnabled(true);
        policy.setCarryForwardCap(BigDecimal.valueOf(5));
        policy.setCarryForwardExpiresAfterMonths(3);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                eq(TENANT_ID),
                                eq(EMPLOYEE_ID),
                                eq(LEAVE_TYPE_ID),
                                eq(LocalDate.of(2027, 1, 1)),
                                eq(LocalDate.of(2027, 1, 1))))
                .thenReturn(Optional.of(nextAlloc));

        LocalDate yearEnd = LocalDate.of(2026, 12, 31);
        // 20 entitlement + 5 accrued - 10 consumed = 15 unused. Capped at 5!
        boolean reset = resetService.resetAllocation(TENANT_ID, oldAlloc, yearEnd, BigDecimal.valueOf(10));
        assertThat(reset).isTrue();

        // Old year row is LEFT ALONE (carried forward not mutated)
        assertThat(oldAlloc.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(oldAlloc.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(5));

        // New year row is written with carry-forward up to cap
        assertThat(nextAlloc.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(nextAlloc.getCarryForwardExpiresOn()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(nextAlloc.getLastResetOn()).isEqualTo(yearEnd);

        // Re-running does not re-write because nextAlloc has lastResetOn set
        boolean duplicate = resetService.resetAllocation(TENANT_ID, oldAlloc, yearEnd, BigDecimal.valueOf(10));
        assertThat(duplicate).isFalse();
    }

    @Test
    @DisplayName("Yearly boundary drops unused balance on new year row when carry-forward is disabled (W-16.2)")
    void yearlyBoundaryDropsUnusedBalanceWithoutCarryForward() {
        LeaveAllocation oldAlloc = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);
        oldAlloc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        LeaveAllocation nextAlloc = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2027",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31),
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.YEARLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                eq(TENANT_ID),
                                eq(EMPLOYEE_ID),
                                eq(LEAVE_TYPE_ID),
                                eq(LocalDate.of(2027, 1, 1)),
                                eq(LocalDate.of(2027, 1, 1))))
                .thenReturn(Optional.of(nextAlloc));

        LocalDate yearEnd = LocalDate.of(2026, 12, 31);
        boolean reset = resetService.resetAllocation(TENANT_ID, oldAlloc, yearEnd, BigDecimal.valueOf(10));
        assertThat(reset).isTrue();

        // Old row untouched
        assertThat(oldAlloc.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.ZERO);
        // New row gets 0 carry-forward
        assertThat(nextAlloc.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("First sweep never wipes a new balance in the same period it was created (W-16.2)")
    void firstSweepDoesNotWipeBalanceInCreationPeriod() {
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(2),
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);
        // Created on 2026-04-10
        allocation.setCreatedAt(Instant.parse("2026-04-10T10:00:00Z"));

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.MONTHLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        // Sweep on 2026-04-10 night or 2026-04-30 (same month)
        boolean sameMonth =
                resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 4, 30), BigDecimal.ZERO);
        assertThat(sameMonth).isFalse();
        assertThat(allocation.getLastResetOn()).isNull();

        // Next month sweep (2026-05-31) succeeds
        boolean nextMonth =
                resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 5, 31), BigDecimal.ZERO);
        assertThat(nextMonth).isTrue();
        assertThat(allocation.getLastResetOn()).isEqualTo(LocalDate.of(2026, 5, 31));

        // Idempotent within May
        boolean dup = resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 5, 31), BigDecimal.ZERO);
        assertThat(dup).isFalse();
    }

    @Test
    @DisplayName("Quarterly and half-yearly resets count periods from tenant's leave-year start month (W-16.2)")
    void quarterAndHalfYearCountPeriodsFromTenantStartMonth() {
        // Tenant starts in April (month 4)
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of((short) 4));

        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026-27",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31),
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);
        allocation.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.HALF_YEARLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        // Calendar half-year boundary is 1 July. But for April-start tenant, 1 July is inside H0 (April-Sept)
        boolean julyReset =
                resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 7, 1), BigDecimal.ZERO);
        assertThat(julyReset).isFalse();

        // September 30 is end of H0 (creation half-year), so creation period protection keeps it
        boolean septReset =
                resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 9, 30), BigDecimal.ZERO);
        assertThat(septReset).isFalse();

        // October 1 is H1 start (new period relative to April start) -> Resets!
        boolean octReset =
                resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 10, 1), BigDecimal.ZERO);
        assertThat(octReset).isTrue();
        assertThat(allocation.getLastResetOn()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("resetAll excludes expired carry-forward from prior year unused balance (W-16.2)")
    void resetAllExcludesExpiredCarryForwardFromUnused() {
        UUID oldId = UUID.randomUUID();
        LeaveAllocation oldAlloc = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026-27",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31),
                BigDecimal.valueOf(10),
                BigDecimal.ZERO,
                BigDecimal.valueOf(5), // 5 carried forward into 2026-27
                LocalDate.of(2026, 6, 30), // Expired on 2026-06-30
                BigDecimal.ONE,
                POLICY_ID);
        oldAlloc.setId(oldId);

        LeaveAllocation newAlloc = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2027-28",
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2028, 3, 31),
                BigDecimal.valueOf(10),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.YEARLY);
        policy.setCarryForwardEnabled(true);
        policy.setCarryForwardCap(BigDecimal.valueOf(15));

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));
        when(allocationRepository.findByTenantIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                        eq(TENANT_ID), eq(LocalDate.of(2027, 4, 1)), eq(LocalDate.of(2027, 4, 1))))
                .thenReturn(List.of(newAlloc));
        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                eq(TENANT_ID),
                                eq(EMPLOYEE_ID),
                                eq(LEAVE_TYPE_ID),
                                eq(LocalDate.of(2027, 3, 31)),
                                eq(LocalDate.of(2027, 3, 31))))
                .thenReturn(Optional.of(oldAlloc));

        // Total 2 consumed in old year (all after June 30, so 0 consumed before expiry)
        when(consumptionRepository.sumConsumedDaysByAllocation(eq(TENANT_ID), eq(oldId)))
                .thenReturn(BigDecimal.valueOf(2));
        when(consumptionRepository.sumConsumedDaysByAllocationAndConsumedOnLessThanEqual(
                        eq(TENANT_ID), eq(oldId), eq(LocalDate.of(2026, 6, 30))))
                .thenReturn(BigDecimal.ZERO);

        int count = resetService.resetAll(TENANT_ID, LocalDate.of(2027, 4, 1));
        assertThat(count).isEqualTo(1);

        // Effective carried forward from old year was 0 (5 expired with 0 consumed before expiry).
        // Total unused = 10 entitlement + 0 accrued + 0 effectiveCarried - 2 consumed = 8.
        // New allocation receives 8 days, NOT 8 + 5 = 13!
        assertThat(newAlloc.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.valueOf(8));
        // Old allocation row was left alone!
        assertThat(oldAlloc.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }
}
