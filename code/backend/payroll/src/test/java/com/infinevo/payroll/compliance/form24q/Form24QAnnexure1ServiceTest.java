package com.infinevo.payroll.compliance.form24q;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.challan.TdsChallan;
import com.infinevo.payroll.challan.TdsChallanAllocation;
import com.infinevo.payroll.challan.TdsChallanAllocationRepository;
import com.infinevo.payroll.challan.TdsChallanRepository;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * Unit tests for {@link Form24QAnnexure1ServiceImpl} (W-89.2).
 */
class Form24QAnnexure1ServiceTest {

    private TaxDeductorService taxDeductorService;
    private TdsChallanRepository challanRepository;
    private TdsChallanAllocationRepository challanAllocationRepository;
    private PayRunRepository payRunRepository;
    private EmployeePayRunRepository employeePayRunRepository;
    private EmployeePayRunLineRepository payRunLineRepository;
    private EmployeeService employeeService;
    private EmployeeIdentificationService employeeIdentificationService;
    private EmployeeTdsRepository employeeTdsRepository;

    private Form24QAnnexure1ServiceImpl service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        taxDeductorService = mock(TaxDeductorService.class);
        challanRepository = mock(TdsChallanRepository.class);
        challanAllocationRepository = mock(TdsChallanAllocationRepository.class);
        payRunRepository = mock(PayRunRepository.class);
        employeePayRunRepository = mock(EmployeePayRunRepository.class);
        payRunLineRepository = mock(EmployeePayRunLineRepository.class);
        employeeService = mock(EmployeeService.class);
        employeeIdentificationService = mock(EmployeeIdentificationService.class);
        employeeTdsRepository = mock(EmployeeTdsRepository.class);

        service = new Form24QAnnexure1ServiceImpl(
                taxDeductorService,
                challanRepository,
                challanAllocationRepository,
                payRunRepository,
                employeePayRunRepository,
                payRunLineRepository,
                employeeService,
                employeeIdentificationService,
                employeeTdsRepository);

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Throws DeductorNotSetException when employer tax deductor is not configured")
    void previewAnnexure1_missingDeductor_throwsConflict() {
        when(taxDeductorService.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewAnnexure1("2026-2027", "Q1"))
                .isInstanceOf(DeductorNotSetException.class);
    }

    @Test
    @DisplayName("Throws Form24QValidationException for invalid quarter or malformed FY")
    void previewAnnexure1_invalidInput_throwsValidationException() {
        assertThatThrownBy(() -> service.previewAnnexure1("2026-2027", "Q5"))
                .isInstanceOf(Form24QValidationException.class)
                .hasMessageContaining("Invalid quarter");

        assertThatThrownBy(() -> service.previewAnnexure1("2026/27", "Q1"))
                .isInstanceOf(Form24QValidationException.class)
                .hasMessageContaining("Invalid financial year");
    }

    @Test
    @DisplayName("Previews Annexure I with reconciled challan and deductee mapping")
    void previewAnnexure1_reconciledData_returnsCorrectSummary() {
        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRE12345F");
        when(deductor.pan()).thenReturn("ABCDE1234F");
        when(deductor.signatoryName()).thenReturn("Alice Signatory");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));

        UUID challanId = UUID.randomUUID();
        TdsChallan challan = mock(TdsChallan.class);
        when(challan.getId()).thenReturn(challanId);
        when(challan.getBsrCode()).thenReturn("0210045");
        when(challan.getChallanSerialNo()).thenReturn("12345");
        when(challan.getTenderDate()).thenReturn(LocalDate.of(2026, 7, 5));
        when(challan.getTaxAmount()).thenReturn(new BigDecimal("50000.00"));
        when(challan.getSurchargeAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getCessAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getInterestAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getFeePenaltyAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getTotalAmount()).thenReturn(new BigDecimal("50000.00"));
        when(challan.getMinorHead()).thenReturn("200");

        when(challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(
                        TENANT_ID, "2026-2027", "Q1"))
                .thenReturn(List.of(challan));

        UUID payRunId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        TdsChallanAllocation alloc = mock(TdsChallanAllocation.class);
        when(alloc.getChallanId()).thenReturn(challanId);
        when(alloc.getPayRunId()).thenReturn(payRunId);
        when(alloc.getEmployeeId()).thenReturn(empId);
        when(alloc.getAllocatedTax()).thenReturn(new BigDecimal("5000.00"));
        when(alloc.getAllocatedCess()).thenReturn(BigDecimal.ZERO);

        when(challanAllocationRepository.findByTenantIdAndChallanIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(alloc));

        PayRun payRun = mock(PayRun.class);
        when(payRun.getId()).thenReturn(payRunId);
        when(payRun.getPayDate()).thenReturn(LocalDate.of(2026, 4, 30));

        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(eq(TENANT_ID), anyCollection(), anyCollection()))
                .thenReturn(List.of(payRun));
        UUID empPayRunId = UUID.randomUUID();
        EmployeePayRun empRun = mock(EmployeePayRun.class);
        when(empRun.getId()).thenReturn(empPayRunId);
        when(empRun.getPayrunId()).thenReturn(payRunId);
        when(empRun.getEmployeeId()).thenReturn(empId);
        when(empRun.getGrossEarnings()).thenReturn(new BigDecimal("75000.00"));

        when(employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                        eq(TENANT_ID), anyCollection(), eq(InclusionStatus.INCLUDED)))
                .thenReturn(List.of(empRun));

        EmployeePayRunLine taxLine = mock(EmployeePayRunLine.class);
        when(taxLine.getEmployeePayrunId()).thenReturn(empPayRunId);
        when(taxLine.getAmount()).thenReturn(new BigDecimal("5000.00"));

        when(payRunLineRepository.findTaxLinesByPayrunIds(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(taxLine));

        EmployeeResponse empResp = new EmployeeResponse(
                empId,
                TENANT_ID,
                "EMP001",
                "John",
                null,
                "Doe",
                "MALE",
                LocalDate.of(2025, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "john@example.com",
                "9876543210",
                true,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now(),
                null,
                null);
        when(employeeService.get(empId)).thenReturn(empResp);

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
                Instant.now(),
                Instant.now());
        when(employeeIdentificationService.find(empId)).thenReturn(Optional.of(idResp));

        EmployeeTds tds = mock(EmployeeTds.class);
        when(tds.getRegime()).thenReturn(TaxRegime.NEW);
        when(employeeTdsRepository.findActive(TENANT_ID, empId, "2026-2027")).thenReturn(Optional.of(tds));

        Annexure1PreviewResponse preview = service.previewAnnexure1("2026-2027", "Q1");

        assertThat(preview.financialYear()).isEqualTo("2026-2027");
        assertThat(preview.quarter()).isEqualTo("Q1");
        assertThat(preview.assessmentYear()).isEqualTo("2027-2028");
        assertThat(preview.tan()).isEqualTo("BLRE12345F");
        assertThat(preview.totalChallans()).isEqualTo(1);
        assertThat(preview.totalChallanAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(preview.totalDeductees()).isEqualTo(1);
        assertThat(preview.totalGrossPaid()).isEqualByComparingTo(new BigDecimal("75000.00"));
        assertThat(preview.totalTdsDeducted()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(preview.isReconciled()).isTrue();
        assertThat(preview.missingPanCount()).isZero();

        DeducteeDetailRecord deductee = preview.deductees().get(0);
        assertThat(deductee.employeeName()).isEqualTo("John Doe");
        assertThat(deductee.pan()).isEqualTo("ABCDE1234F");
        assertThat(deductee.sectionCode()).isEqualTo("92B(1A)"); // New regime
        assertThat(deductee.grossAmount()).isEqualByComparingTo(new BigDecimal("75000.00"));
        assertThat(deductee.tdsAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(deductee.totalTaxDeposited()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(deductee.matchedChallanId()).isEqualTo(challanId);
        assertThat(deductee.challanSerialNumberInBatch()).isEqualTo(1);
    }

    @Test
    @DisplayName("Unallocated deductee has zero deposited tax and marks preview unreconciled")
    void previewAnnexure1_unallocatedDeductee_depositedIsZeroAndNotReconciled() {
        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRE12345F");
        when(deductor.pan()).thenReturn("ABCDE1234F");
        when(deductor.signatoryName()).thenReturn("Alice Signatory");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));

        UUID challanId = UUID.randomUUID();
        TdsChallan challan = mock(TdsChallan.class);
        when(challan.getId()).thenReturn(challanId);
        when(challan.getBsrCode()).thenReturn("0210045");
        when(challan.getChallanSerialNo()).thenReturn("12345");
        when(challan.getTenderDate()).thenReturn(LocalDate.of(2026, 7, 5));
        when(challan.getTaxAmount()).thenReturn(new BigDecimal("50000.00"));
        when(challan.getSurchargeAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getCessAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getInterestAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getFeePenaltyAmount()).thenReturn(BigDecimal.ZERO);
        when(challan.getTotalAmount()).thenReturn(new BigDecimal("50000.00"));
        when(challan.getMinorHead()).thenReturn("200");

        when(challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(
                        TENANT_ID, "2026-2027", "Q1"))
                .thenReturn(List.of(challan));

        // No allocations recorded for this challan
        when(challanAllocationRepository.findByTenantIdAndChallanIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(Collections.emptyList());

        UUID payRunId = UUID.randomUUID();
        PayRun payRun = mock(PayRun.class);
        when(payRun.getId()).thenReturn(payRunId);
        when(payRun.getPayDate()).thenReturn(LocalDate.of(2026, 4, 30));

        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(eq(TENANT_ID), anyCollection(), anyCollection()))
                .thenReturn(List.of(payRun));

        UUID empId = UUID.randomUUID();
        UUID empPayRunId = UUID.randomUUID();
        EmployeePayRun empRun = mock(EmployeePayRun.class);
        when(empRun.getId()).thenReturn(empPayRunId);
        when(empRun.getPayrunId()).thenReturn(payRunId);
        when(empRun.getEmployeeId()).thenReturn(empId);
        when(empRun.getGrossEarnings()).thenReturn(new BigDecimal("75000.00"));

        when(employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                        eq(TENANT_ID), anyCollection(), eq(InclusionStatus.INCLUDED)))
                .thenReturn(List.of(empRun));

        EmployeePayRunLine taxLine = mock(EmployeePayRunLine.class);
        when(taxLine.getEmployeePayrunId()).thenReturn(empPayRunId);
        when(taxLine.getAmount()).thenReturn(new BigDecimal("5000.00"));

        when(payRunLineRepository.findTaxLinesByPayrunIds(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(taxLine));

        EmployeeResponse empResp = mock(EmployeeResponse.class);
        when(empResp.employeeNumber()).thenReturn("EMP001");
        when(empResp.firstName()).thenReturn("John");
        when(empResp.lastName()).thenReturn("Doe");
        when(employeeService.get(empId)).thenReturn(empResp);

        EmployeeIdentificationResponse idResp = mock(EmployeeIdentificationResponse.class);
        when(idResp.panNumber()).thenReturn("ABCDE1234F");
        when(employeeIdentificationService.find(empId)).thenReturn(Optional.of(idResp));

        EmployeeTds tds = mock(EmployeeTds.class);
        when(tds.getRegime()).thenReturn(TaxRegime.NEW);
        when(employeeTdsRepository.findActive(TENANT_ID, empId, "2026-2027")).thenReturn(Optional.of(tds));

        Annexure1PreviewResponse preview = service.previewAnnexure1("2026-2027", "Q1");

        assertThat(preview.totalTdsDeducted()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(preview.totalTdsDeposited()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.isReconciled()).isFalse();

        DeducteeDetailRecord deductee = preview.deductees().get(0);
        assertThat(deductee.totalTaxDeposited()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(deductee.matchedChallanId()).isNull();
        assertThat(deductee.challanSerialNumberInBatch()).isZero();
    }

    @Test
    @DisplayName("Flags missing PAN as PANNOTAVBL with higher rate reason Y")
    void previewAnnexure1_missingPan_flagsPanNotAvblAndReasonY() {
        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRE12345F");
        when(deductor.pan()).thenReturn("ABCDE1234F");
        when(deductor.signatoryName()).thenReturn("Alice");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));
        when(challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        UUID payRunId = UUID.randomUUID();
        PayRun payRun = mock(PayRun.class);
        when(payRun.getId()).thenReturn(payRunId);
        when(payRun.getPayDate()).thenReturn(LocalDate.of(2026, 4, 30));
        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(any(), any(), any()))
                .thenReturn(List.of(payRun));

        UUID empId = UUID.randomUUID();
        UUID empPayRunId = UUID.randomUUID();
        EmployeePayRun empRun = mock(EmployeePayRun.class);
        when(empRun.getId()).thenReturn(empPayRunId);
        when(empRun.getPayrunId()).thenReturn(payRunId);
        when(empRun.getEmployeeId()).thenReturn(empId);
        when(empRun.getGrossEarnings()).thenReturn(new BigDecimal("50000.00"));
        when(employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(any(), any(), any()))
                .thenReturn(List.of(empRun));

        EmployeePayRunLine taxLine = mock(EmployeePayRunLine.class);
        when(taxLine.getEmployeePayrunId()).thenReturn(empPayRunId);
        when(taxLine.getAmount()).thenReturn(new BigDecimal("10000.00"));
        when(payRunLineRepository.findTaxLinesByPayrunIds(any(), any())).thenReturn(List.of(taxLine));

        when(employeeIdentificationService.find(empId)).thenReturn(Optional.empty()); // No PAN record
        when(employeeTdsRepository.findActive(any(), any(), any())).thenReturn(Optional.empty());

        Annexure1PreviewResponse preview = service.previewAnnexure1("2026-2027", "Q1");

        assertThat(preview.missingPanCount()).isEqualTo(1);
        assertThat(preview.missingPanEmployeeIds()).containsExactly(empId);

        DeducteeDetailRecord deductee = preview.deductees().get(0);
        assertThat(deductee.pan()).isEqualTo("PANNOTAVBL");
        assertThat(deductee.reasonForNonDeduction()).isEqualTo("Y");
        assertThat(deductee.sectionCode()).isEqualTo("92B"); // Default regular regime
    }

    @Test
    @DisplayName("Exports CSV and NSDL TXT files successfully")
    void exportAnnexure1_csvAndTxtFormats() {
        TaxDeductorResponse deductor = mock(TaxDeductorResponse.class);
        when(deductor.tan()).thenReturn("BLRE12345F");
        when(deductor.pan()).thenReturn("ABCDE1234F");
        when(deductor.signatoryName()).thenReturn("Alice");
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));
        when(challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        byte[] csv = service.exportAnnexure1("2026-2027", "Q1", "CSV");
        String csvString = new String(csv, StandardCharsets.UTF_8);
        assertThat(csvString).contains("Challan Serial No,BSR Code,Challan Date,Minor Head");

        byte[] txt = service.exportAnnexure1("2026-2027", "Q1", "TXT");
        String txtString = new String(txt, StandardCharsets.UTF_8);
        assertThat(txtString).contains("FH^1^NSDL-24Q").contains("BH^2^1^24Q");
    }
}
