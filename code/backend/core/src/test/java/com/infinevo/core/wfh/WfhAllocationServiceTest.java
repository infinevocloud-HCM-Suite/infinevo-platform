package com.infinevo.core.wfh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

class WfhAllocationServiceTest {

    private WfhAllocationRepository allocationRepository;
    private WfhPolicyRepository policyRepository;
    private WfhPolicyService policyService;
    private EmployeeRepository employeeRepository;

    private WfhAllocationServiceImpl allocationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();
    private Employee employee;

    @BeforeEach
    void setUp() {
        allocationRepository = mock(WfhAllocationRepository.class);
        policyRepository = mock(WfhPolicyRepository.class);
        policyService = mock(WfhPolicyService.class);
        employeeRepository = mock(EmployeeRepository.class);

        allocationService =
                new WfhAllocationServiceImpl(allocationRepository, policyRepository, policyService, employeeRepository);

        employee = mock(Employee.class);
        when(employee.getId()).thenReturn(employeeId);
        when(employee.getTenantId()).thenReturn(tenantId);
        when(employee.getFirstName()).thenReturn("Krushna");
        when(employee.getLastName()).thenReturn("Sonalkar");
        when(employee.getEmployeeNumber()).thenReturn("EMP001");

        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(employee));
    }

    @Test
    @DisplayName("calculateProRatedDays calculates remaining months inclusive of joining month")
    void calculateProRatedDaysMidYear() {
        // Joining on July 1st: July to December = 6 months remaining
        // Annual quota = 12.00 days -> 6/12 * 12 = 6.00 days
        LocalDate joiningDate = LocalDate.of(2026, 7, 1);
        LocalDate yearEnd = LocalDate.of(2026, 12, 31);
        BigDecimal annualQuota = new BigDecimal("12.00");

        BigDecimal proRated = allocationService.calculateProRatedDays(annualQuota, joiningDate, yearEnd);
        assertThat(proRated).isEqualByComparingTo("6.00");

        // Joining on October 15th: Oct, Nov, Dec = 3 months
        // 3/12 * 12 = 3.00 days
        BigDecimal q4ProRated =
                allocationService.calculateProRatedDays(annualQuota, LocalDate.of(2026, 10, 15), yearEnd);
        assertThat(q4ProRated).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("createAllocation with overlapping period throws IllegalArgumentException")
    void createAllocationOverlappingThrows() {
        WfhPolicy policy = new WfhPolicy();
        policy.setId(policyId);
        policy.setTenantId(tenantId);
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        when(allocationRepository.existsOverlappingAllocation(tenantId, employeeId, start, end))
                .thenReturn(true);

        CreateWfhAllocationRequest request = new CreateWfhAllocationRequest(
                employeeId, policyId, "2026", start, end, new BigDecimal("12.00"), BigDecimal.ONE);

        assertThatThrownBy(() -> allocationService.createAllocation(tenantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlapping WFH allocation already exists");
    }

    @Test
    @DisplayName("createAllocation creates allocation entity and returns response")
    void createAllocationSuccess() {
        WfhPolicy policy = new WfhPolicy();
        policy.setId(policyId);
        policy.setTenantId(tenantId);
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        when(allocationRepository.existsOverlappingAllocation(tenantId, employeeId, start, end))
                .thenReturn(false);

        when(allocationRepository.save(any(WfhAllocation.class))).thenAnswer(inv -> {
            WfhAllocation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        CreateWfhAllocationRequest request = new CreateWfhAllocationRequest(
                employeeId, policyId, "2026", start, end, new BigDecimal("12.00"), BigDecimal.ONE);

        WfhAllocationResponse res = allocationService.createAllocation(tenantId, request);
        assertThat(res).isNotNull();
        assertThat(res.entitlementDays()).isEqualByComparingTo("12.00");
        assertThat(res.employeeName()).isEqualTo("Krushna Sonalkar");
    }
}
