package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.infinevo.payroll.taxcalc.exception.RegimeNotAvailableException;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceImplTest {

    @Mock
    private TaxDeclarationService taxDeclarationService;

    @Mock
    private TaxInputAssembler taxInputAssembler;

    @Mock
    private RegimeCalculator newRegimeCalculator;

    @Mock
    private TaxSummaryFiguresMapper figuresMapper;

    @Mock
    private TaxSummaryService taxSummaryService;

    private RegimeCalculators regimeCalculators;
    private TaxCalculationServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final FinancialYear fy = FinancialYear.of(2025, 2026);

    @BeforeEach
    void setUp() {
        given(newRegimeCalculator.regime()).willReturn(TaxRegime.NEW);
        regimeCalculators = new RegimeCalculators(List.of(newRegimeCalculator));
        service = new TaxCalculationServiceImpl(
                taxDeclarationService, taxInputAssembler, regimeCalculators, figuresMapper, taxSummaryService);
    }

    private EmployeeInvestmentDeclaration sampleDeclaration(String regime) {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(declarationId);
        decl.setTenantId(tenantId);
        decl.setEmployeeId(employeeId);
        decl.setFinancialYear(fy.label());
        decl.setTaxRegime(regime);
        return decl;
    }

    private TaxInput sampleInput() {
        return new TaxInput(
                employeeId,
                declarationId,
                new SalaryProjectionResult(Money.of(1575000), List.of(), List.of()),
                Map.of(),
                AgeCategory.GENERAL);
    }

    private TaxComputation sampleComputation() {
        return new TaxComputation(
                TaxRegime.NEW,
                fy.label(),
                Money.of(1575000),
                Money.ZERO,
                Money.of(75000),
                Money.of(1575000),
                Money.of(1500000),
                Money.of(105000),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.of(4200),
                Money.ZERO,
                Money.of(109200),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("compute() runs pure preview using requested regime")
    void computeUsesRequestedRegime() {
        EmployeeInvestmentDeclaration decl = sampleDeclaration("NEW");
        TaxInput input = sampleInput();
        TaxComputation expected = sampleComputation();

        given(taxDeclarationService.find(employeeId, fy.label())).willReturn(java.util.Optional.of(decl));
        given(taxInputAssembler.assemble(tenantId, employeeId, fy)).willReturn(input);
        given(newRegimeCalculator.compute(input, fy)).willReturn(expected);

        TaxComputation actual = service.compute(employeeId, fy, TaxRegime.NEW);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("compute() defaults to declaration header's regime when regime argument is null")
    void computeDefaultsToHeaderRegime() {
        EmployeeInvestmentDeclaration decl = sampleDeclaration("NEW");
        TaxInput input = sampleInput();
        TaxComputation expected = sampleComputation();

        given(taxDeclarationService.find(employeeId, fy.label())).willReturn(java.util.Optional.of(decl));
        given(taxInputAssembler.assemble(tenantId, employeeId, fy)).willReturn(input);
        given(newRegimeCalculator.compute(input, fy)).willReturn(expected);

        TaxComputation actual = service.compute(employeeId, fy, null);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("compute() defaults to NEW regime when declaration is absent")
    void computeWithoutDeclarationDefaultsToNewRegime() {
        com.infinevo.shared.tenant.TenantContext.set(tenantId);
        try {
            TaxInput input = sampleInput();
            TaxComputation expected = sampleComputation();

            given(taxDeclarationService.find(employeeId, fy.label())).willReturn(java.util.Optional.empty());
            given(taxInputAssembler.assemble(tenantId, employeeId, fy)).willReturn(input);
            given(newRegimeCalculator.compute(input, fy)).willReturn(expected);

            TaxComputation actual = service.compute(employeeId, fy, null);

            assertThat(actual).isSameAs(expected);
        } finally {
            com.infinevo.shared.tenant.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("compute() throws RegimeNotAvailableException when requested regime is not registered")
    void computeThrowsWhenRegimeNotAvailable() {
        EmployeeInvestmentDeclaration decl = sampleDeclaration("OLD");
        given(taxDeclarationService.find(employeeId, fy.label())).willReturn(java.util.Optional.of(decl));

        assertThatThrownBy(() -> service.compute(employeeId, fy, TaxRegime.OLD))
                .isInstanceOf(RegimeNotAvailableException.class);
    }

    @Test
    @DisplayName("computeAndRecord() computes each available regime and records summary figures")
    void computeAndRecordPersistsFigures() {
        EmployeeInvestmentDeclaration decl = sampleDeclaration("NEW");
        TaxInput input = sampleInput();
        TaxComputation comp = sampleComputation();
        TaxSummaryFigures figures = new TaxSummaryFigures(
                new BigDecimal("1575000"),
                new BigDecimal("1500000"),
                new BigDecimal("109200"),
                BigDecimal.ZERO,
                new BigDecimal("109200"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                7);

        given(taxDeclarationService.require(employeeId, fy.label())).willReturn(decl);
        given(taxInputAssembler.assemble(tenantId, employeeId, fy)).willReturn(input);
        given(newRegimeCalculator.compute(input, fy)).willReturn(comp);
        given(figuresMapper.toFigures(comp, fy)).willReturn(figures);

        Map<TaxRegime, TaxComputation> results = service.computeAndRecord(employeeId, fy);

        assertThat(results).containsEntry(TaxRegime.NEW, comp);
        verify(taxSummaryService).record(declarationId, "NEW", figures);
    }
}
