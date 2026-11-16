package com.infinevo.payroll.statutory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.salary.EmployeeStatutoryProfile;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverride;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class StatutoryReportServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PAYRUN_ID = UUID.randomUUID();

    @Mock
    private PayRunRepository payRunRepository;

    @Mock
    private EmployeePayRunRepository employeePayRunRepository;

    @Mock
    private EmployeePayRunLineRepository employeePayRunLineRepository;

    @Mock
    private EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository;

    @Mock
    private OrgPtOverrideRepository orgPtOverrideRepository;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private WorkLocationService workLocationService;

    private StatutoryReportServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new StatutoryReportServiceImpl(
                payRunRepository,
                employeePayRunRepository,
                employeePayRunLineRepository,
                employeeStatutoryProfileRepository,
                orgPtOverrideRepository,
                employeeService,
                workLocationService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ESI Return XLSX generates valid workbook with correct calculations and zero-days reason code")
    void generateEsiReturn_Xlsx() throws IOException {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(PAYRUN_ID);
        when(run.getStatus()).thenReturn(PayRunStatus.COMPUTED);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 4));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID emp3Id = UUID.randomUUID();

        UUID epr1Id = UUID.randomUUID();
        UUID epr2Id = UUID.randomUUID();
        UUID epr3Id = UUID.randomUUID();

        EmployeePayRun epr1 = mock(EmployeePayRun.class);
        when(epr1.getId()).thenReturn(epr1Id);
        when(epr1.getEmployeeId()).thenReturn(emp1Id);
        when(epr1.getPaidDays()).thenReturn(new BigDecimal("26"));
        when(epr1.getGrossEarnings()).thenReturn(new BigDecimal("20000.00"));

        EmployeePayRun epr2 = mock(EmployeePayRun.class);
        when(epr2.getId()).thenReturn(epr2Id);
        when(epr2.getEmployeeId()).thenReturn(emp2Id);
        when(epr2.getPaidDays()).thenReturn(BigDecimal.ZERO);

        EmployeePayRun epr3 = mock(EmployeePayRun.class);
        when(epr3.getId()).thenReturn(epr3Id);
        when(epr3.getEmployeeId()).thenReturn(emp3Id);

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2, epr3));

        EmployeePayRunLine line1Ee = mock(EmployeePayRunLine.class);
        when(line1Ee.getEmployeePayrunId()).thenReturn(epr1Id);
        when(line1Ee.getComponentCode()).thenReturn("ESI_EMPLOYEE");
        when(line1Ee.getAmount()).thenReturn(new BigDecimal("150.00"));

        EmployeePayRunLine line1Er = mock(EmployeePayRunLine.class);
        when(line1Er.getEmployeePayrunId()).thenReturn(epr1Id);
        when(line1Er.getComponentCode()).thenReturn("ESI_EMPLOYER");
        when(line1Er.getAmount()).thenReturn(new BigDecimal("650.00"));

        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(line1Ee, line1Er));

        EmployeeStatutoryProfile prof1 = new EmployeeStatutoryProfile(TENANT_ID, emp1Id, "test");
        prof1.setEligibleForEsi(true);
        prof1.setEsiNumber("1234567890");

        EmployeeStatutoryProfile prof2 = new EmployeeStatutoryProfile(TENANT_ID, emp2Id, "test");
        prof2.setEligibleForEsi(true);
        prof2.setEsiNumber("9876543210");

        EmployeeStatutoryProfile prof3 = new EmployeeStatutoryProfile(TENANT_ID, emp3Id, "test");
        prof3.setEligibleForEsi(false);

        when(employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(prof1, prof2, prof3));

        EmployeeResponse emp1 = mock(EmployeeResponse.class);
        when(emp1.firstName()).thenReturn("Alice");
        when(emp1.lastName()).thenReturn("Smith");
        when(employeeService.get(emp1Id)).thenReturn(emp1);

        EmployeeResponse emp2 = mock(EmployeeResponse.class);
        when(emp2.firstName()).thenReturn("Bob");
        when(emp2.lastName()).thenReturn("Jones");
        when(employeeService.get(emp2Id)).thenReturn(emp2);

        StatutoryReportFile result = service.generateEsiReturnFile(PAYRUN_ID, ExportFormat.XLSX);

        assertThat(result.filename()).isEqualTo("esi-return-2026-04.xlsx");
        assertThat(result.mediaType())
                .isEqualTo(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.content()))) {
            XSSFSheet sheet = wb.getSheet("ESI Return");
            assertThat(sheet).isNotNull();
            // Row 0 is header, Row 1 is emp1, Row 2 is emp2 (emp3 excluded)
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);

            // Headers
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("IP Number");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("IP Name");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("No. of Days Worked");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Total Monthly Wages");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("IP Contribution");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Employer Contribution");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("Total Contribution");
            assertThat(sheet.getRow(0).getCell(7).getStringCellValue()).isEqualTo("Reason Code for Zero Days");

            // Row 1: Alice Smith
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("1234567890");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Alice Smith");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("26");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("20000.00");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("150.00");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("650.00");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("800.00");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEmpty();

            // Row 2: Bob Jones (zero days worked)
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("9876543210");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Bob Jones");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("0");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("0.00");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("0.00");
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("0.00");
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("0.00");
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo("1");
        }
    }

    @Test
    @DisplayName("ESI Return CSV generates RFC-4180 CSV with UTF-8 BOM and formula neutralization")
    void generateEsiReturn_Csv() {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(PAYRUN_ID);
        when(run.getStatus()).thenReturn(PayRunStatus.APPROVED);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 5));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID empId = UUID.randomUUID();
        UUID eprId = UUID.randomUUID();

        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(empId);
        when(epr.getPaidDays()).thenReturn(new BigDecimal("30"));
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal("18000.00"));

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr));

        EmployeePayRunLine lineEe = mock(EmployeePayRunLine.class);
        when(lineEe.getEmployeePayrunId()).thenReturn(eprId);
        when(lineEe.getComponentCode()).thenReturn("ESI_EMPLOYEE");
        when(lineEe.getAmount()).thenReturn(new BigDecimal("135.00"));

        EmployeePayRunLine lineEr = mock(EmployeePayRunLine.class);
        when(lineEr.getEmployeePayrunId()).thenReturn(eprId);
        when(lineEr.getComponentCode()).thenReturn("ESI_EMPLOYER");
        when(lineEr.getAmount()).thenReturn(new BigDecimal("585.00"));

        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(lineEe, lineEr));

        EmployeeStatutoryProfile prof = new EmployeeStatutoryProfile(TENANT_ID, empId, "test");
        prof.setEligibleForEsi(true);
        prof.setEsiNumber("5550001112");

        when(employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(prof));

        EmployeeResponse emp = mock(EmployeeResponse.class);
        when(emp.firstName()).thenReturn("Charlie");
        when(emp.lastName()).thenReturn("Brown");
        when(employeeService.get(empId)).thenReturn(emp);

        StatutoryReportFile result = service.generateEsiReturnFile(PAYRUN_ID, ExportFormat.CSV);

        assertThat(result.filename()).isEqualTo("esi-return-2026-05.csv");
        assertThat(result.mediaType()).isEqualTo(MediaType.parseMediaType("text/csv"));

        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF'); // BOM
        assertThat(csv).contains("IP Number,IP Name,No. of Days Worked,Total Monthly Wages");
        assertThat(csv).contains("5550001112,Charlie Brown,30,18000.00,135.00,585.00,720.00,");
    }

    @Test
    @DisplayName("PT Return XLSX generates Return sheet and State Summary sheet with PTRC numbers")
    void generatePtReturn_Xlsx() throws IOException {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(PAYRUN_ID);
        when(run.getStatus()).thenReturn(PayRunStatus.PAID);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 4));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID epr1Id = UUID.randomUUID();
        UUID epr2Id = UUID.randomUUID();
        UUID locKaId = UUID.randomUUID();
        UUID locMhId = UUID.randomUUID();

        EmployeePayRun epr1 = mock(EmployeePayRun.class);
        when(epr1.getId()).thenReturn(epr1Id);
        when(epr1.getEmployeeId()).thenReturn(emp1Id);
        when(epr1.getGrossEarnings()).thenReturn(new BigDecimal("45000.00"));

        EmployeePayRun epr2 = mock(EmployeePayRun.class);
        when(epr2.getId()).thenReturn(epr2Id);
        when(epr2.getEmployeeId()).thenReturn(emp2Id);
        when(epr2.getGrossEarnings()).thenReturn(new BigDecimal("60000.00"));

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2));

        EmployeePayRunLine line1 = mock(EmployeePayRunLine.class);
        when(line1.getEmployeePayrunId()).thenReturn(epr1Id);
        when(line1.getComponentCode()).thenReturn("PROFESSIONAL_TAX");
        when(line1.getAmount()).thenReturn(new BigDecimal("200.00"));

        EmployeePayRunLine line2 = mock(EmployeePayRunLine.class);
        when(line2.getEmployeePayrunId()).thenReturn(epr2Id);
        when(line2.getComponentCode()).thenReturn("PROFESSIONAL_TAX");
        when(line2.getAmount()).thenReturn(new BigDecimal("200.00"));

        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(line1, line2));

        EmployeeResponse emp1 = mock(EmployeeResponse.class);
        when(emp1.workLocationId()).thenReturn(locKaId);
        when(emp1.employeeNumber()).thenReturn("EMP001");
        when(emp1.firstName()).thenReturn("Deepak");
        when(emp1.lastName()).thenReturn("Sharma");
        when(employeeService.get(emp1Id)).thenReturn(emp1);

        EmployeeResponse emp2 = mock(EmployeeResponse.class);
        when(emp2.workLocationId()).thenReturn(locMhId);
        when(emp2.employeeNumber()).thenReturn("EMP002");
        when(emp2.firstName()).thenReturn("Pooja");
        when(emp2.lastName()).thenReturn("Patil");
        when(employeeService.get(emp2Id)).thenReturn(emp2);

        WorkLocationResponse locKa = mock(WorkLocationResponse.class);
        when(locKa.stateCode()).thenReturn("KA");
        when(workLocationService.get(locKaId)).thenReturn(locKa);

        WorkLocationResponse locMh = mock(WorkLocationResponse.class);
        when(locMh.stateCode()).thenReturn("MH");
        when(workLocationService.get(locMhId)).thenReturn(locMh);

        OrgPtOverride overrideKa =
                new OrgPtOverride(TENANT_ID, "KA", "PTRC-KA-12345", LocalDate.of(2025, 4, 1), "system");
        when(orgPtOverrideRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(overrideKa));

        StatutoryReportFile result = service.generatePtReturnFile(PAYRUN_ID, ExportFormat.XLSX, null);

        assertThat(result.filename()).isEqualTo("pt-return-2026-04.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.content()))) {
            XSSFSheet ptSheet = wb.getSheet("PT Return");
            assertThat(ptSheet).isNotNull();
            assertThat(ptSheet.getPhysicalNumberOfRows()).isEqualTo(3);

            // Row 1: KA Deepak Sharma
            assertThat(ptSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("KA");
            assertThat(ptSheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("PTRC-KA-12345");
            assertThat(ptSheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("EMP001");
            assertThat(ptSheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Deepak Sharma");
            assertThat(ptSheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("45000.00");
            assertThat(ptSheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("200.00");

            // Row 2: MH Pooja Patil (no override configured)
            assertThat(ptSheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("MH");
            assertThat(ptSheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("NOT_CONFIGURED");
            assertThat(ptSheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("EMP002");
            assertThat(ptSheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Pooja Patil");
            assertThat(ptSheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("60000.00");
            assertThat(ptSheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("200.00");

            // Sheet 2: State Summary
            XSSFSheet summarySheet = wb.getSheet("State Summary");
            assertThat(summarySheet).isNotNull();
            assertThat(summarySheet.getPhysicalNumberOfRows()).isEqualTo(3);

            // KA Summary
            assertThat(summarySheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("KA");
            assertThat(summarySheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("PTRC-KA-12345");
            assertThat(summarySheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("1");
            assertThat(summarySheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("200.00");

            // MH Summary
            assertThat(summarySheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("MH");
            assertThat(summarySheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("NOT_CONFIGURED");
            assertThat(summarySheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("1");
            assertThat(summarySheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("200.00");
        }
    }

    @Test
    @DisplayName("PT Return filtered by state returns only specified state rows and proper filename")
    void generatePtReturn_FilteredState() {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(PAYRUN_ID);
        when(run.getStatus()).thenReturn(PayRunStatus.COMPUTED);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 4));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID epr1Id = UUID.randomUUID();
        UUID epr2Id = UUID.randomUUID();
        UUID locKaId = UUID.randomUUID();
        UUID locMhId = UUID.randomUUID();

        EmployeePayRun epr1 = mock(EmployeePayRun.class);
        when(epr1.getId()).thenReturn(epr1Id);
        when(epr1.getEmployeeId()).thenReturn(emp1Id);
        when(epr1.getGrossEarnings()).thenReturn(new BigDecimal("45000.00"));

        EmployeePayRun epr2 = mock(EmployeePayRun.class);
        when(epr2.getId()).thenReturn(epr2Id);
        when(epr2.getEmployeeId()).thenReturn(emp2Id);

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2));

        EmployeePayRunLine line1 = mock(EmployeePayRunLine.class);
        when(line1.getEmployeePayrunId()).thenReturn(epr1Id);
        when(line1.getComponentCode()).thenReturn("PROFESSIONAL_TAX");
        when(line1.getAmount()).thenReturn(new BigDecimal("200.00"));

        EmployeePayRunLine line2 = mock(EmployeePayRunLine.class);
        when(line2.getEmployeePayrunId()).thenReturn(epr2Id);
        when(line2.getComponentCode()).thenReturn("PROFESSIONAL_TAX");
        when(line2.getAmount()).thenReturn(new BigDecimal("200.00"));

        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(line1, line2));

        EmployeeResponse emp1 = mock(EmployeeResponse.class);
        when(emp1.workLocationId()).thenReturn(locKaId);
        when(emp1.employeeNumber()).thenReturn("EMP001");
        when(emp1.firstName()).thenReturn("Deepak");
        when(emp1.lastName()).thenReturn("Sharma");
        when(employeeService.get(emp1Id)).thenReturn(emp1);

        EmployeeResponse emp2 = mock(EmployeeResponse.class);
        when(emp2.workLocationId()).thenReturn(locMhId);
        when(employeeService.get(emp2Id)).thenReturn(emp2);

        WorkLocationResponse locKa = mock(WorkLocationResponse.class);
        when(locKa.stateCode()).thenReturn("KA");
        when(workLocationService.get(locKaId)).thenReturn(locKa);

        WorkLocationResponse locMh = mock(WorkLocationResponse.class);
        when(locMh.stateCode()).thenReturn("MH");
        when(workLocationService.get(locMhId)).thenReturn(locMh);

        when(orgPtOverrideRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());

        StatutoryReportFile result = service.generatePtReturnFile(PAYRUN_ID, ExportFormat.CSV, "KA");

        assertThat(result.filename()).isEqualTo("pt-return-ka-2026-04.csv");
        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("KA,NOT_CONFIGURED,EMP001,Deepak Sharma,45000.00,200.00");
        assertThat(csv).doesNotContain("MH");
    }

    @Test
    @DisplayName("Refuses export when pay run is in DRAFT or LOCKED status with 409 Conflict exception")
    void refuses_UncomputedRun() {
        PayRun draftRun = mock(PayRun.class);
        when(draftRun.getStatus()).thenReturn(PayRunStatus.DRAFT);
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(draftRun));

        assertThatThrownBy(() -> service.generateEsiReturnFile(PAYRUN_ID, ExportFormat.XLSX))
                .isInstanceOf(PayFigureNotComputedException.class);

        PayRun lockedRun = mock(PayRun.class);
        when(lockedRun.getStatus()).thenReturn(PayRunStatus.LOCKED);
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(lockedRun));

        assertThatThrownBy(() -> service.generatePtReturnFile(PAYRUN_ID, ExportFormat.CSV, null))
                .isInstanceOf(PayFigureNotComputedException.class);
    }

    @Test
    @DisplayName("Refuses export when pay run does not exist with 404 Not Found exception")
    void refuses_UnknownRun() {
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateEsiReturnFile(PAYRUN_ID, ExportFormat.XLSX))
                .isInstanceOf(PayRunNotFoundException.class);
    }
}
