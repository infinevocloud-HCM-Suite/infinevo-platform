package com.infinevo.payroll.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.leave.LeaveBalanceResponse;
import com.infinevo.core.leave.LeaveBalanceService;
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

class LeaveEncashmentServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID elTypeId = UUID.randomUUID();
    private final UUID slTypeId = UUID.randomUUID();
    private final LocalDate exitDate = LocalDate.of(2026, 10, 31);

    private LeaveEncashmentRepository repository;
    private LeaveBalanceService leaveBalanceService;
    private EmployeeSalaryService salaryService;
    private LeaveEncashmentServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        repository = mock(LeaveEncashmentRepository.class);
        leaveBalanceService = mock(LeaveBalanceService.class);
        salaryService = mock(EmployeeSalaryService.class);

        service = new LeaveEncashmentServiceImpl(repository, leaveBalanceService, salaryService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SalaryVersionResponse buildSalaryVersion(BigDecimal basicAmount, BigDecimal daAmount) {
        SalaryComponentItemResponse basic = new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BASIC",
                "Basic Salary",
                CalculationType.FLAT,
                basicAmount,
                null,
                basicAmount,
                basicAmount.multiply(BigDecimal.valueOf(12)),
                true,
                true,
                "MONTHLY",
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        SalaryComponentItemResponse da = new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DA",
                "Dearness Allowance",
                CalculationType.FLAT,
                daAmount,
                null,
                daAmount,
                daAmount.multiply(BigDecimal.valueOf(12)),
                true,
                true,
                "MONTHLY",
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        return new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 4, 1),
                BigDecimal.valueOf(1200000),
                BigDecimal.valueOf(100000),
                false,
                null,
                null,
                BigDecimal.ZERO,
                List.of(basic, da),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("Estimates leave encashment: Basic 60,000 + DA 15,000 / 30 = 2,500/day for 20 EL days = 50,000 exempt")
    void estimateEncashment_StandardUnderCeiling() {
        when(salaryService.getAsOf(eq(employeeId), eq(exitDate)))
                .thenReturn(buildSalaryVersion(BigDecimal.valueOf(60000), BigDecimal.valueOf(15000)));

        LeaveBalanceResponse elBalance = new LeaveBalanceResponse(
                employeeId,
                elTypeId,
                "EL",
                "Earned Leave",
                "2026-2027",
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(15),
                BigDecimal.ZERO,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(20),
                null,
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.valueOf(20));

        when(leaveBalanceService.getBalancesForEmployee(eq(tenantId), eq(employeeId), eq(exitDate)))
                .thenReturn(List.of(elBalance));

        LeaveEncashmentEstimateRequest request =
                new LeaveEncashmentEstimateRequest(employeeId, exitDate, elTypeId, null, 30);

        LeaveEncashmentResponse res = service.estimateEncashment(request);

        assertThat(res).isNotNull();
        assertThat(res.monthlyBasicAndDa()).isEqualByComparingTo(BigDecimal.valueOf(75000.00));
        assertThat(res.perDayRate()).isEqualByComparingTo(BigDecimal.valueOf(2500.00));
        assertThat(res.encashableDays()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        assertThat(res.grossEncashmentAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(res.taxExemptAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(res.taxableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res.statutoryCeiling()).isEqualByComparingTo(BigDecimal.valueOf(2500000.00));
    }

    @Test
    @DisplayName("Applies Section 10(10AA) statutory ceiling of ₹25,00,000 on large payouts")
    void estimateEncashment_AppliesSection1010AACeiling() {
        // High basic salary: 3,00,000/month / 30 = 10,000/day
        when(salaryService.getAsOf(eq(employeeId), eq(exitDate)))
                .thenReturn(buildSalaryVersion(BigDecimal.valueOf(300000), BigDecimal.ZERO));

        // 300 days accrued = 30,00,000 gross
        LeaveBalanceResponse elBalance = new LeaveBalanceResponse(
                employeeId,
                elTypeId,
                "PL",
                "Privilege Leave",
                "2026-2027",
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(300),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(300),
                null,
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.valueOf(300));

        when(leaveBalanceService.getBalancesForEmployee(eq(tenantId), eq(employeeId), eq(exitDate)))
                .thenReturn(List.of(elBalance));

        LeaveEncashmentEstimateRequest request =
                new LeaveEncashmentEstimateRequest(employeeId, exitDate, elTypeId, BigDecimal.valueOf(300), 30);

        LeaveEncashmentResponse res = service.estimateEncashment(request);

        assertThat(res.grossEncashmentAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000000.00));
        // ₹25 Lakh exempt, ₹5 Lakh taxable
        assertThat(res.taxExemptAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500000.00));
        assertThat(res.taxableAmount()).isEqualByComparingTo(BigDecimal.valueOf(500000.00));
    }

    @Test
    @DisplayName("Rejects encashment for non-encashable leave types like Sick Leave (SL)")
    void estimateEncashment_RejectsNonEncashableLeaveType() {
        when(salaryService.getAsOf(eq(employeeId), eq(exitDate)))
                .thenReturn(buildSalaryVersion(BigDecimal.valueOf(50000), BigDecimal.ZERO));

        LeaveBalanceResponse slBalance = new LeaveBalanceResponse(
                employeeId,
                slTypeId,
                "SL",
                "Sick Leave",
                "2026-2027",
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(10),
                null,
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.valueOf(10));

        when(leaveBalanceService.getBalancesForEmployee(eq(tenantId), eq(employeeId), eq(exitDate)))
                .thenReturn(List.of(slBalance));

        LeaveEncashmentEstimateRequest request =
                new LeaveEncashmentEstimateRequest(employeeId, exitDate, slTypeId, null, 30);

        assertThatThrownBy(() -> service.estimateEncashment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not encashable");
    }

    @Test
    @DisplayName("Records and saves leave encashment settlement")
    void recordSettlement_Success() {
        when(salaryService.getAsOf(eq(employeeId), eq(exitDate)))
                .thenReturn(buildSalaryVersion(BigDecimal.valueOf(60000), BigDecimal.ZERO));

        LeaveBalanceResponse elBalance = new LeaveBalanceResponse(
                employeeId,
                elTypeId,
                "EL",
                "Earned Leave",
                "2026-2027",
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(15),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(15),
                null,
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.valueOf(15));

        when(leaveBalanceService.getBalancesForEmployee(eq(tenantId), eq(employeeId), eq(exitDate)))
                .thenReturn(List.of(elBalance));

        when(repository.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatusNot(
                        eq(tenantId), eq(employeeId), eq(elTypeId), eq("CANCELLED")))
                .thenReturn(Optional.empty());

        when(repository.save(any(LeaveEncashmentSettlement.class))).thenAnswer(inv -> {
            LeaveEncashmentSettlement arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        RecordLeaveEncashmentRequest request = new RecordLeaveEncashmentRequest(
                employeeId, exitDate, elTypeId, BigDecimal.valueOf(10), 30, "APPROVED");

        LeaveEncashmentResponse res = service.recordSettlement(request);

        assertThat(res.id()).isNotNull();
        assertThat(res.encashableDays()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(res.grossEncashmentAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000.00));
        assertThat(res.status()).isEqualTo("APPROVED");

        verify(repository).save(any(LeaveEncashmentSettlement.class));
    }

    @Test
    @DisplayName("Rejects duplicate settlement creation if an active settlement already exists")
    void recordSettlement_RejectsDuplicateActiveSettlement() {
        LeaveEncashmentSettlement existing = new LeaveEncashmentSettlement();
        existing.setId(UUID.randomUUID());

        when(repository.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatusNot(
                        eq(tenantId), eq(employeeId), eq(elTypeId), eq("CANCELLED")))
                .thenReturn(Optional.of(existing));

        RecordLeaveEncashmentRequest request =
                new RecordLeaveEncashmentRequest(employeeId, exitDate, elTypeId, null, 30, "DRAFT");

        assertThatThrownBy(() -> service.recordSettlement(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }
}
