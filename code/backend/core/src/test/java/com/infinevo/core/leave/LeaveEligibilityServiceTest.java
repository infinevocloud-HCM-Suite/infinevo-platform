package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.Department;
import com.infinevo.core.org.Designation;
import com.infinevo.core.org.WorkLocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link LeaveEligibilityService} (W-16.1, spec section 7).
 * Asserts:
 * <ul>
 *   <li>Each dimension filters</li>
 *   <li>No eligibility rows means everyone is eligible</li>
 *   <li>Combined dimensions are AND, not OR</li>
 * </ul>
 */
class LeaveEligibilityServiceTest {

    private LeaveTypeRepository leaveTypeRepository;
    private LeavePolicyRepository leavePolicyRepository;
    private LeavePolicyEligibilityRepository eligibilityRepository;
    private EmployeeRepository employeeRepository;
    private LeaveEligibilityService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 9, 28);

    private final UUID deptA = UUID.randomUUID();
    private final UUID deptB = UUID.randomUUID();
    private final UUID desigA = UUID.randomUUID();
    private final UUID desigB = UUID.randomUUID();
    private final UUID locA = UUID.randomUUID();
    private final UUID locB = UUID.randomUUID();

    private LeaveType leaveType;
    private LeavePolicy policy;

    @BeforeEach
    void setUp() {
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        leavePolicyRepository = mock(LeavePolicyRepository.class);
        eligibilityRepository = mock(LeavePolicyEligibilityRepository.class);
        employeeRepository = mock(EmployeeRepository.class);

        service = new LeaveEligibilityServiceImpl(
                leaveTypeRepository, leavePolicyRepository, eligibilityRepository, employeeRepository);

        leaveType = new LeaveType(
                tenantId, "AL", "Annual Leave", true, LeaveUnit.DAYS, true, LocalDate.of(2026, 1, 1), null, true);
        leaveType.setId(leaveTypeId);

        policy = new LeavePolicy();
        policy.setId(policyId);
        policy.setTenantId(tenantId);
        policy.setLeaveTypeId(leaveTypeId);
        policy.setAnnualDays(BigDecimal.valueOf(20));
        policy.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
        policy.setEffectiveFrom(LocalDate.of(2026, 1, 1));

        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, today))
                .thenReturn(Optional.of(policy));
    }

    private Employee createEmployee(Department dept, Designation desig, WorkLocation loc, String gender) {
        Employee emp = mock(Employee.class);
        when(emp.getTenantId()).thenReturn(tenantId);
        when(emp.getDepartment()).thenReturn(dept);
        when(emp.getDesignation()).thenReturn(desig);
        when(emp.getWorkLocation()).thenReturn(loc);
        when(emp.getGender()).thenReturn(gender);
        return emp;
    }

    private Department createDepartment(UUID id) {
        Department d = mock(Department.class);
        when(d.getId()).thenReturn(id);
        return d;
    }

    private Designation createDesignation(UUID id) {
        Designation d = mock(Designation.class);
        when(d.getId()).thenReturn(id);
        return d;
    }

    private WorkLocation createWorkLocation(UUID id) {
        WorkLocation w = mock(WorkLocation.class);
        when(w.getId()).thenReturn(id);
        return w;
    }

    @Test
    @DisplayName("no eligibility rows means everyone is eligible")
    void noEligibilityRowsMeansEveryoneIsEligible() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of());

        Employee emp1 =
                createEmployee(createDepartment(deptA), createDesignation(desigA), createWorkLocation(locA), "Male");
        Employee emp2 = createEmployee(null, null, null, null);

        assertThat(service.isEligible(emp1, leaveType, today)).isTrue();
        assertThat(service.isEligible(emp2, leaveType, today)).isTrue();
    }

    @Test
    @DisplayName("department dimension filters: only employees in matching department are eligible")
    void departmentDimensionFilters() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of(new LeavePolicyEligibility(tenantId, policyId, "department", deptA)));

        Employee empInA = createEmployee(createDepartment(deptA), null, null, "Female");
        Employee empInB = createEmployee(createDepartment(deptB), null, null, "Female");
        Employee empNoDept = createEmployee(null, null, null, "Female");

        assertThat(service.isEligible(empInA, leaveType, today)).isTrue();
        assertThat(service.isEligible(empInB, leaveType, today)).isFalse();
        assertThat(service.isEligible(empNoDept, leaveType, today)).isFalse();
    }

    @Test
    @DisplayName("designation dimension filters: only employees with matching designation are eligible")
    void designationDimensionFilters() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of(new LeavePolicyEligibility(tenantId, policyId, "designation", desigA)));

        Employee empWithA = createEmployee(null, createDesignation(desigA), null, "Male");
        Employee empWithB = createEmployee(null, createDesignation(desigB), null, "Male");

        assertThat(service.isEligible(empWithA, leaveType, today)).isTrue();
        assertThat(service.isEligible(empWithB, leaveType, today)).isFalse();
    }

    @Test
    @DisplayName("work location dimension filters: only employees at matching location are eligible")
    void workLocationDimensionFilters() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of(new LeavePolicyEligibility(tenantId, policyId, "work_location", locA)));

        Employee empAtA = createEmployee(null, null, createWorkLocation(locA), "Female");
        Employee empAtB = createEmployee(null, null, createWorkLocation(locB), "Female");

        assertThat(service.isEligible(empAtA, leaveType, today)).isTrue();
        assertThat(service.isEligible(empAtB, leaveType, today)).isFalse();
    }

    @Test
    @DisplayName("combined dimensions are AND, not OR: employee must satisfy every active dimension")
    void combinedDimensionsAreAndNotOr() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of(
                        new LeavePolicyEligibility(tenantId, policyId, "department", deptA),
                        new LeavePolicyEligibility(tenantId, policyId, "work_location", locA)));

        // Matches both department AND work location -> eligible
        Employee empBoth = createEmployee(createDepartment(deptA), null, createWorkLocation(locA), "Male");
        // Matches department only -> NOT eligible
        Employee empDeptOnly = createEmployee(createDepartment(deptA), null, createWorkLocation(locB), "Male");
        // Matches work location only -> NOT eligible
        Employee empLocOnly = createEmployee(createDepartment(deptB), null, createWorkLocation(locA), "Male");
        // Matches neither -> NOT eligible
        Employee empNeither = createEmployee(createDepartment(deptB), null, createWorkLocation(locB), "Male");

        assertThat(service.isEligible(empBoth, leaveType, today)).isTrue();
        assertThat(service.isEligible(empDeptOnly, leaveType, today)).isFalse();
        assertThat(service.isEligible(empLocOnly, leaveType, today)).isFalse();
        assertThat(service.isEligible(empNeither, leaveType, today)).isFalse();
    }

    @Test
    @DisplayName("multiple values within the same dimension are OR: employee in any listed department is eligible")
    void multipleValuesWithinSameDimensionAreOr() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of(
                        new LeavePolicyEligibility(tenantId, policyId, "department", deptA),
                        new LeavePolicyEligibility(tenantId, policyId, "department", deptB)));

        Employee empA = createEmployee(createDepartment(deptA), null, null, "Female");
        Employee empB = createEmployee(createDepartment(deptB), null, null, "Female");
        Employee empOther = createEmployee(createDepartment(UUID.randomUUID()), null, null, "Female");

        assertThat(service.isEligible(empA, leaveType, today)).isTrue();
        assertThat(service.isEligible(empB, leaveType, today)).isTrue();
        assertThat(service.isEligible(empOther, leaveType, today)).isFalse();
    }

    @Test
    @DisplayName("gender restriction on policy: only matching gender is eligible")
    void genderRestrictionOnPolicy() {
        policy.setGender("FEMALE");
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of());

        Employee female = createEmployee(null, null, null, "female");
        Employee male = createEmployee(null, null, null, "male");
        Employee noGender = createEmployee(null, null, null, null);

        assertThat(service.isEligible(female, leaveType, today)).isTrue();
        assertThat(service.isEligible(male, leaveType, today)).isFalse();
        assertThat(service.isEligible(noGender, leaveType, today)).isFalse();
    }

    @Test
    @DisplayName("inactive leave type or outside validity dates is refused")
    void inactiveOrExpiredLeaveTypeRefused() {
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policyId))
                .thenReturn(List.of());
        Employee emp = createEmployee(null, null, null, "Male");

        // Inactive type
        leaveType.setActive(false);
        assertThat(service.isEligible(emp, leaveType, today)).isFalse();

        // Valid in future
        leaveType.setActive(true);
        leaveType.setValidFrom(today.plusDays(1));
        assertThat(service.isEligible(emp, leaveType, today)).isFalse();

        // Expired in past
        leaveType.setValidFrom(today.minusMonths(5));
        leaveType.setValidTo(today.minusDays(1));
        assertThat(service.isEligible(emp, leaveType, today)).isFalse();
    }
}
