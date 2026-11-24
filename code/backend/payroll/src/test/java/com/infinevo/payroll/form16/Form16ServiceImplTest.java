package com.infinevo.payroll.form16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.org.DesignationResponse;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PeriodTaxTotal;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.taxcalc.RegimeCalculator;
import com.infinevo.payroll.taxcalc.RegimeCalculators;
import com.infinevo.payroll.taxcalc.TaxInputAssembler;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome.HousePropertyResult;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRepository;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsService;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * D-149: who the statement names — the tenant as deductor, the designation by name. D-150: no TDS record,
 * approved runs not yet paid, and the old-regime working when gross total income was floored at zero.
 */
class Form16ServiceImplTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DESIGNATION = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String FY = "2026-2027";

    private final TaxDeductorService taxDeductorService = mock(TaxDeductorService.class);
    private final EmployeeTdsService employeeTdsService = mock(EmployeeTdsService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final TenantProfileService tenantProfileService = mock(TenantProfileService.class);
    private final DesignationService designationService = mock(DesignationService.class);
    private final EmployeeResponse employee = mock(EmployeeResponse.class);
    private final TaxComputationRepository taxComputationRepository = mock(TaxComputationRepository.class);
    private final EmployeePayRunLineRepository payRunLineRepository = mock(EmployeePayRunLineRepository.class);
    private final TaxInputAssembler taxInputAssembler = mock(TaxInputAssembler.class);
    private final RegimeCalculators regimeCalculators = mock(RegimeCalculators.class);

    private Form16ServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        when(taxDeductorService.current()).thenReturn(Optional.of(mock(TaxDeductorResponse.class)));
        when(employee.firstName()).thenReturn("Asha");
        when(employee.lastName()).thenReturn("Rao");
        when(employeeService.get(EMPLOYEE)).thenReturn(employee);
        EmployeeTds tds = mock(EmployeeTds.class);
        when(tds.getAnnualTax()).thenReturn(new BigDecimal("120000.00"));
        when(employeeTdsService.active(EMPLOYEE, FY)).thenReturn(Optional.of(tds));

        service = new Form16ServiceImpl(
                taxDeductorService,
                employeeTdsService,
                taxComputationRepository,
                payRunLineRepository,
                mock(PayRunRepository.class),
                employeeService,
                mock(EmployeeIdentificationService.class),
                mock(PriorPayrollTaxQuery.class),
                tenantProfileService,
                designationService,
                taxInputAssembler,
                regimeCalculators,
                new NoOpTransactionManager());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The deductor is the tenant by name and the designation prints by name")
    void namesTheTenantAndTheDesignation() {
        when(tenantProfileService.branding())
                .thenReturn(Optional.of(new TenantBranding("Acme Manufacturing", null, null, null, null)));
        when(employee.designationId()).thenReturn(DESIGNATION);
        when(designationService.list(false))
                .thenReturn(List.of(new DesignationResponse(
                        DESIGNATION, TENANT, "SE", "Senior Engineer", true, Instant.now(), Instant.now())));

        Form16Statement statement = service.render(EMPLOYEE, FY);

        assertThat(statement.deductor().name()).isEqualTo("Acme Manufacturing");
        assertThat(statement.employee().name()).isEqualTo("Asha Rao");
        assertThat(statement.employee().designation()).isEqualTo("Senior Engineer");
    }

    @Test
    @DisplayName("A designation that is gone, or none at all, prints blank and never an id")
    void missingDesignationPrintsBlank() {
        when(tenantProfileService.branding()).thenReturn(Optional.empty());
        when(employee.designationId()).thenReturn(DESIGNATION);
        when(designationService.list(false)).thenReturn(List.of());

        Form16Statement gone = service.render(EMPLOYEE, FY);
        assertThat(gone.employee().designation()).isEmpty();
        assertThat(gone.deductor().name()).isEmpty();

        when(employee.designationId()).thenReturn(null);
        assertThat(service.render(EMPLOYEE, FY).employee().designation()).isEmpty();
    }

    @Test
    @DisplayName("D-150: no TDS record prints nil tax, the tax actually paid, and a note; not a 404")
    void noTdsRecordIsANilStatement() {
        when(employeeTdsService.active(EMPLOYEE, FY)).thenReturn(Optional.empty());
        when(payRunLineRepository.sumTaxLinesByPeriod(eq(TENANT), eq(EMPLOYEE), anyString(), anyString()))
                .thenReturn(List.of(total("2026-04", "5000.00")));

        Form16Statement statement = service.render(EMPLOYEE, FY);

        assertThat(statement.statementNote()).isEqualTo("NO_TDS_RECORD");
        assertThat(statement.breakdownNote()).isEqualTo("NO_TDS_RECORD");
        assertThat(statement.breakdown()).isNull();
        assertThat(statement.annualTax()).isEqualByComparingTo("0");
        assertThat(statement.totalDeducted()).isEqualByComparingTo("5000.00");
        assertThat(statement.regime()).isNull();
    }

    @Test
    @DisplayName("D-150: an unknown employee is still not found")
    void unknownEmployeeIsStillNotFound() {
        UUID stranger = UUID.randomUUID();
        when(employeeService.get(stranger)).thenThrow(new EmployeeService.NotFoundException(stranger));

        assertThatThrownBy(() -> service.render(stranger, FY)).isInstanceOf(EmployeeService.NotFoundException.class);
    }

    @Test
    @DisplayName("D-150: tax on an approved run not yet paid is its own figure, never part of tax deducted")
    void approvedNotPaidIsShownApart() {
        when(payRunLineRepository.sumTaxLinesByPeriod(eq(TENANT), eq(EMPLOYEE), anyString(), anyString()))
                .thenReturn(List.of(total("2026-09", "10000.00")));
        when(payRunLineRepository.sumApprovedUnpaidTaxLinesByPeriod(eq(TENANT), eq(EMPLOYEE), anyString(), anyString()))
                .thenReturn(List.of(total("2026-10", "8000.00")));

        Form16Statement statement = service.render(EMPLOYEE, FY);

        assertThat(statement.totalDeducted()).isEqualByComparingTo("10000.00");
        assertThat(statement.approvedNotPaid()).isEqualByComparingTo("8000.00");
        assertThat(statement.balance()).isEqualByComparingTo("110000.00");
    }

    @Test
    @DisplayName("D-150 F-2: salary 1,50,000 and a 2,00,000 loss print gross salary 1,50,000, not 2,50,000")
    void flooredOldRegimeTotalPrintsTheTrueSalary() {
        nilTaxRecord();
        TaxComputationRecord comp = flooredComputation("{\"incomeFromSalary\":{\"zero\":false}}");
        when(taxComputationRepository.findFirstByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        TENANT, EMPLOYEE, FY))
                .thenReturn(Optional.of(comp));
        TaxComputation again = mock(TaxComputation.class);
        when(again.grossTotalIncome()).thenReturn(Money.ZERO);
        when(again.houseProperty())
                .thenReturn(new HousePropertyResult(Money.of("-200000"), Money.of("200000"), Money.ZERO, false));
        when(again.otherIncome()).thenReturn(Money.ZERO);
        when(again.annualTax()).thenReturn(Money.ZERO);
        when(again.incomeFromSalary()).thenReturn(Money.of("100000"));
        RegimeCalculator calculator = mock(RegimeCalculator.class);
        when(calculator.compute(any(), any())).thenReturn(again);
        when(regimeCalculators.forRegime(TaxRegime.OLD)).thenReturn(calculator);

        TaxBreakdown b = service.render(EMPLOYEE, FY).breakdown();

        assertThat(b.grossSalary()).isEqualByComparingTo("150000");
        assertThat(b.incomeFromSalary()).isEqualByComparingTo("100000");
        assertThat(b.housePropertyIncome()).isEqualByComparingTo("-200000");
        assertThat(b.grossTotalIncome()).isEqualByComparingTo("0");
        assertThat(b.grossTotalIncomeFloored()).isTrue();
    }

    @Test
    @DisplayName("D-150 F-2: a number in the working is read first and the engine is not run again")
    void incomeFromSalaryInTheWorkingIsReadFirst() {
        nilTaxRecord();
        TaxComputationRecord comp = flooredComputation("{\"incomeFromSalary\":100000.00}");
        when(taxComputationRepository.findFirstByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        TENANT, EMPLOYEE, FY))
                .thenReturn(Optional.of(comp));

        TaxBreakdown b = service.render(EMPLOYEE, FY).breakdown();

        assertThat(b.grossSalary()).isEqualByComparingTo("150000");
        assertThat(b.grossTotalIncomeFloored()).isTrue();
        verify(regimeCalculators, never()).forRegime(any());
    }

    @Test
    @DisplayName("D-150 F-2: a recompute that no longer matches the stored computation is not used")
    void recomputeThatDiffersFallsBack() {
        nilTaxRecord();
        TaxComputationRecord comp = flooredComputation("{}");
        when(taxComputationRepository.findFirstByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        TENANT, EMPLOYEE, FY))
                .thenReturn(Optional.of(comp));
        TaxComputation again = mock(TaxComputation.class);
        when(again.grossTotalIncome()).thenReturn(Money.of("40000"));
        RegimeCalculator calculator = mock(RegimeCalculator.class);
        when(calculator.compute(any(), any())).thenReturn(again);
        when(regimeCalculators.forRegime(TaxRegime.OLD)).thenReturn(calculator);

        TaxBreakdown b = service.render(EMPLOYEE, FY).breakdown();

        // The working-back from the stored zero: the old behaviour, which still adds up.
        assertThat(b.incomeFromSalary()).isEqualByComparingTo("200000");
        assertThat(b.grossTotalIncomeFloored()).isFalse();
    }

    @Test
    @DisplayName("D-150: the working's income from salary is read only when it is a number")
    void workingReader() {
        assertThat(Form16ServiceImpl.incomeFromSalaryInWorking("{\"incomeFromSalary\":123.45}"))
                .isEqualByComparingTo("123.45");
        assertThat(Form16ServiceImpl.incomeFromSalaryInWorking("{\"incomeFromSalary\":\"99\"}"))
                .isEqualByComparingTo("99");
        assertThat(Form16ServiceImpl.incomeFromSalaryInWorking("{\"incomeFromSalary\":{\"positive\":true}}"))
                .isNull();
        assertThat(Form16ServiceImpl.incomeFromSalaryInWorking("not json")).isNull();
        assertThat(Form16ServiceImpl.incomeFromSalaryInWorking(null)).isNull();
    }

    private void nilTaxRecord() {
        EmployeeTds tds = mock(EmployeeTds.class);
        when(tds.getAnnualTax()).thenReturn(BigDecimal.ZERO);
        when(employeeTdsService.active(EMPLOYEE, FY)).thenReturn(Optional.of(tds));
    }

    /** Old regime: salary 1,50,000, standard 50,000, house-property loss 2,00,000; the total floored to 0. */
    private static TaxComputationRecord flooredComputation(String working) {
        TaxComputationRecord comp = mock(TaxComputationRecord.class);
        when(comp.getTenantId()).thenReturn(TENANT);
        when(comp.getRegime()).thenReturn("OLD");
        when(comp.getGrossTotalIncome()).thenReturn(BigDecimal.ZERO);
        when(comp.getHraExemption()).thenReturn(BigDecimal.ZERO);
        when(comp.getStandardDeduction()).thenReturn(new BigDecimal("50000"));
        when(comp.getProfessionalTax()).thenReturn(BigDecimal.ZERO);
        when(comp.getHousePropertyIncome()).thenReturn(new BigDecimal("-200000"));
        when(comp.getOtherIncome()).thenReturn(BigDecimal.ZERO);
        when(comp.getChapterVia()).thenReturn(BigDecimal.ZERO);
        when(comp.getTaxableIncome()).thenReturn(BigDecimal.ZERO);
        when(comp.getAnnualTax()).thenReturn(BigDecimal.ZERO);
        when(comp.getWorking()).thenReturn(working);
        return comp;
    }

    private static PeriodTaxTotal total(String period, String amount) {
        return new PeriodTaxTotal() {
            @Override
            public String getPeriod() {
                return period;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    /** A transaction manager with no resource: the rerun's template runs its callback and rolls back. */
    static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
