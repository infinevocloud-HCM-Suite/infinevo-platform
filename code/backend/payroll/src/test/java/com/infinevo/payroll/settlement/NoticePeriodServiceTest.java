package com.infinevo.payroll.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class NoticePeriodServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate resignationDate = LocalDate.of(2026, 10, 1);
    private final LocalDate actualExitDate = LocalDate.of(2026, 10, 16); // 15 days served

    private NoticePeriodRepository repository;
    private EmployeeSalaryService salaryService;
    private NoticePeriodServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        repository = mock(NoticePeriodRepository.class);
        salaryService = mock(EmployeeSalaryService.class);
        service = new NoticePeriodServiceImpl(repository, salaryService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SalaryVersionResponse buildSalaryVersion(BigDecimal basic, BigDecimal hra, BigDecimal special) {
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

        SalaryComponentItemResponse specialItem = new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SPECIAL",
                "Special Allowance",
                CalculationType.FLAT,
                special,
                null,
                special,
                special.multiply(BigDecimal.valueOf(12)),
                true,
                true,
                "MONTHLY",
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        BigDecimal totalGross = basic.add(hra).add(special);

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
                List.of(basicItem, hraItem, specialItem),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName(
            "Calculates shortfall recovery on GROSS rate basis: Gross 90,000 / 30 = 3,000/day. 15 days shortfall = ₹45,000 RECOVERY")
    void calculateNoticePeriod_ShortfallRecoveryGross() {
        // Basic: 50,000, HRA: 25,000, Special: 15,000 -> Gross: 90,000
        when(salaryService.getAsOf(eq(employeeId), eq(actualExitDate)))
                .thenReturn(buildSalaryVersion(
                        BigDecimal.valueOf(50000), BigDecimal.valueOf(25000), BigDecimal.valueOf(15000)));

        NoticePeriodCalculateRequest request = new NoticePeriodCalculateRequest(
                employeeId, resignationDate, actualExitDate, 30, "GROSS", 30, false, null);

        NoticePeriodResponse res = service.calculateNoticePeriod(request);

        assertThat(res).isNotNull();
        assertThat(res.requiredNoticeDays()).isEqualTo(30);
        assertThat(res.servedNoticeDays()).isEqualTo(15);
        assertThat(res.shortfallDays()).isEqualTo(15);
        assertThat(res.rateBasis()).isEqualTo("GROSS");
        assertThat(res.baseSalaryAmount()).isEqualByComparingTo(BigDecimal.valueOf(90000.00));
        assertThat(res.dailyRate()).isEqualByComparingTo(BigDecimal.valueOf(3000.00));
        assertThat(res.settlementType()).isEqualTo("RECOVERY");
        assertThat(res.settlementAmount()).isEqualByComparingTo(BigDecimal.valueOf(45000.00));
    }

    @Test
    @DisplayName(
            "Calculates shortfall recovery on BASIC rate basis: Basic 50,000 / 30 = 1,666.67/day. 15 days = ₹25,000.05 RECOVERY")
    void calculateNoticePeriod_ShortfallRecoveryBasic() {
        when(salaryService.getAsOf(eq(employeeId), eq(actualExitDate)))
                .thenReturn(buildSalaryVersion(
                        BigDecimal.valueOf(50000), BigDecimal.valueOf(25000), BigDecimal.valueOf(15000)));

        NoticePeriodCalculateRequest request = new NoticePeriodCalculateRequest(
                employeeId, resignationDate, actualExitDate, 30, "BASIC", 30, false, null);

        NoticePeriodResponse res = service.calculateNoticePeriod(request);

        assertThat(res.rateBasis()).isEqualTo("BASIC");
        assertThat(res.baseSalaryAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(res.dailyRate()).isEqualByComparingTo(BigDecimal.valueOf(1666.67));
        assertThat(res.settlementType()).isEqualTo("RECOVERY");
        assertThat(res.settlementAmount()).isEqualByComparingTo(BigDecimal.valueOf(25000.05));
    }

    @Test
    @DisplayName("Handles notice shortfall waiver: amount is ₹0 and type is WAIVED")
    void calculateNoticePeriod_Waiver() {
        when(salaryService.getAsOf(eq(employeeId), eq(actualExitDate)))
                .thenReturn(buildSalaryVersion(
                        BigDecimal.valueOf(50000), BigDecimal.valueOf(25000), BigDecimal.valueOf(15000)));

        NoticePeriodCalculateRequest request = new NoticePeriodCalculateRequest(
                employeeId,
                resignationDate,
                actualExitDate,
                30,
                "GROSS",
                30,
                true,
                "Management approval for health reasons");

        NoticePeriodResponse res = service.calculateNoticePeriod(request);

        assertThat(res.settlementType()).isEqualTo("WAIVED");
        assertThat(res.settlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res.waiverReason()).isEqualTo("Management approval for health reasons");
    }

    @Test
    @DisplayName("Rejects actual exit date before resignation date")
    void calculateNoticePeriod_ExitBeforeResignation_ThrowsException() {
        LocalDate invalidExit = resignationDate.minusDays(5);
        NoticePeriodCalculateRequest request = new NoticePeriodCalculateRequest(
                employeeId, resignationDate, invalidExit, 30, "GROSS", 30, false, null);

        assertThatThrownBy(() -> service.calculateNoticePeriod(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be before");
    }

    @Test
    @DisplayName("Records and saves notice period settlement row")
    void recordSettlement_Success() {
        when(salaryService.getAsOf(eq(employeeId), eq(actualExitDate)))
                .thenReturn(buildSalaryVersion(BigDecimal.valueOf(60000), BigDecimal.valueOf(30000), BigDecimal.ZERO));

        when(repository.findByTenantIdAndEmployeeIdAndStatusNot(eq(tenantId), eq(employeeId), eq("CANCELLED")))
                .thenReturn(Optional.empty());

        when(repository.save(any(NoticePeriodSettlement.class))).thenAnswer(inv -> {
            NoticePeriodSettlement arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        RecordNoticeSettlementRequest request = new RecordNoticeSettlementRequest(
                employeeId, resignationDate, actualExitDate, 30, "GROSS", 30, false, null, "admin", "APPROVED");

        NoticePeriodResponse res = service.recordSettlement(request);

        assertThat(res.id()).isNotNull();
        assertThat(res.settlementType()).isEqualTo("RECOVERY");
        assertThat(res.settlementAmount()).isEqualByComparingTo(BigDecimal.valueOf(45000.00));
        assertThat(res.status()).isEqualTo("APPROVED");

        verify(repository).save(any(NoticePeriodSettlement.class));
    }

    @Test
    @DisplayName("Rejects duplicate active notice period settlement")
    void recordSettlement_DuplicateThrowsException() {
        NoticePeriodSettlement existing = new NoticePeriodSettlement();
        existing.setId(UUID.randomUUID());

        when(repository.findByTenantIdAndEmployeeIdAndStatusNot(eq(tenantId), eq(employeeId), eq("CANCELLED")))
                .thenReturn(Optional.of(existing));

        RecordNoticeSettlementRequest request = new RecordNoticeSettlementRequest(
                employeeId, resignationDate, actualExitDate, 30, "GROSS", 30, false, null, null, "DRAFT");

        assertThatThrownBy(() -> service.recordSettlement(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }
}
