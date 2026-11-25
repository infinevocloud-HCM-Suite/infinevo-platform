package com.infinevo.payroll.taxcalc.recalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxcalc.TaxCalculationService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.TaxSummaryFiguresMapper;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclarationRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.shared.authz.NoEmployeeRecordException;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests verifying business rules of tax recalculation engine (W-33.3).
 */
@ExtendWith(MockitoExtension.class)
class TaxRecalculationRulesTest {

    @Mock
    private TaxComputationRepository taxComputationRepository;

    @Mock
    private EmployeeInvestmentDeclarationRepository declarationRepository;

    @Mock
    private TaxDeclarationWindowService windowService;

    @Mock
    private TaxCalculationService taxCalculationService;

    @Mock
    private TaxSummaryService taxSummaryService;

    @Mock
    private TaxSummaryFiguresMapper taxSummaryFiguresMapper;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private EmployeeTdsService employeeTdsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TaxRecalculationServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final UUID officerId = UUID.randomUUID();
    private final FinancialYear fy = FinancialYear.of(2025, 2026);

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        service = new TaxRecalculationServiceImpl(
                taxComputationRepository,
                declarationRepository,
                windowService,
                taxCalculationService,
                taxSummaryService,
                taxSummaryFiguresMapper,
                employeeService,
                objectMapper,
                employeeTdsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TaxComputation sampleComputation(TaxRegime regime) {
        return new TaxComputation(
                regime,
                fy.label(),
                Money.of(1200000),
                Money.ZERO,
                Money.of(75000),
                Money.of(1200000),
                Money.of(922600),
                Money.of(60000),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.of(2400),
                Money.ZERO,
                Money.of(62400),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("When declaration exists with OLD regime, computes OLD and retains trigger and declarationId")
    void whenDeclarationExistsOldRegime_retainsTriggerAndDeclarationId() {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(declarationId);
        decl.setTaxRegime("OLD");
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.of(decl));

        TaxComputation computation = sampleComputation(TaxRegime.OLD);
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.OLD)).willReturn(computation);

        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        TaxComputationRecord record = service.recalculate(employeeId, fy.label(), TaxTrigger.OFFICER, officerId);

        assertThat(record.getRegime()).isEqualTo("OLD");
        assertThat(record.getTrigger()).isEqualTo(TaxTrigger.OFFICER);
        assertThat(record.getDeclarationId()).isEqualTo(declarationId);
        assertThat(record.getComputedBy()).isEqualTo(officerId);
        assertThat(record.getGrossTotalIncome()).isEqualByComparingTo("1200000");
        assertThat(record.getAnnualTax()).isEqualByComparingTo("62400");

        verify(taxCalculationService).compute(employeeId, fy, TaxRegime.OLD);
        verify(taxSummaryService).record(eq(declarationId), eq("OLD"), any());
        verify(employeeTdsService).record(eq(employeeId), eq(fy.label()), any());
    }

    @Test
    @DisplayName("When declaration exists with NEW regime, trigger DECLARATION_SUBMITTED is saved")
    void whenDeclarationExistsNewRegime_computesNew() {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(declarationId);
        decl.setTaxRegime("NEW");
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.of(decl));

        TaxComputation computation = sampleComputation(TaxRegime.NEW);
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW)).willReturn(computation);

        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        TaxComputationRecord record =
                service.recalculate(employeeId, fy.label(), TaxTrigger.DECLARATION_SUBMITTED, null);

        assertThat(record.getRegime()).isEqualTo("NEW");
        assertThat(record.getTrigger()).isEqualTo(TaxTrigger.DECLARATION_SUBMITTED);
        assertThat(record.getDeclarationId()).isEqualTo(declarationId);
        assertThat(record.getComputedBy()).isNull();

        verify(taxCalculationService).compute(employeeId, fy, TaxRegime.NEW);
    }

    @Test
    @DisplayName("When no declaration exists, falls back to window default regime and triggers SALARY_DEFAULT")
    void whenNoDeclaration_usesWindowDefaultRegimeAndSalaryDefaultTrigger() {
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.empty());

        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setDefaultTaxRegime("NEW");
        given(windowService.findOrCreateDefault(tenantId, fy.label())).willReturn(window);

        TaxComputation computation = sampleComputation(TaxRegime.NEW);
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW)).willReturn(computation);

        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        TaxComputationRecord record = service.recalculate(employeeId, fy.label(), TaxTrigger.SALARY_REVISION, null);

        assertThat(record.getRegime()).isEqualTo("NEW");
        // SALARY_REVISION trigger must be converted to SALARY_DEFAULT when no declaration header exists
        assertThat(record.getTrigger()).isEqualTo(TaxTrigger.SALARY_DEFAULT);
        assertThat(record.getDeclarationId()).isNull();

        verify(taxCalculationService).compute(employeeId, fy, TaxRegime.NEW);
        // taxSummaryService must not be called when declarationId is null
        verify(taxSummaryService, never()).record(any(), any(), any());
        verify(employeeTdsService).record(eq(employeeId), eq(fy.label()), any());
    }

    @Test
    @DisplayName("When no declaration and window default is missing, falls back to NEW regime")
    void whenNoDeclarationAndNoWindowRegime_fallbacksToNew() {
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.empty());
        given(windowService.findOrCreateDefault(tenantId, fy.label())).willReturn(null);

        TaxComputation computation = sampleComputation(TaxRegime.NEW);
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW)).willReturn(computation);
        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        TaxComputationRecord record = service.recalculate(employeeId, fy.label(), TaxTrigger.SALARY_REVISION, null);

        assertThat(record.getRegime()).isEqualTo("NEW");
        assertThat(record.getTrigger()).isEqualTo(TaxTrigger.SALARY_DEFAULT);
    }

    @Test
    @DisplayName("D-147: a taxSummaryService failure fails the recalculation, so its transaction rolls back whole")
    void taxSummaryFailurePropagates() {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(declarationId);
        decl.setTaxRegime("NEW");
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.of(decl));

        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW))
                .willReturn(sampleComputation(TaxRegime.NEW));
        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("Tax summary DB error"))
                .when(taxSummaryService)
                .record(any(), any(), any());

        assertThatThrownBy(() -> service.recalculate(employeeId, fy.label(), TaxTrigger.OFFICER, officerId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Tax summary DB error");
        verify(employeeTdsService, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("D-147: an employeeTdsService failure fails the recalculation instead of being swallowed")
    void employeeTdsFailurePropagates() {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(declarationId);
        decl.setTaxRegime("NEW");
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.of(decl));

        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW))
                .willReturn(sampleComputation(TaxRegime.NEW));
        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("TDS sync error")).when(employeeTdsService).record(any(), any(), any());

        assertThatThrownBy(() -> service.recalculate(employeeId, fy.label(), TaxTrigger.OFFICER, officerId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("TDS sync error");
    }

    @Test
    @DisplayName("D-147: a successful recalculation closes the open failure for the employee and year")
    void successResolvesOpenFailure() {
        TaxRecalcFailureRepository failureRepository = org.mockito.Mockito.mock(TaxRecalcFailureRepository.class);
        TaxRecalculationServiceImpl withFailures = new TaxRecalculationServiceImpl(
                taxComputationRepository,
                declarationRepository,
                windowService,
                taxCalculationService,
                taxSummaryService,
                taxSummaryFiguresMapper,
                employeeService,
                objectMapper,
                employeeTdsService,
                failureRepository);
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.empty());
        given(windowService.findOrCreateDefault(tenantId, fy.label())).willReturn(null);
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW))
                .willReturn(sampleComputation(TaxRegime.NEW));
        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        withFailures.recalculate(employeeId, fy.label(), TaxTrigger.SALARY_REVISION, null);

        verify(failureRepository).resolveOpen(eq(tenantId), eq(employeeId), eq(fy.label()), any(Instant.class));
    }

    @Test
    @DisplayName("D-147: a failed recalculation leaves the open failure open")
    void failureDoesNotResolve() {
        TaxRecalcFailureRepository failureRepository = org.mockito.Mockito.mock(TaxRecalcFailureRepository.class);
        TaxRecalculationServiceImpl withFailures = new TaxRecalculationServiceImpl(
                taxComputationRepository,
                declarationRepository,
                windowService,
                taxCalculationService,
                taxSummaryService,
                taxSummaryFiguresMapper,
                employeeService,
                objectMapper,
                employeeTdsService,
                failureRepository);
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.empty());
        given(windowService.findOrCreateDefault(tenantId, fy.label())).willReturn(null);
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.NEW))
                .willReturn(sampleComputation(TaxRegime.NEW));
        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("TDS sync error")).when(employeeTdsService).record(any(), any(), any());

        assertThatThrownBy(() -> withFailures.recalculate(employeeId, fy.label(), TaxTrigger.SALARY_REVISION, null))
                .hasMessage("TDS sync error");
        verify(failureRepository, never()).resolveOpen(any(), any(), any(), any());
    }

    @Test
    @DisplayName("history returns list of TaxComputationRecordResponse mapped correctly")
    void historyReturnsMappedResponses() {
        TaxComputationRecord rec = new TaxComputationRecord(
                tenantId,
                employeeId,
                declarationId,
                fy.label(),
                "NEW",
                TaxTrigger.OFFICER,
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(75000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(925000),
                BigDecimal.valueOf(50000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(2000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(52000),
                "{\"taxableIncome\":925000}",
                Instant.now(),
                officerId,
                "system");

        given(taxComputationRepository.findByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        tenantId, employeeId, fy.label()))
                .willReturn(List.of(rec));

        List<TaxComputationRecordResponse> history = service.history(employeeId, fy.label());

        assertThat(history).hasSize(1);
        TaxComputationRecordResponse response = history.get(0);
        assertThat(response.regime()).isEqualTo("NEW");
        assertThat(response.trigger()).isEqualTo(TaxTrigger.OFFICER);
        assertThat(response.computedBy()).isEqualTo(officerId);
        assertThat(response.annualTax()).isEqualByComparingTo("52000");
    }

    @Test
    @DisplayName("D-153: the stored working carries every amount as a number")
    void storedWorkingCarriesNumbers() throws Exception {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(declarationId);
        decl.setTaxRegime("OLD");
        given(declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label()))
                .willReturn(Optional.of(decl));
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.OLD))
                .willReturn(sampleComputation(TaxRegime.OLD));
        given(taxComputationRepository.save(any(TaxComputationRecord.class))).willAnswer(inv -> inv.getArgument(0));

        TaxComputationRecord record = service.recalculate(employeeId, fy.label(), TaxTrigger.OFFICER, officerId);

        assertThat(record.getWorking()).doesNotContain("\"positive\"", "\"zero\"", "\"negative\"");
        JsonNode working = objectMapper.readTree(record.getWorking());
        assertThat(working.path("incomeFromSalary").isNumber()).isTrue();
        assertThat(working.path("incomeFromSalary").decimalValue()).isEqualByComparingTo("1200000");
        assertThat(working.path("grossTotalIncome").isNumber()).isTrue();
        assertThat(working.path("grossTotalIncome").decimalValue()).isEqualByComparingTo("1200000");
        assertThat(working.path("annualTax").decimalValue()).isEqualByComparingTo("62400");
        assertThat(working.path("houseProperty").path("housePropertyIncome").isNumber())
                .isTrue();
    }

    @Test
    @DisplayName("D-153: history returns a new working's amounts exactly and an old working without them, unharmed")
    void historyReadsNewAndOldWorkings() throws Exception {
        TaxComputationRecord fresh = historyRecord("{\"incomeFromSalary\":1200000.5000,\"rebate\":0.0000}");
        TaxComputationRecord old =
                historyRecord("{\"incomeFromSalary\":{\"zero\":false,\"positive\":true,\"negative\":false}}");
        given(taxComputationRepository.findByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        tenantId, employeeId, fy.label()))
                .willReturn(List.of(fresh, old));

        List<TaxComputationRecordResponse> history = service.history(employeeId, fy.label());

        assertThat(history).hasSize(2);
        String freshJson = objectMapper.writeValueAsString(history.get(0).working());
        assertThat(freshJson).isEqualTo("{\"incomeFromSalary\":1200000.5000,\"rebate\":0.0000}");
        JsonNode oldWorking = (JsonNode) history.get(1).working();
        assertThat(oldWorking.path("incomeFromSalary").isObject()).isTrue();
        assertThat(oldWorking.path("incomeFromSalary").path("positive").asBoolean())
                .isTrue();
    }

    private TaxComputationRecord historyRecord(String working) {
        return new TaxComputationRecord(
                tenantId,
                employeeId,
                declarationId,
                fy.label(),
                "OLD",
                TaxTrigger.OFFICER,
                BigDecimal.valueOf(1200000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(50000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1150000),
                BigDecimal.valueOf(50000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(2000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(52000),
                working,
                Instant.now(),
                officerId,
                "system");
    }

    @Test
    @DisplayName("historyOwn with no employee record says so (D-91), rather than naming an action the caller holds")
    void historyOwnWithoutCurrentEmployeeThrows() {
        given(employeeService.currentEmployee()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.historyOwn(fy.label()))
                .isInstanceOf(NoEmployeeRecordException.class)
                .hasMessageContaining("No employee record is linked");
    }
}
