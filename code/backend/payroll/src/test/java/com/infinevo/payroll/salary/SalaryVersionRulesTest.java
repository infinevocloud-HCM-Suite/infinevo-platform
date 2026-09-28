package com.infinevo.payroll.salary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.component.BenefitRepository;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalaryVersionRulesTest {

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID employeeId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private CtcStructureRepository ctcStructureRepository;
    private EmployeeEarningRepository employeeEarningRepository;
    private EmployeeBenefitRepository employeeBenefitRepository;
    private EmployeeReimbursementRepository employeeReimbursementRepository;
    private EarningRepository earningRepository;
    private BenefitRepository benefitRepository;
    private ReimbursementRepository reimbursementRepository;
    private EmployeeService employeeService;

    private EmployeeSalaryServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        ctcStructureRepository = mock(CtcStructureRepository.class);
        employeeEarningRepository = mock(EmployeeEarningRepository.class);
        employeeBenefitRepository = mock(EmployeeBenefitRepository.class);
        employeeReimbursementRepository = mock(EmployeeReimbursementRepository.class);
        earningRepository = mock(EarningRepository.class);
        benefitRepository = mock(BenefitRepository.class);
        reimbursementRepository = mock(ReimbursementRepository.class);
        employeeService = mock(EmployeeService.class);

        when(employeeService.get(employeeId))
                .thenReturn(new EmployeeResponse(
                        employeeId,
                        tenantId,
                        "EMP-001",
                        "John",
                        null,
                        "Doe",
                        "MALE",
                        LocalDate.of(2025, 1, 1),
                        null,
                        null,
                        "john@example.com",
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        java.time.Instant.now(),
                        java.time.Instant.now()));

        service = new EmployeeSalaryServiceImpl(
                ctcStructureRepository,
                employeeEarningRepository,
                employeeBenefitRepository,
                employeeReimbursementRepository,
                earningRepository,
                benefitRepository,
                reimbursementRepository,
                employeeService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Second first-version call via create is refused with 409 Conflict")
    void secondFirstVersionRefused() {
        when(ctcStructureRepository.existsByTenantIdAndEmployeeId(tenantId, employeeId))
                .thenReturn(true);

        SalaryVersionRequest request = new SalaryVersionRequest(
                new BigDecimal("500000.00"), LocalDate.now(), null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> service.create(employeeId, request))
                .isInstanceOf(SalaryConflictException.class)
                .hasMessageContaining("A salary structure already exists for employee");
    }

    @Test
    @DisplayName("Duplicate effective_from in revision is refused with 409 Conflict")
    void duplicateEffectiveFromRefused() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        when(ctcStructureRepository.existsByTenantIdAndEmployeeIdAndEffectiveFrom(tenantId, employeeId, date))
                .thenReturn(true);

        SalaryVersionRequest request =
                new SalaryVersionRequest(new BigDecimal("500000.00"), date, null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> service.revise(employeeId, request))
                .isInstanceOf(SalaryConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Editing a version whose effective_from is today or in the past is refused with 409 Conflict")
    void editPastOrTodayVersionRefused() {
        UUID versionId = UUID.randomUUID();
        CtcStructure currentVersion = new CtcStructure(tenantId, employeeId, LocalDate.now(), "system");
        when(ctcStructureRepository.findByIdAndTenantId(versionId, tenantId)).thenReturn(Optional.of(currentVersion));

        SalaryVersionRequest request = new SalaryVersionRequest(
                new BigDecimal("500000.00"), LocalDate.now(), null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> service.update(employeeId, versionId, request))
                .isInstanceOf(SalaryConflictException.class)
                .hasMessageContaining("Cannot edit salary version");
    }

    @Test
    @DisplayName("Cancelling a version whose effective_from is today or in the past is refused with 409 Conflict")
    void cancelPastOrTodayVersionRefused() {
        UUID versionId = UUID.randomUUID();
        CtcStructure currentVersion = new CtcStructure(tenantId, employeeId, LocalDate.now(), "system");
        when(ctcStructureRepository.findByIdAndTenantId(versionId, tenantId)).thenReturn(Optional.of(currentVersion));

        assertThatThrownBy(() -> service.cancel(employeeId, versionId))
                .isInstanceOf(SalaryConflictException.class)
                .hasMessageContaining("Cannot cancel salary version");
    }
}
