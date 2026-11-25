package com.infinevo.payroll.statutory.ecr;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
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
 * End-to-end integration tests for PF ECR file preview and export (W-87).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PfEcrIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PfEcrService pfEcrService;

    @Autowired
    private PermissionService permissionService;

    private MockMvc mvc;
    private UUID computedRunId;
    private UUID employeeId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);

        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);

        // Seed EPF settings for TENANT_A with establishment code
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.epf_setting
                            (id, tenant_id, is_enabled, registration_number, wage_ceiling,
                             employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate,
                             restrict_employee_to_ceiling, restrict_employer_to_ceiling,
                             consider_earned_wage, prorate_restricted_wage, eps_senior_age,
                             include_employer_in_structure, include_edli_in_structure, include_admin_in_structure)
                        VALUES (gen_random_uuid(), ?, true, 'DLCPM1234567000', 15000.0000,
                                12.0000, 12.0000, 8.3300, 0.5000, 0.5000,
                                true, true, true, false, 58,
                                true, true, true)
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            is_enabled = true,
                            registration_number = 'DLCPM1234567000'
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.executeUpdate();
        }

        // Employee X-01 with gross 20,000 (Basic 15,000, HRA 5,000)
        employeeId = PayRunTestSchema.insertEmployee(TENANT_A, "X-01", LocalDate.of(2023, 4, 1), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, employeeId);
        UUID ctc1 = PayRunTestSchema.insertSalary(TENANT_A, employeeId, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc1, catalogue.basic(), "15000.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc1, catalogue.hra(), "5000.0000", null);

        // Statutory profile with 12-digit UAN
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_statutory_profile
                            (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi,
                             is_eligible_for_pt, contributes_eps_on_higher_wages, uan)
                        VALUES (gen_random_uuid(), ?, ?, true, true, false, false, false, '100123456789')
                        ON CONFLICT (tenant_id, employee_id) DO UPDATE SET uan = '100123456789'
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }

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

        PfEcrController controller = new PfEcrController(pfEcrService);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));

        mvc = MockMvcBuilders.standaloneSetup((PfEcrController) factory.getProxy())
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
        TenantContext.clear();
    }

    @Test
    @DisplayName("AC-1: GET /preview returns 200 with establishment code, member count, and rows")
    void getPreview_success() throws Exception {
        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/preview", computedRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.establishmentCode").value("DLCPM1234567000"))
                .andExpect(jsonPath("$.data.totalMembers").value(1))
                .andExpect(jsonPath("$.data.rows[0].uan").value("100123456789"))
                .andExpect(jsonPath("$.data.rows[0].epfWages").value(15000))
                .andExpect(jsonPath("$.data.rows[0].eeShare").value(1800));
    }

    @Test
    @DisplayName("AC-2: GET /export?format=TXT returns 200 with #~# formatted text file")
    void exportTxt_success() throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/export?format=TXT", computedRunId))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                containsString("attachment; filename=\"ECR_DLCPM1234567000_2026_07.txt\"")))
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andReturn();

        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("100123456789#~#");
        assertThat(body).contains("#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0\r\n");
    }

    @Test
    @DisplayName("AC-2: GET /export?format=XLSX returns valid Excel workbook")
    void exportXlsx_success() throws Exception {
        MvcResult res = mvc.perform(
                        get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/export?format=XLSX", computedRunId))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                containsString("attachment; filename=\"ECR_DLCPM1234567000_2026_07.xlsx\"")))
                .andReturn();

        byte[] bytes = res.getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("PF ECR Return");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("UAN");
        }
    }

    @Test
    @DisplayName("AC-8: Authorization enforcement - 403 when permission is missing")
    void authorization_deniedReturns403() throws Exception {
        when(permissionService.holds("payroll.statutory_report.read")).thenReturn(false);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/preview", computedRunId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cross-tenant isolation: querying another tenant's pay run returns 404")
    void crossTenant_returns404() throws Exception {
        TenantContext.set(TENANT_B);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/preview", computedRunId))
                .andExpect(status().isNotFound());
    }
}
