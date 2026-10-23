package com.infinevo.core.wfh;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WfhAllocationServiceImpl implements WfhAllocationService {

    private final WfhAllocationRepository allocationRepository;
    private final WfhPolicyRepository policyRepository;
    private final WfhPolicyService policyService;
    private final EmployeeRepository employeeRepository;

    public WfhAllocationServiceImpl(
            WfhAllocationRepository allocationRepository,
            WfhPolicyRepository policyRepository,
            WfhPolicyService policyService,
            EmployeeRepository employeeRepository) {
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.policyRepository = Objects.requireNonNull(policyRepository);
        this.policyService = Objects.requireNonNull(policyService);
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
    }

    @Override
    public WfhAllocationResponse createAllocation(UUID tenantId, CreateWfhAllocationRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(request.employeeId(), tenantId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found: " + request.employeeId()));

        WfhPolicy policy;
        if (request.policyId() != null) {
            policy = policyRepository
                    .findById(request.policyId())
                    .filter(p -> p.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new NoSuchElementException("WFH Policy not found: " + request.policyId()));
        } else {
            policy = policyService.getOrCreateDefaultPolicy(tenantId);
        }

        if (request.yearStartDate().isAfter(request.yearEndDate())) {
            throw new IllegalArgumentException("yearStartDate must be before or equal to yearEndDate");
        }

        if (allocationRepository.existsOverlappingAllocation(
                tenantId, request.employeeId(), request.yearStartDate(), request.yearEndDate())) {
            throw new IllegalArgumentException("An overlapping WFH allocation already exists for this employee.");
        }

        BigDecimal proRateFactor = request.proRateFactor() != null
                ? request.proRateFactor()
                : BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

        WfhAllocation allocation = new WfhAllocation();
        allocation.setTenantId(tenantId);
        allocation.setEmployeeId(request.employeeId());
        allocation.setPolicyId(policy.getId());
        allocation.setWfhYear(request.wfhYear().trim());
        allocation.setYearStartDate(request.yearStartDate());
        allocation.setYearEndDate(request.yearEndDate());
        allocation.setEntitlementDays(request.entitlementDays().setScale(2, RoundingMode.HALF_UP));
        allocation.setProRateFactor(proRateFactor);

        WfhAllocation saved = allocationRepository.save(allocation);
        String employeeName =
                employee.getFirstName() + " " + (employee.getLastName() != null ? employee.getLastName() : "");
        return WfhAllocationResponse.from(saved, employeeName.trim(), employee.getEmployeeNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WfhAllocationResponse> listAllocations(UUID tenantId, UUID employeeId, String year) {
        List<WfhAllocation> list;
        if (employeeId != null) {
            list = allocationRepository.findByTenantIdAndEmployeeIdOrderByYearStartDateDesc(tenantId, employeeId);
        } else if (year != null && !year.isBlank()) {
            list = allocationRepository.findByTenantIdAndWfhYear(tenantId, year.trim());
        } else {
            list = allocationRepository.findByTenantId(tenantId);
        }

        return list.stream()
                .map(a -> {
                    Employee emp = employeeRepository
                            .findByIdAndTenantIdAndDeletedFalse(a.getEmployeeId(), tenantId)
                            .orElse(null);
                    String name = emp != null
                            ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim()
                            : "Unknown";
                    String number = emp != null ? emp.getEmployeeNumber() : "";
                    return WfhAllocationResponse.from(a, name, number);
                })
                .toList();
    }

    @Override
    public BigDecimal calculateProRatedDays(BigDecimal annualQuota, LocalDate joiningDate, LocalDate yearEndDate) {
        if (joiningDate == null || yearEndDate == null || annualQuota == null) {
            return annualQuota != null ? annualQuota : BigDecimal.ZERO;
        }

        // Calculate remaining months inclusive of joining month
        int remainingMonths = 12 - joiningDate.getMonthValue() + 1;
        if (remainingMonths <= 0) {
            remainingMonths = 1;
        }
        if (remainingMonths > 12) {
            remainingMonths = 12;
        }

        BigDecimal factor = BigDecimal.valueOf(remainingMonths).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);

        return annualQuota.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public WfhAllocationResponse allocateForNewJoiner(UUID tenantId, UUID employeeId, LocalDate joiningDate) {
        WfhPolicy policy = policyService.getOrCreateDefaultPolicy(tenantId);
        LocalDate yearStart = LocalDate.of(joiningDate.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(joiningDate.getYear(), 12, 31);
        String year = String.valueOf(joiningDate.getYear());

        BigDecimal proRatedDays = calculateProRatedDays(policy.getAnnualDays(), joiningDate, yearEnd);
        BigDecimal factor = proRatedDays.divide(policy.getAnnualDays(), 4, RoundingMode.HALF_UP);

        CreateWfhAllocationRequest request = new CreateWfhAllocationRequest(
                employeeId, policy.getId(), year, yearStart, yearEnd, proRatedDays, factor);

        return createAllocation(tenantId, request);
    }
}
