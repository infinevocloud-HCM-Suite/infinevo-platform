package com.infinevo.payroll.statutory.ecr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.salary.EmployeeStatutoryProfile;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileRepository;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PfEcrServiceTest {

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
    private EmployeeService employeeService;

    @Mock
    private StatutorySettingsService statutorySettingsService;

    @Mock
    private EmployeePersonalService employeePersonalService;

    private PfEcrServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new PfEcrServiceImpl(
                payRunRepository,
                employeePayRunRepository,
                employeePayRunLineRepository,
                employeeStatutoryProfileRepository,
                employeeService,
                statutorySettingsService,
                employeePersonalService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("AC-1: ECR preview calculates all 11 columns, senior EPS zeroing, and challan accounts")
    void preview_happyPath_calculatesAll11ColumnsAndChallanSummary() {
        PayRun run = createRun(PAYRUN_ID, PayRunStatus.COMPUTED, PayRunType.REGULAR, YearMonth.of(2026, 10));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        EpfSettingResponse epfSetting = mock(EpfSettingResponse.class);
        when(epfSetting.registrationNumber()).thenReturn("DLCPM1234567000");
        when(epfSetting.epsSeniorAge()).thenReturn(58);
        when(statutorySettingsService.epf(TENANT_ID)).thenReturn(epfSetting);

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID emp3Id = UUID.randomUUID();

        UUID epr1Id = UUID.randomUUID();
        UUID epr2Id = UUID.randomUUID();
        UUID epr3Id = UUID.randomUUID();

        // Emp 1: Asha Rao (capped)
        EmployeePayRun epr1 = mock(EmployeePayRun.class);
        when(epr1.getId()).thenReturn(epr1Id);
        when(epr1.getEmployeeId()).thenReturn(emp1Id);
        when(epr1.getGrossEarnings()).thenReturn(new BigDecimal("65000.0000"));
        when(epr1.getEpfWage()).thenReturn(new BigDecimal("15000.0000"));
        when(epr1.getEpsWage()).thenReturn(new BigDecimal("15000.0000"));
        when(epr1.getEdliWage()).thenReturn(new BigDecimal("15000.0000"));
        when(epr1.getLopDays()).thenReturn(BigDecimal.ZERO);

        // Emp 2: Bob Smith (under cap, 2 LOP days)
        EmployeePayRun epr2 = mock(EmployeePayRun.class);
        when(epr2.getId()).thenReturn(epr2Id);
        when(epr2.getEmployeeId()).thenReturn(emp2Id);
        when(epr2.getGrossEarnings()).thenReturn(new BigDecimal("14000.0000"));
        when(epr2.getEpfWage()).thenReturn(new BigDecimal("14000.0000"));
        when(epr2.getEpsWage()).thenReturn(new BigDecimal("14000.0000"));
        when(epr2.getEdliWage()).thenReturn(new BigDecimal("14000.0000"));
        when(epr2.getLopDays()).thenReturn(new BigDecimal("2.00"));

        // Emp 3: Ramesh Patel (age 59 senior, EPS is 0)
        EmployeePayRun epr3 = mock(EmployeePayRun.class);
        when(epr3.getId()).thenReturn(epr3Id);
        when(epr3.getEmployeeId()).thenReturn(emp3Id);
        when(epr3.getGrossEarnings()).thenReturn(new BigDecimal("50000.0000"));
        when(epr3.getEpfWage()).thenReturn(new BigDecimal("15000.0000"));
        when(epr3.getEpsWage()).thenReturn(BigDecimal.ZERO);
        when(epr3.getEdliWage()).thenReturn(new BigDecimal("15000.0000"));
        when(epr3.getLopDays()).thenReturn(BigDecimal.ZERO);

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2, epr3));

        EmployeePayRunLine l1Ee = createLine(epr1Id, "EPF_EMPLOYEE", new BigDecimal("1800.00"));
        EmployeePayRunLine l1Eps = createLine(epr1Id, "EPS_EMPLOYER", new BigDecimal("1250.00"));
        EmployeePayRunLine l1Er = createLine(epr1Id, "EPF_EMPLOYER", new BigDecimal("550.00"));

        EmployeePayRunLine l2Ee = createLine(epr2Id, "EPF_EMPLOYEE", new BigDecimal("1680.00"));
        EmployeePayRunLine l2Eps = createLine(epr2Id, "EPS_EMPLOYER", new BigDecimal("1166.00"));
        EmployeePayRunLine l2Er = createLine(epr2Id, "EPF_EMPLOYER", new BigDecimal("514.00"));

        EmployeePayRunLine l3Ee = createLine(epr3Id, "EPF_EMPLOYEE", new BigDecimal("1800.00"));
        EmployeePayRunLine l3Er = createLine(epr3Id, "EPF_EMPLOYER", new BigDecimal("1800.00"));

        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(l1Ee, l1Eps, l1Er, l2Ee, l2Eps, l2Er, l3Ee, l3Er));

        EmployeeStatutoryProfile p1 = createProfile(emp1Id, "100123456789", true, true);
        EmployeeStatutoryProfile p2 = createProfile(emp2Id, "100123456790", true, true);
        EmployeeStatutoryProfile p3 = createProfile(emp3Id, "100123456791", true, true);
        when(employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(
                        TENANT_ID, List.of(emp1Id, emp2Id, emp3Id)))
                .thenReturn(List.of(p1, p2, p3));

        EmployeeResponse emp1 = createEmployee(emp1Id, "EMP-001", "Asha", "Rao");
        EmployeeResponse emp2 = createEmployee(emp2Id, "EMP-002", "Bob", "Smith");
        EmployeeResponse emp3 = createEmployee(emp3Id, "EMP-003", "Ramesh", "Patel");
        when(employeePersonalService.dateOfBirth(emp1Id)).thenReturn(Optional.of(LocalDate.of(1990, 5, 15)));
        when(employeePersonalService.dateOfBirth(emp2Id)).thenReturn(Optional.of(LocalDate.of(1985, 8, 20)));
        when(employeePersonalService.dateOfBirth(emp3Id)).thenReturn(Optional.of(LocalDate.of(1967, 2, 10))); // Age 59
        when(employeeService.findByIdInIncludingDeleted(anyList()))
                .thenReturn(Map.of(emp1Id, emp1, emp2Id, emp2, emp3Id, emp3));

        PfEcrPreviewResponse preview = service.getEcrPreview(PAYRUN_ID);

        assertThat(preview).isNotNull();
        assertThat(preview.establishmentCode()).isEqualTo("DLCPM1234567000");
        assertThat(preview.totalMembers()).isEqualTo(3);
        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.rows()).hasSize(3);

        // Verify sorted rows: Asha Rao, Bob Smith, Ramesh Patel
        PfEcrRowResponse r1 = preview.rows().get(0);
        assertThat(r1.memberName()).isEqualTo("ASHA RAO");
        assertThat(r1.uan()).isEqualTo("100123456789");
        assertThat(r1.grossWages()).isEqualTo(65000L);
        assertThat(r1.epfWages()).isEqualTo(15000L);
        assertThat(r1.epsWages()).isEqualTo(15000L);
        assertThat(r1.edliWages()).isEqualTo(15000L);
        assertThat(r1.eeShare()).isEqualTo(1800L);
        assertThat(r1.epsShare()).isEqualTo(1250L);
        assertThat(r1.erShare()).isEqualTo(550L);
        assertThat(r1.ncpDays()).isEqualTo(0);
        assertThat(r1.toEcrLine())
                .isEqualTo("100123456789#~#ASHA RAO#~#65000#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0");

        PfEcrRowResponse r2 = preview.rows().get(1);
        assertThat(r2.memberName()).isEqualTo("BOB SMITH");
        assertThat(r2.ncpDays()).isEqualTo(2);
        assertThat(r2.toEcrLine())
                .isEqualTo("100123456790#~#BOB SMITH#~#14000#~#14000#~#14000#~#14000#~#1680#~#1166#~#514#~#2#~#0");

        PfEcrRowResponse r3 = preview.rows().get(2);
        assertThat(r3.memberName()).isEqualTo("RAMESH PATEL");
        assertThat(r3.epsWages()).isEqualTo(0L);
        assertThat(r3.epsShare()).isEqualTo(0L);
        assertThat(r3.erShare()).isEqualTo(1800L); // Col 9 = Col 7 because Col 8 is 0
        assertThat(r3.toEcrLine())
                .isEqualTo("100123456791#~#RAMESH PATEL#~#50000#~#15000#~#0#~#15000#~#1800#~#0#~#1800#~#0#~#0");

        // Verify totals
        assertThat(preview.totalGrossWages()).isEqualTo(129000L);
        assertThat(preview.totalEpfWages()).isEqualTo(44000L);
        assertThat(preview.totalEpsWages()).isEqualTo(29000L);
        assertThat(preview.totalEdliWages()).isEqualTo(44000L);
        assertThat(preview.totalEeShare()).isEqualTo(5280L);
        assertThat(preview.totalEpsShare()).isEqualTo(2416L);
        assertThat(preview.totalErShare()).isEqualTo(2864L);
    }

    @Test
    @DisplayName("AC-2: export TXT returns lines delimited with #~# and CRLF")
    void export_txt_generatesExactDelimiterAndCrlf() {
        PayRun run = createRun(PAYRUN_ID, PayRunStatus.COMPUTED, PayRunType.REGULAR, YearMonth.of(2026, 10));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID empId = UUID.randomUUID();
        UUID eprId = UUID.randomUUID();
        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(empId);
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal("30000.00"));
        when(epr.getEpfWage()).thenReturn(new BigDecimal("15000.00"));
        when(epr.getEpsWage()).thenReturn(new BigDecimal("15000.00"));
        when(epr.getEdliWage()).thenReturn(new BigDecimal("15000.00"));

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr));

        EmployeePayRunLine lEe = createLine(eprId, "EPF_EMPLOYEE", new BigDecimal("1800.00"));
        EmployeePayRunLine lEps = createLine(eprId, "EPS_EMPLOYER", new BigDecimal("1250.00"));
        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(lEe, lEps));

        EmployeeStatutoryProfile profile = createProfile(empId, "123456789012", true, true);
        when(employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(TENANT_ID, List.of(empId)))
                .thenReturn(List.of(profile));

        EmployeeResponse emp = createEmployee(empId, "EMP-100", "Kiran", "Kumar");
        when(employeeService.findByIdInIncludingDeleted(anyList())).thenReturn(Map.of(empId, emp));

        PfEcrFile file = service.exportEcrFile(PAYRUN_ID, PfEcrFormat.TXT);

        assertThat(file.filename()).startsWith("ECR_").endsWith("_2026_10.txt");
        String content = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(content)
                .isEqualTo(
                        "123456789012#~#KIRAN KUMAR#~#30000#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0\r\n");
    }

    @Test
    @DisplayName("AC-4: export XLSX generates numeric cells for amounts and string for UAN")
    void export_xlsx_generatesNumericCells() throws IOException {
        PayRun run = createRun(PAYRUN_ID, PayRunStatus.PAID, PayRunType.REGULAR, YearMonth.of(2026, 10));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID empId = UUID.randomUUID();
        UUID eprId = UUID.randomUUID();
        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(empId);
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal("20000.00"));
        when(epr.getEpfWage()).thenReturn(new BigDecimal("15000.00"));
        when(epr.getEpsWage()).thenReturn(new BigDecimal("15000.00"));
        when(epr.getEdliWage()).thenReturn(new BigDecimal("15000.00"));

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr));

        EmployeePayRunLine lEe = createLine(eprId, "EPF_EMPLOYEE", new BigDecimal("1800.00"));
        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(lEe));

        EmployeeStatutoryProfile profile = createProfile(empId, "999888777666", true, true);
        when(employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(TENANT_ID, List.of(empId)))
                .thenReturn(List.of(profile));

        EmployeeResponse emp = createEmployee(empId, "EMP-200", "Sunita", "Devi");
        when(employeeService.findByIdInIncludingDeleted(anyList())).thenReturn(Map.of(empId, emp));

        PfEcrFile file = service.exportEcrFile(PAYRUN_ID, PfEcrFormat.XLSX);

        assertThat(file.filename()).endsWith(".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            XSSFSheet sheet = workbook.getSheet("PF ECR Return");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("UAN");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Member Name");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("999888777666");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("SUNITA DEVI");

            // Numeric cells
            assertThat(dataRow.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(dataRow.getCell(2).getNumericCellValue()).isEqualTo(20000.0);
            assertThat(dataRow.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(dataRow.getCell(3).getNumericCellValue()).isEqualTo(15000.0);
        }
    }

    @Test
    @DisplayName("AC-5: flags warning when employee has missing or invalid UAN")
    void preview_flagsMissingUanWarning() {
        PayRun run = createRun(PAYRUN_ID, PayRunStatus.COMPUTED, PayRunType.REGULAR, YearMonth.of(2026, 10));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID empId = UUID.randomUUID();
        UUID eprId = UUID.randomUUID();
        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(empId);
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal("25000.00"));
        when(epr.getEpfWage()).thenReturn(new BigDecimal("15000.00"));

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr));

        EmployeePayRunLine lEe = createLine(eprId, "EPF_EMPLOYEE", new BigDecimal("1800.00"));
        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(lEe));

        // Profile with null UAN
        EmployeeStatutoryProfile profile = createProfile(empId, null, true, true);
        when(employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(TENANT_ID, List.of(empId)))
                .thenReturn(List.of(profile));

        EmployeeResponse emp = createEmployee(empId, "EMP-300", "Vikram", "Singh");
        when(employeeService.findByIdInIncludingDeleted(anyList())).thenReturn(Map.of(empId, emp));

        PfEcrPreviewResponse preview = service.getEcrPreview(PAYRUN_ID);

        assertThat(preview.warnings()).hasSize(1);
        assertThat(preview.warnings().get(0).warningCode()).isEqualTo("MISSING_UAN");
        assertThat(preview.warnings().get(0).employeeNumber()).isEqualTo("EMP-300");
    }

    @Test
    @DisplayName("AC-6: off-cycle pay runs throw IllegalArgumentException (400)")
    void export_offCycleRun_throwsIllegalArgumentException() {
        PayRun run = mock(PayRun.class);
        when(run.getStatus()).thenReturn(PayRunStatus.COMPUTED);
        when(run.getRunType()).thenReturn(PayRunType.OFF_CYCLE);
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.exportEcrFile(PAYRUN_ID, PfEcrFormat.TXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Statutory ECR returns apply only to regular pay runs");
    }

    @Test
    @DisplayName("Pay run in DRAFT status throws PayFigureNotComputedException (409)")
    void export_draftRun_throwsPayFigureNotComputedException() {
        PayRun run = mock(PayRun.class);
        when(run.getStatus()).thenReturn(PayRunStatus.DRAFT);
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.getEcrPreview(PAYRUN_ID)).isInstanceOf(PayFigureNotComputedException.class);
    }

    private static PayRun createRun(UUID id, PayRunStatus status, PayRunType runType, YearMonth period) {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getStatus()).thenReturn(status);
        when(run.getRunType()).thenReturn(runType);
        when(run.getPeriod()).thenReturn(period);
        when(run.getPeriodStart()).thenReturn(period.atDay(1));
        return run;
    }

    private static EmployeePayRunLine createLine(UUID employeePayrunId, String componentCode, BigDecimal amount) {
        EmployeePayRunLine line = mock(EmployeePayRunLine.class);
        when(line.getEmployeePayrunId()).thenReturn(employeePayrunId);
        when(line.getComponentCode()).thenReturn(componentCode);
        when(line.getAmount()).thenReturn(amount);
        return line;
    }

    private static EmployeeStatutoryProfile createProfile(
            UUID empId, String uan, boolean eligiblePf, boolean eligibleEps) {
        EmployeeStatutoryProfile p = mock(EmployeeStatutoryProfile.class);
        when(p.getEmployeeId()).thenReturn(empId);
        when(p.getUan()).thenReturn(uan);
        when(p.isEligibleForPf()).thenReturn(eligiblePf);
        when(p.isEligibleForEps()).thenReturn(eligibleEps);
        return p;
    }

    private static EmployeeResponse createEmployee(UUID empId, String empNo, String first, String last) {
        EmployeeResponse emp = mock(EmployeeResponse.class);
        when(emp.id()).thenReturn(empId);
        when(emp.employeeNumber()).thenReturn(empNo);
        when(emp.firstName()).thenReturn(first);
        when(emp.lastName()).thenReturn(last);
        return emp;
    }
}
