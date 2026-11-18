package com.infinevo.payroll.compliance.form24q;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.form16.Form16Service;
import com.infinevo.payroll.form16.Form16Statement;
import com.infinevo.payroll.form16.TaxBreakdown;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Form24QAnnexure2ServiceImpl} (W-89.3).
 */
class Form24QAnnexure2ServiceTest {

    private TaxDeductorService taxDeductorService;
    private EmployeeRepository employeeRepository;
    private EmployeeIdentificationService employeeIdentificationService;
    private Form16Service form16Service;
    private PayRunRepository payRunRepository;
    private EmployeePayRunRepository employeePayRunRepository;
    private EmployeePayRunLineRepository payRunLineRepository;

    private Form24QAnnexure2ServiceImpl service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        taxDeductorService = mock(TaxDeductorService.class);
        employeeRepository = mock(EmployeeRepository.class);
        employeeIdentificationService = mock(EmployeeIdentificationService.class);
        form16Service = mock(Form16Service.class);
        payRunRepository = mock(PayRunRepository.class);
        employeePayRunRepository = mock(EmployeePayRunRepository.class);
        payRunLineRepository = mock(EmployeePayRunLineRepository.class);

        service = new Form24QAnnexure2ServiceImpl(
                taxDeductorService,
                employeeRepository,
                employeeIdentificationService,
                form16Service,
                payRunRepository,
                employeePayRunRepository,
                payRunLineRepository);

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Throws DeductorNotSetException when deductor is not configured")
    void previewAnnexure2_missingDeductor_throwsConflict() {
        when(taxDeductorService.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewAnnexure2("2026-2027")).isInstanceOf(DeductorNotSetException.class);
    }

    @Test
    @DisplayName("Throws Form24QValidationException for invalid FY")
    void previewAnnexure2_invalidFy_throwsValidationException() {
        assertThatThrownBy(() -> service.previewAnnexure2("2026/27"))
                .isInstanceOf(Form24QValidationException.class)
                .hasMessageContaining("Invalid financial year");
    }

    @Test
    @DisplayName("Previews Annexure II with salary breakdown across employees")
    void previewAnnexure2_validEmployees_returnsSummaryAndRecords() {
        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRE12345F");
        when(deductor.pan()).thenReturn("ABCDE1234F");
        when(deductor.signatoryName()).thenReturn("Alice Signatory");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));

        UUID empId = UUID.randomUUID();
        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(empId);
        when(emp.getEmployeeNumber()).thenReturn("EMP001");
        when(emp.getFirstName()).thenReturn("Jane");
        when(emp.getLastName()).thenReturn("Smith");
        when(emp.getDateOfJoining()).thenReturn(LocalDate.of(2025, 6, 1));

        when(employeeRepository.findEmployedBetween(
                        eq(TENANT_ID),
                        any(LocalDate.class),
                        any(LocalDate.class),
                        eq(EmploymentStatus.ACTIVE),
                        eq(EmploymentStatus.TERMINATED)))
                .thenReturn(List.of(emp));

        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(eq(TENANT_ID), anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());

        EmployeeIdentificationResponse idResp = new EmployeeIdentificationResponse(
                UUID.randomUUID(),
                TENANT_ID,
                empId,
                "CITIZEN",
                "123456789012",
                "ABCDE1234F",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(employeeIdentificationService.find(empId)).thenReturn(Optional.of(idResp));

        TaxBreakdown breakdown = new TaxBreakdown(
                new BigDecimal("1200000.00"), // gross
                new BigDecimal("100000.00"), // hra
                new BigDecimal("50000.00"), // std ded
                new BigDecimal("2400.00"), // pt
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("150000.00"), // via
                new BigDecimal("897600.00"), // taxable
                new BigDecimal("94520.00"), // tax before rebate
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("3780.80"), // cess
                BigDecimal.ZERO,
                new BigDecimal("98300.80")); // annual tax

        Form16Statement form16 = mock(Form16Statement.class);
        when(form16.breakdown()).thenReturn(breakdown);
        when(form16.regime()).thenReturn("OLD");
        when(form16.annualTax()).thenReturn(new BigDecimal("98300.80"));
        when(form16.totalDeducted()).thenReturn(new BigDecimal("98300.80"));
        when(form16.balance()).thenReturn(BigDecimal.ZERO);

        when(form16Service.render(empId, "2026-2027")).thenReturn(form16);

        Annexure2PreviewResponse preview = service.previewAnnexure2("2026-2027");

        assertThat(preview.financialYear()).isEqualTo("2026-2027");
        assertThat(preview.assessmentYear()).isEqualTo("2027-2028");
        assertThat(preview.tan()).isEqualTo("BLRE12345F");
        assertThat(preview.totalEmployees()).isEqualTo(1);
        assertThat(preview.totalGrossSalary()).isEqualByComparingTo(new BigDecimal("1200000.00"));
        assertThat(preview.totalTaxableIncome()).isEqualByComparingTo(new BigDecimal("897600.00"));
        assertThat(preview.totalTaxPayable()).isEqualByComparingTo(new BigDecimal("98300.80"));
        assertThat(preview.totalTdsDeducted()).isEqualByComparingTo(new BigDecimal("98300.80"));
        assertThat(preview.totalShortfall()).isEqualByComparingTo(BigDecimal.ZERO);

        SalaryDetailRecord rec = preview.records().get(0);
        assertThat(rec.employeeName()).isEqualTo("Jane Smith");
        assertThat(rec.pan()).isEqualTo("ABCDE1234F");
        assertThat(rec.regime()).isEqualTo("OLD");
        assertThat(rec.exemptionsSection10()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(rec.standardDeduction()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(rec.professionalTax()).isEqualByComparingTo(new BigDecimal("2400.00"));
        assertThat(rec.chapterViaDeductions()).isEqualByComparingTo(new BigDecimal("150000.00"));
    }

    @Test
    @DisplayName("Exports CSV and NSDL TXT formats for Annexure II")
    void exportAnnexure2_csvAndTxtFormats() {
        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRE12345F");
        when(deductor.pan()).thenReturn("ABCDE1234F");
        when(deductor.signatoryName()).thenReturn("Alice");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));

        when(employeeRepository.findEmployedBetween(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        byte[] csv = service.exportAnnexure2("2026-2027", "CSV");
        String csvString = new String(csv, StandardCharsets.UTF_8);
        assertThat(csvString).contains("Sl No,Employee Code,PAN of Employee,Name of Employee,Regime");

        byte[] txt = service.exportAnnexure2("2026-2027", "TXT");
        String txtString = new String(txt, StandardCharsets.UTF_8);
        assertThat(txtString).contains("FH^1^NSDL-24Q").contains("BH^2^1^24Q");
    }
}
