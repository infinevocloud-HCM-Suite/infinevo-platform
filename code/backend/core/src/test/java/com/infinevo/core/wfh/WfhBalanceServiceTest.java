package com.infinevo.core.wfh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WfhBalanceServiceTest {

    private WfhAllocationRepository allocationRepository;
    private WfhConsumptionRepository consumptionRepository;
    private WfhRequestRepository requestRepository;
    private WfhPolicyRepository policyRepository;
    private WfhBalanceServiceImpl balanceService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        allocationRepository = mock(WfhAllocationRepository.class);
        consumptionRepository = mock(WfhConsumptionRepository.class);
        requestRepository = mock(WfhRequestRepository.class);
        policyRepository = mock(WfhPolicyRepository.class);

        balanceService = new WfhBalanceServiceImpl(
                allocationRepository, consumptionRepository, requestRepository, policyRepository);
    }

    @Test
    @DisplayName("Returns 0 balance summary when no allocation exists for the year")
    void balanceSummaryWhenNoAllocationExists() {
        LocalDate asOf = LocalDate.of(2026, 6, 15);
        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, asOf, asOf))
                .thenReturn(Optional.empty());

        WfhBalanceSummaryResponse summary = balanceService.getBalanceSummary(tenantId, employeeId, asOf);
        assertThat(summary.entitledDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.consumedDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.pendingDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.availableDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Calculates availableDays = entitled - consumed - pending accurately")
    void balanceSummaryWithAllocationAndDeductions() {
        LocalDate asOf = LocalDate.of(2026, 6, 15);
        UUID allocationId = UUID.randomUUID();

        WfhAllocation allocation = new WfhAllocation();
        allocation.setId(allocationId);
        allocation.setTenantId(tenantId);
        allocation.setEmployeeId(employeeId);
        allocation.setPolicyId(policyId);
        allocation.setWfhYear("2026");
        allocation.setYearStartDate(LocalDate.of(2026, 1, 1));
        allocation.setYearEndDate(LocalDate.of(2026, 12, 31));
        allocation.setEntitlementDays(new BigDecimal("12.00"));

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, asOf, asOf))
                .thenReturn(Optional.of(allocation));

        // 3 consumed days
        when(consumptionRepository.sumConsumedDaysByAllocation(tenantId, allocationId))
                .thenReturn(new BigDecimal("3.00"));

        // 2 pending days
        WfhRequest pendingReq = new WfhRequest();
        pendingReq.setTenantId(tenantId);
        pendingReq.setEmployeeId(employeeId);
        pendingReq.setFromDate(LocalDate.of(2026, 7, 1));
        pendingReq.setToDate(LocalDate.of(2026, 7, 2));
        pendingReq.setWorkingDays(new BigDecimal("2.00"));
        pendingReq.setStatus(WfhRequestStatus.PENDING);

        when(requestRepository.findByTenantIdAndEmployeeIdOrderByFromDateDesc(tenantId, employeeId))
                .thenReturn(List.of(pendingReq));

        // Month consumed = 1.00
        when(consumptionRepository.sumConsumedDaysByPeriod(tenantId, employeeId, "2026-06"))
                .thenReturn(new BigDecimal("1.00"));

        WfhPolicy policy = new WfhPolicy();
        policy.setId(policyId);
        policy.setMonthlyLimitDays(new BigDecimal("2.00"));
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        WfhBalanceSummaryResponse summary = balanceService.getBalanceSummary(tenantId, employeeId, asOf);
        assertThat(summary.entitledDays()).isEqualByComparingTo("12.00");
        assertThat(summary.consumedDays()).isEqualByComparingTo("3.00");
        assertThat(summary.pendingDays()).isEqualByComparingTo("2.00");
        // 12 - 3 - 2 = 7 available
        assertThat(summary.availableDays()).isEqualByComparingTo("7.00");
        assertThat(summary.monthlyLimitDays()).isEqualByComparingTo("2.00");
        assertThat(summary.monthConsumedDays()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("isWithinMonthlyLimit validates monthly velocity correctly")
    void isWithinMonthlyLimitValidation() {
        LocalDate date = LocalDate.of(2026, 5, 10);
        UUID allocationId = UUID.randomUUID();

        WfhAllocation allocation = new WfhAllocation();
        allocation.setId(allocationId);
        allocation.setPolicyId(policyId);
        allocation.setYearStartDate(LocalDate.of(2026, 1, 1));
        allocation.setYearEndDate(LocalDate.of(2026, 12, 31));

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, date, date))
                .thenReturn(Optional.of(allocation));

        WfhPolicy policy = new WfhPolicy();
        policy.setId(policyId);
        policy.setMonthlyLimitDays(new BigDecimal("2.00"));
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        // 1 day already consumed in 2026-05
        when(consumptionRepository.sumConsumedDaysByPeriod(tenantId, employeeId, "2026-05"))
                .thenReturn(new BigDecimal("1.00"));
        when(requestRepository.findByTenantIdAndEmployeeIdOrderByFromDateDesc(tenantId, employeeId))
                .thenReturn(List.of());

        // Requesting 1.00 day -> 1 + 1 = 2 <= 2 (Allowed)
        assertThat(balanceService.isWithinMonthlyLimit(tenantId, employeeId, date, new BigDecimal("1.00")))
                .isTrue();

        // Requesting 1.50 days -> 1 + 1.5 = 2.5 > 2 (Exceeds monthly velocity)
        assertThat(balanceService.isWithinMonthlyLimit(tenantId, employeeId, date, new BigDecimal("1.50")))
                .isFalse();
    }
}
