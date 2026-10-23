package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WfhBalanceServiceImpl implements WfhBalanceService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final WfhAllocationRepository allocationRepository;
    private final WfhConsumptionRepository consumptionRepository;
    private final WfhRequestRepository requestRepository;
    private final WfhPolicyRepository policyRepository;

    public WfhBalanceServiceImpl(
            WfhAllocationRepository allocationRepository,
            WfhConsumptionRepository consumptionRepository,
            WfhRequestRepository requestRepository,
            WfhPolicyRepository policyRepository) {
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.consumptionRepository = Objects.requireNonNull(consumptionRepository);
        this.requestRepository = Objects.requireNonNull(requestRepository);
        this.policyRepository = Objects.requireNonNull(policyRepository);
    }

    @Override
    public WfhBalanceSummaryResponse getBalanceSummary(UUID tenantId, UUID employeeId, LocalDate asOf) {
        Optional<WfhAllocation> allocationOpt =
                allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, asOf, asOf);

        if (allocationOpt.isEmpty()) {
            return new WfhBalanceSummaryResponse(
                    employeeId,
                    String.valueOf(asOf.getYear()),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }

        WfhAllocation allocation = allocationOpt.get();
        BigDecimal entitledDays = allocation.getEntitlementDays();
        BigDecimal consumedDays = consumptionRepository.sumConsumedDaysByAllocation(tenantId, allocation.getId());
        if (consumedDays == null) {
            consumedDays = BigDecimal.ZERO;
        }

        // Sum pending requests in this allocation year
        List<WfhRequest> employeeRequests =
                requestRepository.findByTenantIdAndEmployeeIdOrderByFromDateDesc(tenantId, employeeId);

        BigDecimal pendingDays = employeeRequests.stream()
                .filter(r -> r.getStatus() == WfhRequestStatus.PENDING)
                .filter(r -> !r.getFromDate().isBefore(allocation.getYearStartDate())
                        && !r.getToDate().isAfter(allocation.getYearEndDate()))
                .map(WfhRequest::getWorkingDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableDays = entitledDays.subtract(consumedDays).subtract(pendingDays);
        if (availableDays.compareTo(BigDecimal.ZERO) < 0) {
            availableDays = BigDecimal.ZERO;
        }

        // Monthly limit and monthly consumed
        String currentPeriod = asOf.format(PERIOD_FORMATTER);
        BigDecimal monthConsumed = consumptionRepository.sumConsumedDaysByPeriod(tenantId, employeeId, currentPeriod);
        if (monthConsumed == null) {
            monthConsumed = BigDecimal.ZERO;
        }

        BigDecimal monthlyLimit = policyRepository
                .findById(allocation.getPolicyId())
                .map(WfhPolicy::getMonthlyLimitDays)
                .orElse(new BigDecimal("2.00"));

        return new WfhBalanceSummaryResponse(
                employeeId,
                allocation.getWfhYear(),
                entitledDays,
                consumedDays,
                pendingDays,
                availableDays,
                monthlyLimit,
                monthConsumed);
    }

    @Override
    public BigDecimal getAvailableDays(UUID tenantId, UUID employeeId, LocalDate asOf) {
        return getBalanceSummary(tenantId, employeeId, asOf).availableDays();
    }

    @Override
    public boolean isWithinMonthlyLimit(UUID tenantId, UUID employeeId, LocalDate date, BigDecimal requestedDays) {
        Optional<WfhAllocation> allocationOpt =
                allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, date, date);

        if (allocationOpt.isEmpty()) {
            return false;
        }

        WfhAllocation allocation = allocationOpt.get();
        Optional<WfhPolicy> policyOpt = policyRepository.findById(allocation.getPolicyId());
        if (policyOpt.isEmpty() || policyOpt.get().getMonthlyLimitDays() == null) {
            return true; // No monthly cap configured
        }

        BigDecimal limit = policyOpt.get().getMonthlyLimitDays();
        String period = date.format(PERIOD_FORMATTER);

        BigDecimal monthConsumed = consumptionRepository.sumConsumedDaysByPeriod(tenantId, employeeId, period);
        if (monthConsumed == null) {
            monthConsumed = BigDecimal.ZERO;
        }

        // Sum pending requests for this period
        List<WfhRequest> requests =
                requestRepository.findByTenantIdAndEmployeeIdOrderByFromDateDesc(tenantId, employeeId);
        BigDecimal monthPending = requests.stream()
                .filter(r -> r.getStatus() == WfhRequestStatus.PENDING)
                .filter(r -> r.getFromDate().format(PERIOD_FORMATTER).equals(period))
                .map(WfhRequest::getWorkingDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projectedTotal = monthConsumed.add(monthPending).add(requestedDays);
        return projectedTotal.compareTo(limit) <= 0;
    }
}
