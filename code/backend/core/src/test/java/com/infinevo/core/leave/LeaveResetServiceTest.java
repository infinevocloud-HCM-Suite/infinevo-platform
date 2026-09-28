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
 * Unit test for {@link LeaveResetService} (W-16.2, spec section 7).
 * Covers:
 * - yearly, monthly, quarterly and halfYearly boundaries
 * - unused balance drops at boundary when carry-forward is disabled
 * - unused balance carries forward up to cap when carry-forward is enabled
 * - carry forward expiry calculation
 * - re-running for the same boundary resets nothing twice (last_reset_on idempotence)
 */
class LeaveResetServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID LEAVE_TYPE_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();

    private LeaveAllocationRepository allocationRepository;
    private LeavePolicyRepository policyRepository;
    private LeaveResetService resetService;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(LeaveAllocationRepository.class);
        policyRepository = mock(LeavePolicyRepository.class);
        resetService = new LeaveResetServiceImpl(allocationRepository, policyRepository);
    }

    @Test
    @DisplayName("Yearly boundary drops unused balance when carry-forward is disabled")
    void yearlyBoundaryDropsUnusedBalanceWithoutCarryForward() {
        LeaveAllocation allocation = new LeaveAllocation(
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

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.YEARLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        LocalDate yearEnd = LocalDate.of(2026, 12, 31);
        boolean reset = resetService.resetAllocation(TENANT_ID, allocation, yearEnd, BigDecimal.valueOf(10));
        assertThat(reset).isTrue();
        // total 20 + 5 - 10 consumed = 15 unused, but carry-forward is disabled -> 0
        assertThat(allocation.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(allocation.getLastResetOn()).isEqualTo(yearEnd);

        // Re-running for the same year does nothing (idempotent)
        boolean duplicateReset = resetService.resetAllocation(TENANT_ID, allocation, yearEnd, BigDecimal.valueOf(10));
        assertThat(duplicateReset).isFalse();
    }

    @Test
    @DisplayName("Yearly boundary carries forward up to cap with expiration")
    void yearlyBoundaryCarriesForwardUpToCap() {
        LeaveAllocation allocation = new LeaveAllocation(
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

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.YEARLY);
        policy.setCarryForwardEnabled(true);
        policy.setCarryForwardCap(BigDecimal.valueOf(5));
        policy.setCarryForwardExpiresAfterMonths(3);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        LocalDate yearEnd = LocalDate.of(2026, 12, 31);
        // 20 entitlement - 8 consumed = 12 unused. Capped at 5!
        boolean reset = resetService.resetAllocation(TENANT_ID, allocation, yearEnd, BigDecimal.valueOf(8));
        assertThat(reset).isTrue();
        assertThat(allocation.getCarriedForwardDays()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(allocation.getCarryForwardExpiresOn()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    @DisplayName("Monthly reset boundary and idempotence")
    void monthlyResetBoundary() {
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

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.MONTHLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        boolean reset = resetService.resetAllocation(TENANT_ID, allocation, jan31, BigDecimal.ZERO);
        assertThat(reset).isTrue();
        assertThat(allocation.getLastResetOn()).isEqualTo(jan31);

        // Duplicate in same month
        boolean duplicate = resetService.resetAllocation(TENANT_ID, allocation, jan31, BigDecimal.ZERO);
        assertThat(duplicate).isFalse();

        // February reset succeeds
        LocalDate feb28 = LocalDate.of(2026, 2, 28);
        boolean febReset = resetService.resetAllocation(TENANT_ID, allocation, feb28, BigDecimal.ZERO);
        assertThat(febReset).isTrue();
        assertThat(allocation.getLastResetOn()).isEqualTo(feb28);
    }

    @Test
    @DisplayName("Quarterly reset boundary and idempotence")
    void quarterlyResetBoundary() {
        LeaveAllocation allocation = new LeaveAllocation(
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

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.QUARTERLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        LocalDate mar31 = LocalDate.of(2026, 3, 31); // Q1
        boolean q1Reset = resetService.resetAllocation(TENANT_ID, allocation, mar31, BigDecimal.ZERO);
        assertThat(q1Reset).isTrue();

        // Duplicate in same quarter (Q1)
        boolean dup = resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 2, 28), BigDecimal.ZERO);
        assertThat(dup).isFalse();

        // Q2 reset succeeds
        LocalDate jun30 = LocalDate.of(2026, 6, 30); // Q2
        boolean q2Reset = resetService.resetAllocation(TENANT_ID, allocation, jun30, BigDecimal.ZERO);
        assertThat(q2Reset).isTrue();
    }

    @Test
    @DisplayName("Half-yearly reset boundary and idempotence")
    void halfYearlyResetBoundary() {
        LeaveAllocation allocation = new LeaveAllocation(
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

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setResetEnabled(true);
        policy.setResetFrequency(ResetFrequency.HALF_YEARLY);
        policy.setCarryForwardEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        LocalDate jun30 = LocalDate.of(2026, 6, 30); // H1
        boolean h1Reset = resetService.resetAllocation(TENANT_ID, allocation, jun30, BigDecimal.ZERO);
        assertThat(h1Reset).isTrue();

        // Duplicate in H1
        boolean dup = resetService.resetAllocation(TENANT_ID, allocation, LocalDate.of(2026, 5, 15), BigDecimal.ZERO);
        assertThat(dup).isFalse();

        // H2 reset succeeds
        LocalDate dec31 = LocalDate.of(2026, 12, 31); // H2
        boolean h2Reset = resetService.resetAllocation(TENANT_ID, allocation, dec31, BigDecimal.ZERO);
        assertThat(h2Reset).isTrue();
    }
}
