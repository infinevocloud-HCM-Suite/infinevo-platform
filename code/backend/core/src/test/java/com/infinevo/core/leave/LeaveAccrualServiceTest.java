package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link LeaveAccrualService} (W-16.2, spec section 7).
 * Covers:
 * - monthly frequency accrual
 * - yearly frequency accrual
 * - re-running for the same period accrues nothing twice (idempotence)
 * - disabled accrual skips processing
 */
class LeaveAccrualServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID LEAVE_TYPE_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();

    private LeaveAllocationRepository allocationRepository;
    private LeavePolicyRepository policyRepository;
    private EmployeeRepository employeeRepository;
    private LeaveAccrualService accrualService;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(LeaveAllocationRepository.class);
        policyRepository = mock(LeavePolicyRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        accrualService = new LeaveAccrualServiceImpl(allocationRepository, policyRepository, employeeRepository);
    }

    @Test
    @DisplayName("Monthly accrual adds units and is idempotent within the same month")
    void monthlyAccrualAndIdempotence() {
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(21),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setAccrualEnabled(true);
        policy.setAccrualFrequency(AccrualFrequency.MONTHLY);
        policy.setAccrualUnits(BigDecimal.valueOf(1.75));

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        // First accrual in January
        LocalDate jan15 = LocalDate.of(2026, 1, 15);
        boolean firstAccrued = accrualService.accrueAllocation(TENANT_ID, allocation, jan15);
        assertThat(firstAccrued).isTrue();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(1.75));
        assertThat(allocation.getLastAccruedOn()).isEqualTo(jan15);

        // Re-running in same month (January 20) accrues nothing twice
        LocalDate jan20 = LocalDate.of(2026, 1, 20);
        boolean duplicateAccrued = accrualService.accrueAllocation(TENANT_ID, allocation, jan20);
        assertThat(duplicateAccrued).isFalse();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(1.75));

        // Accrual in February adds another 1.75
        LocalDate feb1 = LocalDate.of(2026, 2, 1);
        boolean febAccrued = accrualService.accrueAllocation(TENANT_ID, allocation, feb1);
        assertThat(febAccrued).isTrue();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(3.50));
        assertThat(allocation.getLastAccruedOn()).isEqualTo(feb1);
    }

    @Test
    @DisplayName("Yearly accrual adds units once and is idempotent within the same year")
    void yearlyAccrualAndIdempotence() {
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(18),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setAccrualEnabled(true);
        policy.setAccrualFrequency(AccrualFrequency.YEARLY);
        policy.setAccrualUnits(BigDecimal.valueOf(18));

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        // First accrual in year 2026
        LocalDate jan1 = LocalDate.of(2026, 1, 1);
        boolean firstAccrued = accrualService.accrueAllocation(TENANT_ID, allocation, jan1);
        assertThat(firstAccrued).isTrue();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(18));
        assertThat(allocation.getLastAccruedOn()).isEqualTo(jan1);

        // Re-running later in 2026 accrues nothing twice
        LocalDate jul1 = LocalDate.of(2026, 7, 1);
        boolean duplicateAccrued = accrualService.accrueAllocation(TENANT_ID, allocation, jul1);
        assertThat(duplicateAccrued).isFalse();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(18));
    }

    @Test
    @DisplayName("Disabled accrual skips allocation")
    void disabledAccrualSkips() {
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
        policy.setAccrualEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        boolean accrued = accrualService.accrueAllocation(TENANT_ID, allocation, LocalDate.of(2026, 1, 15));
        assertThat(accrued).isFalse();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Monthly accrual respects joining date and pro-rates partial joining month (F-6)")
    void monthlyAccrualRespectsJoiningDateAndProRates() {
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setAccrualEnabled(true);
        policy.setAccrualFrequency(AccrualFrequency.MONTHLY);
        policy.setAccrualUnits(BigDecimal.valueOf(2.00));
        policy.setProRateEnabled(true);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        Employee emp = mock(Employee.class);
        // Employee joins April 16, 2026 (April has 30 days -> active days: 30 - 16 + 1 = 15; factor = 0.5000)
        when(emp.getDateOfJoining()).thenReturn(LocalDate.of(2026, 4, 16));
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(EMPLOYEE_ID, TENANT_ID))
                .thenReturn(Optional.of(emp));

        // Accrual in March 2026 should do nothing (employee not joined yet)
        boolean marchAccrual = accrualService.accrueAllocation(TENANT_ID, allocation, LocalDate.of(2026, 3, 31));
        assertThat(marchAccrual).isFalse();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.ZERO);

        // Accrual in April 2026 should pro-rate (2.00 * 0.5000 = 1.00)
        boolean aprilAccrual = accrualService.accrueAllocation(TENANT_ID, allocation, LocalDate.of(2026, 4, 30));
        assertThat(aprilAccrual).isTrue();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(1.00));

        // Accrual in May 2026 should add full units (2.00 -> total 3.00)
        boolean mayAccrual = accrualService.accrueAllocation(TENANT_ID, allocation, LocalDate.of(2026, 5, 31));
        assertThat(mayAccrual).isTrue();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(3.00));
    }

    @Test
    @DisplayName("Monthly accrual respects termination date and skips subsequent months (F-6)")
    void monthlyAccrualRespectsTerminationDate() {
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_ID,
                EMPLOYEE_ID,
                LEAVE_TYPE_ID,
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ONE,
                POLICY_ID);

        LeavePolicy policy = new LeavePolicy();
        policy.setId(POLICY_ID);
        policy.setAccrualEnabled(true);
        policy.setAccrualFrequency(AccrualFrequency.MONTHLY);
        policy.setAccrualUnits(BigDecimal.valueOf(2.00));
        policy.setProRateEnabled(false);

        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        Employee emp = mock(Employee.class);
        when(emp.getDateOfJoining()).thenReturn(LocalDate.of(2026, 1, 1));
        // Employee terminated on February 15, 2026
        when(emp.getTerminationDate()).thenReturn(LocalDate.of(2026, 2, 15));
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(EMPLOYEE_ID, TENANT_ID))
                .thenReturn(Optional.of(emp));

        // Accrual in February (covers Jan and Feb = 2 * 2.00 = 4.00)
        boolean febAccrual = accrualService.accrueAllocation(TENANT_ID, allocation, LocalDate.of(2026, 2, 28));
        assertThat(febAccrual).isTrue();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(4.00));

        // Accrual in March should return false because targetMonth is capped at termination month (Feb)
        boolean marchAccrual = accrualService.accrueAllocation(TENANT_ID, allocation, LocalDate.of(2026, 3, 31));
        assertThat(marchAccrual).isFalse();
        assertThat(allocation.getAccruedDays()).isEqualByComparingTo(BigDecimal.valueOf(4.00));
    }
}
