package com.infinevo.payroll.statutory.lwf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
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
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
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
class LwfServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PAYRUN_ID = UUID.randomUUID();

    @Mock
    private OrgLwfSettingRepository orgLwfSettingRepository;

    @Mock
    private LwfStateRepository lwfStateRepository;

    @Mock
    private LwfRuleRepository lwfRuleRepository;

    @Mock
    private WorkLocationService workLocationService;

    @Mock
    private PayRunRepository payRunRepository;

    @Mock
    private EmployeePayRunRepository employeePayRunRepository;

    @Mock
    private EmployeePayRunLineRepository employeePayRunLineRepository;

    @Mock
    private EmployeeService employeeService;

    private LwfServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new LwfServiceImpl(
                orgLwfSettingRepository,
                lwfStateRepository,
                lwfRuleRepository,
                workLocationService,
                payRunRepository,
                employeePayRunRepository,
                employeePayRunLineRepository,
                employeeService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("AC-1: Haryana percentage-with-cap rule enforces 0.2% / 0.4% with ₹25 / ₹50 caps")
    void calculate_haryanaPercentageWithCap_capsAt25And50() {
        LwfRule hrRule = new LwfRule(
                UUID.randomUUID(),
                "HR",
                LocalDate.of(2020, 1, 1),
                null,
                new Integer[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12},
                LwfRule.MODE_PERCENTAGE_WITH_CAP,
                BigDecimal.ZERO,
                null,
                null,
                null,
                new BigDecimal("0.0020"),
                new BigDecimal("0.0040"),
                new BigDecimal("25.0000"),
                new BigDecimal("50.0000"));

        when(lwfStateRepository.findById("HR")).thenReturn(Optional.of(new LwfState("HR", "Haryana", true)));
        when(lwfRuleRepository.findInForceRules("HR", LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of(hrRule));

        // 1. Gross ₹20,000: 0.2% = 40 (capped at 25), 0.4% = 80 (capped at 50)
        LwfCalculationResult resultCapped =
                service.calculate(TENANT_ID, "HR", new BigDecimal("20000.0000"), 5, LocalDate.of(2026, 5, 31));
        assertThat(resultCapped.hasContribution()).isTrue();
        assertThat(resultCapped.employeeAmount()).isEqualByComparingTo("25.00");
        assertThat(resultCapped.employerAmount()).isEqualByComparingTo("50.00");

        // 2. Gross ₹5,000: 0.2% = 10 (< 25), 0.4% = 20 (< 50)
        LwfCalculationResult resultBelowCap =
                service.calculate(TENANT_ID, "HR", new BigDecimal("5000.0000"), 5, LocalDate.of(2026, 5, 31));
        assertThat(resultBelowCap.hasContribution()).isTrue();
        assertThat(resultBelowCap.employeeAmount()).isEqualByComparingTo("10.00");
        assertThat(resultBelowCap.employerAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("AC-2 & AC-3: Maharashtra half-yearly rule deducts in June and December, but zero in July")
    void calculate_maharashtra_deductsOnlyInJuneAndDecember() {
        LwfRule mhRule = new LwfRule(
                UUID.randomUUID(),
                "MH",
                LocalDate.of(2024, 1, 1),
                null,
                new Integer[] {6, 12},
                LwfRule.MODE_FLAT,
                BigDecimal.ZERO,
                null,
                new BigDecimal("25.0000"),
                new BigDecimal("75.0000"),
                null,
                null,
                null,
                null);

        when(lwfStateRepository.findById("MH")).thenReturn(Optional.of(new LwfState("MH", "Maharashtra", true)));
        when(lwfRuleRepository.findInForceRules("MH", LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of(mhRule));
        when(lwfRuleRepository.findInForceRules("MH", LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(mhRule));

        // June (month 6) -> Deducts standard ₹25 / ₹75
        LwfCalculationResult junResult =
                service.calculate(TENANT_ID, "MH", new BigDecimal("35000.0000"), 6, LocalDate.of(2026, 6, 30));
        assertThat(junResult.hasContribution()).isTrue();
        assertThat(junResult.employeeAmount()).isEqualByComparingTo("25.00");
        assertThat(junResult.employerAmount()).isEqualByComparingTo("75.00");

        // July (month 7) -> Non-deduction month, evaluates to 0
        LwfCalculationResult julResult =
                service.calculate(TENANT_ID, "MH", new BigDecimal("35000.0000"), 7, LocalDate.of(2026, 7, 31));
        assertThat(julResult.hasContribution()).isFalse();
        assertThat(julResult.employeeAmount()).isEqualByComparingTo("0.00");
        assertThat(julResult.employerAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("AC-4: Karnataka annual rule deducts in December, but zero in all other months")
    void calculate_karnataka_deductsOnlyInDecember() {
        LwfRule kaRule = new LwfRule(
                UUID.randomUUID(),
                "KA",
                LocalDate.of(2024, 1, 1),
                null,
                new Integer[] {12},
                LwfRule.MODE_FLAT,
                BigDecimal.ZERO,
                null,
                new BigDecimal("50.0000"),
                new BigDecimal("100.0000"),
                null,
                null,
                null,
                null);

        when(lwfStateRepository.findById("KA")).thenReturn(Optional.of(new LwfState("KA", "Karnataka", true)));
        when(lwfRuleRepository.findInForceRules("KA", LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(kaRule));
        when(lwfRuleRepository.findInForceRules("KA", LocalDate.of(2026, 4, 30)))
                .thenReturn(List.of(kaRule));

        // December (month 12) -> Deducts annual ₹50 / ₹100
        LwfCalculationResult decResult =
                service.calculate(TENANT_ID, "KA", new BigDecimal("50000.0000"), 12, LocalDate.of(2026, 12, 31));
        assertThat(decResult.hasContribution()).isTrue();
        assertThat(decResult.employeeAmount()).isEqualByComparingTo("50.00");
        assertThat(decResult.employerAmount()).isEqualByComparingTo("100.00");

        // April (month 4) -> 0
        LwfCalculationResult aprResult =
                service.calculate(TENANT_ID, "KA", new BigDecimal("50000.0000"), 4, LocalDate.of(2026, 4, 30));
        assertThat(aprResult.hasContribution()).isFalse();
    }

    @Test
    @DisplayName("AC-5: State without LWF (e.g. Uttar Pradesh) returns 0 contribution")
    void calculate_stateWithoutLwf_returnsZero() {
        when(lwfStateRepository.findById("UP")).thenReturn(Optional.of(new LwfState("UP", "Uttar Pradesh", false)));

        LwfCalculationResult result =
                service.calculate(TENANT_ID, "UP", new BigDecimal("40000.0000"), 6, LocalDate.of(2026, 6, 30));
        assertThat(result.hasContribution()).isFalse();
        assertThat(result.employeeAmount()).isEqualByComparingTo("0.00");
        assertThat(result.employerAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("AC-6: Tenant disabled state returns 0 contribution even in qualifying deduction month")
    void calculate_tenantDisabledState_returnsZero() {
        OrgLwfSetting disabledSetting = new OrgLwfSetting(TENANT_ID, "MH", "MH-LWF-123", false, "admin");
        when(orgLwfSettingRepository.findByTenantIdAndStateCode(TENANT_ID, "MH"))
                .thenReturn(Optional.of(disabledSetting));

        LwfCalculationResult result =
                service.calculate(TENANT_ID, "MH", new BigDecimal("30000.0000"), 6, LocalDate.of(2026, 6, 30));
        assertThat(result.hasContribution()).isFalse();
        assertThat(result.employeeAmount()).isEqualByComparingTo("0.00");
        assertThat(result.employerAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("listSettings: aggregates active work locations and maps statutory rules and tenant overrides")
    void listSettings_aggregatesOperatingStates() {
        WorkLocationResponse locMh1 = mockWorkLocation(UUID.randomUUID(), "LOC-1", "Mumbai HQ", "MH");
        WorkLocationResponse locMh2 = mockWorkLocation(UUID.randomUUID(), "LOC-2", "Pune Branch", "MH");
        WorkLocationResponse locKa = mockWorkLocation(UUID.randomUUID(), "LOC-3", "Bangalore Tech", "KA");

        when(workLocationService.list(true)).thenReturn(List.of(locMh1, locMh2, locKa));

        when(lwfStateRepository.findById("MH")).thenReturn(Optional.of(new LwfState("MH", "Maharashtra", true)));
        when(lwfStateRepository.findById("KA")).thenReturn(Optional.of(new LwfState("KA", "Karnataka", true)));

        LwfRule mhRule = new LwfRule(
                UUID.randomUUID(),
                "MH",
                LocalDate.of(2024, 1, 1),
                null,
                new Integer[] {6, 12},
                LwfRule.MODE_FLAT,
                BigDecimal.ZERO,
                null,
                new BigDecimal("25.0000"),
                new BigDecimal("75.0000"),
                null,
                null,
                null,
                null);
        LwfRule kaRule = new LwfRule(
                UUID.randomUUID(),
                "KA",
                LocalDate.of(2024, 1, 1),
                null,
                new Integer[] {12},
                LwfRule.MODE_FLAT,
                BigDecimal.ZERO,
                null,
                new BigDecimal("50.0000"),
                new BigDecimal("100.0000"),
                null,
                null,
                null,
                null);

        when(lwfRuleRepository.findInForceRules(org.mockito.ArgumentMatchers.eq("MH"), any()))
                .thenReturn(List.of(mhRule));
        when(lwfRuleRepository.findInForceRules(org.mockito.ArgumentMatchers.eq("KA"), any()))
                .thenReturn(List.of(kaRule));

        OrgLwfSetting mhSetting = new OrgLwfSetting(TENANT_ID, "MH", "MH-REG-101", true, "admin");
        when(orgLwfSettingRepository.findByTenantIdAndStateCode(TENANT_ID, "MH"))
                .thenReturn(Optional.of(mhSetting));

        List<LwfStateSettingResponse> list = service.listSettings(TENANT_ID);

        assertThat(list).hasSize(2);
        LwfStateSettingResponse kaResp = list.get(0);
        assertThat(kaResp.stateCode()).isEqualTo("KA");
        assertThat(kaResp.activeLocationsCount()).isEqualTo(1);
        assertThat(kaResp.deductionCycle()).isEqualTo("ANNUAL_DECEMBER");
        assertThat(kaResp.isEnabled()).isTrue();

        LwfStateSettingResponse mhResp = list.get(1);
        assertThat(mhResp.stateCode()).isEqualTo("MH");
        assertThat(mhResp.activeLocationsCount()).isEqualTo(2);
        assertThat(mhResp.deductionCycle()).isEqualTo("HALF_YEARLY_JUNE_DECEMBER");
        assertThat(mhResp.registrationNumber()).isEqualTo("MH-REG-101");
    }

    @Test
    @DisplayName("exportReturnFile: generates XLSX with numeric amounts and employee details")
    void exportReturnFile_xlsx_generatesNumericCells() throws IOException {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(PAYRUN_ID);
        when(run.getStatus()).thenReturn(PayRunStatus.COMPUTED);
        when(run.getRunType()).thenReturn(PayRunType.REGULAR);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 6));
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        UUID empId = UUID.randomUUID();
        UUID eprId = UUID.randomUUID();
        UUID locId = UUID.randomUUID();

        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(empId);
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal("30000.0000"));

        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, PAYRUN_ID, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr));

        EmployeePayRunLine lEe = mock(EmployeePayRunLine.class);
        when(lEe.getEmployeePayrunId()).thenReturn(eprId);
        when(lEe.getComponentCode()).thenReturn("LWF_EMPLOYEE");
        when(lEe.getAmount()).thenReturn(new BigDecimal("25.0000"));

        EmployeePayRunLine lEr = mock(EmployeePayRunLine.class);
        when(lEr.getEmployeePayrunId()).thenReturn(eprId);
        when(lEr.getComponentCode()).thenReturn("LWF_EMPLOYER");
        when(lEr.getAmount()).thenReturn(new BigDecimal("75.0000"));

        when(employeePayRunLineRepository.findByTenantIdAndPayrunId(TENANT_ID, PAYRUN_ID))
                .thenReturn(List.of(lEe, lEr));

        EmployeeResponse emp = mock(EmployeeResponse.class);
        when(emp.id()).thenReturn(empId);
        when(emp.employeeNumber()).thenReturn("EMP-500");
        when(emp.firstName()).thenReturn("Anand");
        when(emp.lastName()).thenReturn("Verma");
        when(emp.workLocationId()).thenReturn(locId);

        when(employeeService.findByIdInIncludingDeleted(List.of(empId))).thenReturn(Map.of(empId, emp));

        WorkLocationResponse loc = mockWorkLocation(locId, "LOC-MH", "Mumbai", "MH");
        when(workLocationService.list(true)).thenReturn(List.of(loc));

        byte[] xlsxBytes = service.exportReturnFile(TENANT_ID, PAYRUN_ID, "XLSX");
        assertThat(xlsxBytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            XSSFSheet sheet = workbook.getSheet("LWF Return Statement");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Employee Number");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Employee Contribution");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Employer Contribution");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("EMP-500");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Anand Verma");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("MH");
            assertThat(dataRow.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(dataRow.getCell(3).getNumericCellValue()).isEqualTo(30000.0);
            assertThat(dataRow.getCell(4).getNumericCellValue()).isEqualTo(25.0);
            assertThat(dataRow.getCell(5).getNumericCellValue()).isEqualTo(75.0);
            assertThat(dataRow.getCell(6).getNumericCellValue()).isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("exportReturnFile: off-cycle pay runs throw IllegalArgumentException (400)")
    void exportReturnFile_offCycleRun_throwsIllegalArgumentException() {
        PayRun run = mock(PayRun.class);
        when(run.getStatus()).thenReturn(PayRunStatus.COMPUTED);
        when(run.getRunType()).thenReturn(PayRunType.OFF_CYCLE);
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.exportReturnFile(TENANT_ID, PAYRUN_ID, "XLSX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LWF return statements apply only to regular pay runs");
    }

    @Test
    @DisplayName("exportReturnFile: pay run in DRAFT status throws PayFigureNotComputedException (409)")
    void exportReturnFile_draftRun_throwsPayFigureNotComputedException() {
        PayRun run = mock(PayRun.class);
        when(run.getStatus()).thenReturn(PayRunStatus.DRAFT);
        when(run.getRunType()).thenReturn(PayRunType.REGULAR);
        when(payRunRepository.findByIdAndTenantId(PAYRUN_ID, TENANT_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.exportReturnFile(TENANT_ID, PAYRUN_ID, "XLSX"))
                .isInstanceOf(PayFigureNotComputedException.class);
    }

    private WorkLocationResponse mockWorkLocation(UUID id, String code, String name, String stateCode) {
        return new WorkLocationResponse(
                id,
                TENANT_ID,
                code,
                name,
                "Address line 1",
                null,
                "City",
                "State",
                stateCode,
                "400001",
                "IN",
                true,
                true,
                Instant.now(),
                Instant.now());
    }
}
