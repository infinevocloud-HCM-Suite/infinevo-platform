package com.infinevo.payroll.compliance.form24q;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.payroll.form16.Form16Service;
import com.infinevo.payroll.form16.Form16Statement;
import com.infinevo.payroll.form16.TaxBreakdown;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Form24QAnnexure2ServiceImpl} (W-89.3): Annexure II figures are Form 16's. */
class Form24QAnnexure2ServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String FY = "2026-2027";

    private TaxDeductorService taxDeductorService;
    private EmployeeRepository employeeRepository;
    private EmployeeIdentificationService identificationService;
    private Form16Service form16Service;
    private PayRunRepository payRunRepository;
    private EmployeePayRunRepository employeePayRunRepository;
    private EmployeePayRunLineRepository lineRepository;
    private TenantProfileService tenantProfileService;
    private Form24QAnnexure2ServiceImpl service;

    private final List<Employee> employed = new ArrayList<>();
    private final List<PayRun> runs = new ArrayList<>();
    private final List<EmployeePayRun> employeeRuns = new ArrayList<>();
    private final List<EmployeePayRunLine> lines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        taxDeductorService = mock(TaxDeductorService.class);
        employeeRepository = mock(EmployeeRepository.class);
        identificationService = mock(EmployeeIdentificationService.class);
        form16Service = mock(Form16Service.class);
        payRunRepository = mock(PayRunRepository.class);
        employeePayRunRepository = mock(EmployeePayRunRepository.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        tenantProfileService = mock(TenantProfileService.class);
        service = new Form24QAnnexure2ServiceImpl(
                taxDeductorService,
                employeeRepository,
                identificationService,
                form16Service,
                payRunRepository,
                employeePayRunRepository,
                lineRepository,
                tenantProfileService);

        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRA12345B");
        when(deductor.pan()).thenReturn("AAACA1234A");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));
        TenantBranding branding = new TenantBranding("Acme Private Limited", null, null, null);
        when(tenantProfileService.branding()).thenReturn(Optional.of(branding));

        when(employeeRepository.findEmployedBetween(
                        eq(TENANT_ID),
                        eq(LocalDate.of(2026, 4, 1)),
                        eq(LocalDate.of(2027, 3, 31)),
                        eq(EmploymentStatus.ACTIVE),
                        eq(EmploymentStatus.TERMINATED)))
                .thenReturn(employed);
        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(eq(TENANT_ID), anyCollection(), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> periods = inv.getArgument(1);
                    Collection<?> statuses = inv.getArgument(2);
                    return runs.stream()
                            .filter(r -> periods.contains(r.getPeriod().toString()))
                            .filter(r -> statuses.contains(r.getStatus()))
                            .toList();
                });
        when(employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                        eq(TENANT_ID), anyCollection(), eq(InclusionStatus.INCLUDED)))
                .thenAnswer(inv -> {
                    Collection<?> ids = inv.getArgument(1);
                    return employeeRuns.stream()
                            .filter(e -> ids.contains(e.getPayrunId()))
                            .toList();
                });
        when(lineRepository.findTaxLinesByPayrunIds(eq(TENANT_ID), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> ids = inv.getArgument(1);
                    return lines.stream()
                            .filter(l -> ids.contains(l.getPayrunId()))
                            .toList();
                });

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Employee employee(String number, LocalDate joined, LocalDate left, String pan) {
        UUID id = UUID.randomUUID();
        Employee e = mock(Employee.class);
        when(e.getId()).thenReturn(id);
        when(e.getEmployeeNumber()).thenReturn(number);
        when(e.getFirstName()).thenReturn("Emp");
        when(e.getLastName()).thenReturn(number);
        when(e.getDateOfJoining()).thenReturn(joined);
        when(e.getTerminationDate()).thenReturn(left);
        employed.add(e);
        EmployeeIdentificationResponse ident = mock(EmployeeIdentificationResponse.class);
        when(ident.panNumber()).thenReturn(pan);
        when(identificationService.find(id)).thenReturn(Optional.of(ident));
        return e;
    }

    private PayRun run(YearMonth period, PayRunStatus status) {
        PayRun r = mock(PayRun.class);
        UUID id = UUID.randomUUID();
        when(r.getId()).thenReturn(id);
        when(r.getPeriod()).thenReturn(period);
        when(r.getStatus()).thenReturn(status);
        runs.add(r);
        return r;
    }

    private void paid(PayRun run, Employee e, String gross, String tds) {
        UUID eprId = UUID.randomUUID();
        UUID runId = run.getId();
        UUID employeeId = e.getId();
        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(employeeId);
        when(epr.getPayrunId()).thenReturn(runId);
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal(gross));
        employeeRuns.add(epr);
        EmployeePayRunLine line = mock(EmployeePayRunLine.class);
        when(line.getEmployeePayrunId()).thenReturn(eprId);
        when(line.getPayrunId()).thenReturn(runId);
        when(line.getAmount()).thenReturn(new BigDecimal(tds));
        lines.add(line);
    }

    /** Form 16 for an employee: gross 12,00,000, HRA 37,500, standard 75,000, PT 2,500. */
    private void form16(Employee e) {
        TaxBreakdown b = new TaxBreakdown(
                new BigDecimal("1200000"), // gross salary
                new BigDecimal("1085000"), // income from salary
                new BigDecimal("1095000"), // gross total income
                false,
                new BigDecimal("37500"), // HRA exemption
                new BigDecimal("75000"), // standard deduction
                new BigDecimal("2500"), // professional tax
                new BigDecimal("-5000"), // house property
                new BigDecimal("15000"), // other income
                new BigDecimal("150000"), // chapter VI-A
                new BigDecimal("945000"), // taxable income
                new BigDecimal("101500"), // tax before rebate
                BigDecimal.ZERO, // rebate
                BigDecimal.ZERO, // surcharge
                new BigDecimal("4060"), // cess
                new BigDecimal("12000"), // previous employer TDS
                new BigDecimal("105560")); // annual tax
        Form16Statement statement = mock(Form16Statement.class);
        when(statement.breakdown()).thenReturn(b);
        when(statement.regime()).thenReturn("OLD");
        when(statement.annualTax()).thenReturn(new BigDecimal("105560.00"));
        when(statement.totalDeducted()).thenReturn(new BigDecimal("90000.00"));
        when(statement.balance()).thenReturn(new BigDecimal("15560.00"));
        when(form16Service.render(e.getId(), FY)).thenReturn(statement);
    }

    @Test
    @DisplayName("Figures come from Form 16: gross, salary income, and section 10 = gross - section 16 - salary income")
    void figuresComeFromForm16() {
        Employee e = employee("E001", LocalDate.of(2020, 1, 1), null, "ABCDE1234F");
        PayRun apr = run(YearMonth.of(2026, 4), PayRunStatus.PAID);
        // The payroll gross differs from Form 16's on purpose: Annexure II must use Form 16's.
        paid(apr, e, "99999.00", "7500.00");
        form16(e);

        Annexure2PreviewResponse p = service.previewAnnexure2(FY);

        assertThat(p.records()).singleElement().satisfies(r -> {
            assertThat(r.employeeNumber()).isEqualTo("E001");
            assertThat(r.pan()).isEqualTo("ABCDE1234F");
            assertThat(r.regime()).isEqualTo("OLD");
            assertThat(r.employmentFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(r.employmentTo()).isEqualTo(LocalDate.of(2027, 3, 31));
            assertThat(r.grossSalary()).isEqualByComparingTo("1200000");
            assertThat(r.incomeChargeableUnderSalaries()).isEqualByComparingTo("1085000");
            assertThat(r.standardDeduction()).isEqualByComparingTo("75000");
            assertThat(r.professionalTax()).isEqualByComparingTo("2500");
            assertThat(r.totalDeductionsSection16()).isEqualByComparingTo("77500");
            assertThat(r.exemptionsSection10()).isEqualByComparingTo("37500");
            assertThat(r.grossSalary().subtract(r.totalDeductionsSection16()).subtract(r.exemptionsSection10()))
                    .isEqualByComparingTo(r.incomeChargeableUnderSalaries());
            assertThat(r.housePropertyIncome()).isEqualByComparingTo("-5000");
            assertThat(r.otherIncome()).isEqualByComparingTo("15000");
            assertThat(r.grossTotalIncome()).isEqualByComparingTo("1095000");
            assertThat(r.chapterViaDeductions()).isEqualByComparingTo("150000");
            assertThat(r.totalTaxableIncome()).isEqualByComparingTo("945000");
            assertThat(r.cess()).isEqualByComparingTo("4060");
            assertThat(r.totalTaxPayable()).isEqualByComparingTo("105560");
            assertThat(r.totalTdsDeducted()).isEqualByComparingTo("90000");
            assertThat(r.prevEmployerTds()).isEqualByComparingTo("12000");
            assertThat(r.shortfallOrExcess()).isEqualByComparingTo("15560");
        });
        assertThat(p.totalGrossSalary()).isEqualByComparingTo("1200000");
        assertThat(p.totalShortfall()).isEqualByComparingTo("15560");
        assertThat(p.companyName()).isEqualTo("Acme Private Limited");
        assertThat(p.assessmentYear()).isEqualTo("2027-2028");
    }

    @Test
    @DisplayName("Only employees paid in the year are listed; joining and leaving dates bound the period")
    void onlyEmployeesPaidInTheYear() {
        Employee paidJoiner = employee("E001", LocalDate.of(2026, 9, 15), null, "ABCDE1234F");
        Employee leaver = employee("E002", LocalDate.of(2019, 1, 1), LocalDate.of(2026, 12, 31), "not a pan");
        Employee neverPaid = employee("E003", LocalDate.of(2020, 1, 1), null, "BCDEF2345G");
        Employee draftOnly = employee("E004", LocalDate.of(2020, 1, 1), null, "CDEFG3456H");
        PayRun oct = run(YearMonth.of(2026, 10), PayRunStatus.APPROVED);
        PayRun nov = run(YearMonth.of(2026, 11), PayRunStatus.COMPUTED);
        PayRun lastYear = run(YearMonth.of(2026, 3), PayRunStatus.PAID);
        paid(oct, paidJoiner, "100000.00", "5000.00");
        paid(oct, leaver, "100000.00", "5000.00");
        paid(nov, draftOnly, "100000.00", "5000.00");
        paid(lastYear, neverPaid, "100000.00", "5000.00");
        form16(paidJoiner);
        form16(leaver);

        Annexure2PreviewResponse p = service.previewAnnexure2(FY);

        assertThat(p.records()).extracting(SalaryDetailRecord::employeeNumber).containsExactly("E001", "E002");
        assertThat(p.records())
                .extracting(SalaryDetailRecord::serialNumberInBatch)
                .containsExactly(1, 2);
        assertThat(p.totalEmployees()).isEqualTo(2);
        assertThat(p.records().get(0).employmentFrom()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(p.records().get(1).employmentTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(p.records().get(1).pan()).isEqualTo("PANNOTAVBL");
        verify(form16Service, never()).render(neverPaid.getId(), FY);
        verify(form16Service, never()).render(draftOnly.getId(), FY);
    }

    @Test
    @DisplayName("A Form 16 that cannot be worked out stops the statement; no made-up figures")
    void form16FailurePropagates() {
        Employee e = employee("E001", LocalDate.of(2020, 1, 1), null, "ABCDE1234F");
        paid(run(YearMonth.of(2026, 4), PayRunStatus.PAID), e, "100000.00", "5000.00");
        IllegalStateException boom = new IllegalStateException("no salary structure");
        when(form16Service.render(any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> service.previewAnnexure2(FY)).isSameAs(boom);
        assertThatThrownBy(() -> service.exportAnnexure2(FY, "CSV")).isSameAs(boom);
    }

    @Test
    void missingDeductorIsDeductorNotSet() {
        when(taxDeductorService.current()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.previewAnnexure2(FY)).isInstanceOf(DeductorNotSetException.class);
    }

    @Test
    void badYearIsAValidationError() {
        assertThatThrownBy(() -> service.previewAnnexure2("2026-27")).isInstanceOf(Form24QValidationException.class);
    }

    @Test
    @DisplayName("The TXT portal file is refused until Protean publishes the Q4 layout; the CSV is made")
    void txtExportRefused() {
        Employee e = employee("E001", LocalDate.of(2020, 1, 1), null, "ABCDE1234F");
        paid(run(YearMonth.of(2026, 4), PayRunStatus.PAID), e, "100000.00", "5000.00");
        form16(e);

        assertThatThrownBy(() -> service.exportAnnexure2(FY, "TXT"))
                .isInstanceOf(Form24QValidationException.class)
                .hasMessageContaining("Q4");
        assertThatThrownBy(() -> service.exportAnnexure2(FY, "PDF")).isInstanceOf(Form24QValidationException.class);
        assertThat(service.exportAnnexure2(FY, "csv")).isNotEmpty();
    }
}
