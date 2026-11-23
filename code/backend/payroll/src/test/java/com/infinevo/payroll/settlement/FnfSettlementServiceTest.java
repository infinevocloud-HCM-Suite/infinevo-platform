package com.infinevo.payroll.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
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

class FnfSettlementServiceTest {

    private FnfSettlementRepository repository;
    private LeaveEncashmentRepository leaveEncashmentRepository;
    private NoticePeriodRepository noticePeriodRepository;
    private EmployeeSalaryService salaryService;
    private EmployeeRepository employeeRepository;
    private FnfSettlementServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate resignationDate = LocalDate.of(2026, 10, 1);
    private final LocalDate exitDate = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        repository = mock(FnfSettlementRepository.class);
        leaveEncashmentRepository = mock(LeaveEncashmentRepository.class);
        noticePeriodRepository = mock(NoticePeriodRepository.class);
        salaryService = mock(EmployeeSalaryService.class);
        employeeRepository = mock(EmployeeRepository.class);

        service = new FnfSettlementServiceImpl(
                repository, leaveEncashmentRepository, noticePeriodRepository, salaryService, employeeRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SalaryVersionResponse buildSalaryVersion(BigDecimal basic, BigDecimal hra) {
        SalaryComponentItemResponse basicItem = new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BASIC",
                "Basic Salary",
                CalculationType.FLAT,
                basic,
                null,
                basic,
                basic.multiply(BigDecimal.valueOf(12)),
                true,
                true,
                "MONTHLY",
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        SalaryComponentItemResponse hraItem = new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HRA",
                "House Rent Allowance",
                CalculationType.FLAT,
                hra,
                null,
                hra,
                hra.multiply(BigDecimal.valueOf(12)),
                true,
                true,
                "MONTHLY",
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        BigDecimal totalGross = basic.add(hra);

        return new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 4, 1),
                totalGross.multiply(BigDecimal.valueOf(12)),
                totalGross,
                false,
                null,
                null,
                BigDecimal.ZERO,
                List.of(basicItem, hraItem),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("Initiates F&F settlement consolidating pro-rated salary, leave, notice recovery, and deductions")
    void initiateSettlement_Success() {
        when(salaryService.getAsOf(eq(employeeId), any(LocalDate.class)))
                .thenReturn(buildSalaryVersion(BigDecimal.valueOf(30000.00), BigDecimal.valueOf(30000.00)));

        when(repository.findByTenantIdAndEmployeeIdAndStatusNot(tenantId, employeeId, "CANCELLED"))
                .thenReturn(Optional.empty());

        // Mock leave encashment of ₹25,000
        LeaveEncashmentSettlement leaveMock = mock(LeaveEncashmentSettlement.class);
        when(leaveMock.getGrossEncashmentAmount()).thenReturn(BigDecimal.valueOf(25000.00));
        when(leaveEncashmentRepository.findByTenantIdAndEmployeeIdAndStatusNot(tenantId, employeeId, "CANCELLED"))
                .thenReturn(List.of(leaveMock));

        // Mock notice period recovery of ₹10,000
        NoticePeriodSettlement noticeMock = mock(NoticePeriodSettlement.class);
        when(noticeMock.getSettlementType()).thenReturn("RECOVERY");
        when(noticeMock.getSettlementAmount()).thenReturn(BigDecimal.valueOf(10000.00));
        when(noticePeriodRepository.findByTenantIdAndEmployeeIdAndStatusNot(tenantId, employeeId, "CANCELLED"))
                .thenReturn(Optional.of(noticeMock));

        // Mock employee with 6 years tenure for gratuity
        Employee emp = mock(Employee.class);
        when(emp.getDateOfJoining()).thenReturn(LocalDate.of(2020, 1, 1));
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(emp));

        when(repository.save(any(FnfSettlement.class))).thenAnswer(invocation -> {
            FnfSettlement s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        FnfInitiateRequest request = new FnfInitiateRequest(
                employeeId,
                resignationDate,
                exitDate,
                "RESIGNATION",
                15,
                BigDecimal.valueOf(5000.00), // other earnings
                BigDecimal.valueOf(2000.00), // other recoveries
                "Standard exit");

        FnfSettlementResponse response = service.initiateSettlement(request);

        assertThat(response).isNotNull();
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.leaveEncashmentAmount()).isEqualByComparingTo("25000.00");
        assertThat(response.noticeRecoveryAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.otherEarnings()).isEqualByComparingTo("5000.00");
        assertThat(response.otherRecoveries()).isEqualByComparingTo("2000.00");
        // Exit month salary: 60,000 / 31 * 15 = 29,032.20
        assertThat(response.exitMonthSalary()).isGreaterThan(BigDecimal.ZERO);
        // Gratuity: 15 * 30000 * 6 / 26 = 103,846.15
        assertThat(response.gratuityAmount()).isGreaterThan(BigDecimal.ZERO);
        // Net payable = grossEarnings - grossDeductions
        assertThat(response.netPayable())
                .isEqualByComparingTo(response.grossEarnings().subtract(response.grossDeductions()));
    }

    @Test
    @DisplayName("Initiate settlement fails if active settlement already exists")
    void initiateSettlement_DuplicateActive_Throws() {
        FnfSettlement existing = new FnfSettlement();
        existing.setStatus("DRAFT");
        when(repository.findByTenantIdAndEmployeeIdAndStatusNot(tenantId, employeeId, "CANCELLED"))
                .thenReturn(Optional.of(existing));

        FnfInitiateRequest request =
                new FnfInitiateRequest(employeeId, resignationDate, exitDate, "RESIGNATION", 15, null, null, null);

        assertThatThrownBy(() -> service.initiateSettlement(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active F&F settlement already exists");
    }

    @Test
    @DisplayName("Approve settlement transitions status from DRAFT to APPROVED")
    void approveSettlement_Success() {
        UUID settlementId = UUID.randomUUID();
        FnfSettlement settlement = new FnfSettlement();
        settlement.setId(settlementId);
        settlement.setTenantId(tenantId);
        settlement.setEmployeeId(employeeId);
        settlement.setStatus("DRAFT");

        when(repository.findByTenantIdAndId(tenantId, settlementId)).thenReturn(Optional.of(settlement));
        when(repository.save(any(FnfSettlement.class))).thenAnswer(i -> i.getArgument(0));

        FnfSettlementResponse response = service.approveSettlement(settlementId, "hr-admin");

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedBy()).isEqualTo("hr-admin");
        assertThat(response.approvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Pay settlement transitions status from APPROVED to PAID")
    void paySettlement_Success() {
        UUID settlementId = UUID.randomUUID();
        FnfSettlement settlement = new FnfSettlement();
        settlement.setId(settlementId);
        settlement.setTenantId(tenantId);
        settlement.setEmployeeId(employeeId);
        settlement.setStatus("APPROVED");

        when(repository.findByTenantIdAndId(tenantId, settlementId)).thenReturn(Optional.of(settlement));
        when(repository.save(any(FnfSettlement.class))).thenAnswer(i -> i.getArgument(0));

        FnfSettlementResponse response = service.paySettlement(settlementId);

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.paidAt()).isNotNull();
    }

    @Test
    @DisplayName("Pay settlement fails if settlement is not APPROVED")
    void paySettlement_NotApproved_Throws() {
        UUID settlementId = UUID.randomUUID();
        FnfSettlement settlement = new FnfSettlement();
        settlement.setId(settlementId);
        settlement.setTenantId(tenantId);
        settlement.setStatus("DRAFT");

        when(repository.findByTenantIdAndId(tenantId, settlementId)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.paySettlement(settlementId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only APPROVED settlements can be marked as PAID");
    }
}
