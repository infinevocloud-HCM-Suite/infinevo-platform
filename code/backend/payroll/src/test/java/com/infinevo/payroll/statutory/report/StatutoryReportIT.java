package com.infinevo.payroll.statutory.report;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration test verifying ESI and PT return report generation endpoints (W-95).
 * Tests HTTP status, content types, Content-Disposition headers, figures in output files,
 * @RequiresAction guards, cross-tenant isolation (404), off-cycle refusal (400), and 409 on uncomputed runs.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class StatutoryReportIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final String STATUTORY_ACTION = "payroll.statutory_report.read";

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private StatutoryReportService statutoryReportService;

    @Autowired
    private PermissionService permissionService;

    private MockMvc mvc;
    private UUID computedRunId;
    private UUID firstEmployeeId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);

        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);

        // Seed work location in Gujarat
        UUID locGj = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (id, tenant_id, code, name, state_code, is_active) VALUES (?, ?, 'LOC-GJ', 'Ahmedabad', 'GJ', true)")) {
            ps.setObject(1, locGj);
            ps.setObject(2, TENANT_A);
            ps.executeUpdate();
        }

        // Seed statutory ESI settings: ceiling 21000, 0.75% EE, 3.25% ER
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.esi_setting (id, tenant_id, is_enabled, wage_ceiling, employee_rate, employer_rate)
                        VALUES (gen_random_uuid(), ?, true, 21000.0000, 0.7500, 3.2500)
                        ON CONFLICT (tenant_id) DO UPDATE SET is_enabled = true, wage_ceiling = 21000.0000, employee_rate = 0.7500, employer_rate = 3.2500
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.executeUpdate();
        }

        // Seed PT override for Gujarat
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.org_pt_override (id, tenant_id, state_code, registration_number, effective_from, created_by)
                        VALUES (gen_random_uuid(), ?, 'GJ', 'PTRC-GJ-12345', DATE '2025-01-01', 'system')
                        ON CONFLICT (tenant_id, state_code) DO UPDATE SET registration_number = 'PTRC-GJ-12345'
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.executeUpdate();
        }

        // Employee X-01 with gross 16000 (Basic 12000, HRA 4000) so ESI and PT both apply (Gujarat charges 200 above
        // 12,000)
        firstEmployeeId = PayRunTestSchema.insertEmployee(TENANT_A, "X-01", LocalDate.of(2023, 4, 1), "ACTIVE", null);
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE core.employee SET work_location_id = ? WHERE id = ?")) {
            ps.setObject(1, locGj);
            ps.setObject(2, firstEmployeeId);
            ps.executeUpdate();
        }
        PayRunTestSchema.insertBank(TENANT_A, firstEmployeeId);
        UUID ctc1 = PayRunTestSchema.insertSalary(TENANT_A, firstEmployeeId, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc1, catalogue.basic(), "12000.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc1, catalogue.hra(), "4000.0000", null);

        // Statutory profile for X-01: eligible for PF, ESI, PT with IP Number
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_statutory_profile
                            (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi,
                             is_eligible_for_pt, contributes_eps_on_higher_wages, esi_number)
                        VALUES (gen_random_uuid(), ?, ?, true, true, true, true, false, '1234567890')
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, firstEmployeeId);
            ps.executeUpdate();
        }

        // Employee X-02 with higher salary (above ESI ceiling)
        PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "X-02", catalogue);

        computedRunId = payRunService.create(JULY).id();
        payRunService.lock(computedRunId);
        assertThat(worker.computeNow(computedRunId).status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(TENANT_A);

        when(permissionService.holds(anyString())).thenReturn(true);
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        StatutoryReportController controller = new StatutoryReportController(statutoryReportService);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));

        mvc = MockMvcBuilders.standaloneSetup((StatutoryReportController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @AfterEach
    void tearDown() {
        when(permissionService.holds(anyString())).thenReturn(true);
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /reports/esi with format=XLSX returns 200 with Excel content type, headers and correct figures")
    void getEsiReturn_Xlsx() throws Exception {
        MvcResult mvcResult = mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/esi?format=XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_TYPE,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("esi-return-2026-07.xlsx")))
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(mvcResult.getResponse().getContentAsByteArray()))) {
            XSSFSheet sheet = wb.getSheet("ESI Return");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(2);

            // Row 0: Headers
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("IP Number");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Total Monthly Wages");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("IP Contribution");

            // Row 1: X-01
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("1234567890");
            assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(31.0);
            assertThat(sheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(16000.00);
            assertThat(sheet.getRow(1).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(120.00);
            assertThat(sheet.getRow(1).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(520.00);
            assertThat(sheet.getRow(1).getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(640.00);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEmpty();
        }
    }

    @Test
    @DisplayName("GET /reports/esi with format=CSV returns 200 with text/csv content type, BOM, and figures")
    void getEsiReturn_Csv() throws Exception {
        MvcResult mvcResult = mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/esi?format=CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("esi-return-2026-07.csv")))
                .andReturn();

        String csv = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
        assertThat(csv).contains("IP Number,IP Name,No. of Days Worked,Total Monthly Wages");
        assertThat(csv).contains("1234567890,First Last,31,16000.00,120.00,520.00,640.00,");
    }

    @Test
    @DisplayName("GET /reports/pt with format=XLSX returns 200 with Excel content type, sheets and figures")
    void getPtReturn_Xlsx() throws Exception {
        MvcResult mvcResult = mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/pt?format=XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_TYPE,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("pt-return-2026-07.xlsx")))
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(mvcResult.getResponse().getContentAsByteArray()))) {
            XSSFSheet ptSheet = wb.getSheet("PT Return");
            assertThat(ptSheet).isNotNull();
            assertThat(ptSheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(2);
            assertThat(ptSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("GJ");
            assertThat(ptSheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("PTRC-GJ-12345");
            assertThat(ptSheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("X-01");
            assertThat(ptSheet.getRow(1).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(ptSheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(16000.00);
            assertThat(ptSheet.getRow(1).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(ptSheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(200.00);

            XSSFSheet summarySheet = wb.getSheet("State Summary");
            assertThat(summarySheet).isNotNull();
            assertThat(summarySheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("GJ");
            assertThat(summarySheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("PTRC-GJ-12345");
            assertThat(summarySheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(summarySheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(1.0);
            assertThat(summarySheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(summarySheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(200.00);
        }
    }

    @Test
    @DisplayName("GET /reports/pt with format=CSV and state=GJ returns 200 with state figures in CSV")
    void getPtReturn_Csv_WithState() throws Exception {
        MvcResult mvcResult = mvc.perform(
                        get("/api/v1/payroll/payruns/" + computedRunId + "/reports/pt?format=CSV&state=GJ"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("pt-return-gj-2026-07.csv")))
                .andReturn();

        String csv = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
        assertThat(csv).contains("State,PTRC Number,Employee Code,Employee Name,Gross Salary,PT Deducted");
        assertThat(csv).contains("GJ,PTRC-GJ-12345,X-01,First Last,16000.00,200.00");
        assertThat(csv).contains("State Code,PTRC Number,Total Employees Liable,Total PT Deducted");
        assertThat(csv).contains("GJ,PTRC-GJ-12345,1,200.00");
    }

    @Test
    @DisplayName("Caller lacking payroll.statutory_report.read action gets 403 Forbidden")
    void requires_StatutoryReportRead() throws Exception {
        when(permissionService.holds(STATUTORY_ACTION)).thenReturn(false);

        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/esi?format=XLSX"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/pt?format=XLSX"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Refuses pre-computation runs (DRAFT, LOCKED) with 409 Conflict")
    void refuses_UncomputedRuns() throws Exception {
        UUID draftId = payRunService.create(YearMonth.of(2026, 8)).id();

        mvc.perform(get("/api/v1/payroll/payruns/" + draftId + "/reports/esi?format=XLSX"))
                .andExpect(status().isConflict());

        payRunService.lock(draftId);

        mvc.perform(get("/api/v1/payroll/payruns/" + draftId + "/reports/pt?format=CSV"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Refuses statutory reports for OFF_CYCLE pay runs with 400 Bad Request")
    void refuses_OffCycleRun() throws Exception {
        PayRunResponse offCycle = payRunService.createOffCycle(JULY.atDay(15), List.of(firstEmployeeId), "Bonus");

        mvc.perform(get("/api/v1/payroll/payruns/" + offCycle.id() + "/reports/esi?format=XLSX"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        mvc.perform(get("/api/v1/payroll/payruns/" + offCycle.id() + "/reports/pt?format=CSV"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @DisplayName("Cross-tenant request for another tenant's pay run returns 404 Not Found")
    void crossTenantRun_Returns404() throws Exception {
        TenantContext.set(TENANT_B);

        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/esi?format=XLSX"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/pt?format=CSV"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-existent pay run returns 404 Not Found")
    void unknownRun_Returns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        mvc.perform(get("/api/v1/payroll/payruns/" + randomId + "/reports/esi?format=XLSX"))
                .andExpect(status().isNotFound());
    }
}
