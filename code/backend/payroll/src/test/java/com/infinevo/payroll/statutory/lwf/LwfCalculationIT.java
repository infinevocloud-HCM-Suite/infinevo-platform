package com.infinevo.payroll.statutory.lwf;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
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
 * End-to-end integration tests for Labour Welfare Fund (LWF) calculation and return export (W-92).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class LwfCalculationIT extends AbstractIntegrationTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private LwfService lwfService;

    @Autowired
    private EmployeePayRunLineRepository lineRepository;

    @Autowired
    private PermissionService permissionService;

    private MockMvc mvc;
    private UUID juneRunId;
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

        // Employee X-01 with gross 20,000 (Basic 15,000, HRA 5,000) in MH work location
        employeeId = PayRunTestSchema.insertEmployee(TENANT_A, "X-01", LocalDate.of(2023, 4, 1), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, employeeId);
        UUID ctc1 = PayRunTestSchema.insertSalary(TENANT_A, employeeId, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc1, catalogue.basic(), "15000.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", TENANT_A, ctc1, catalogue.hra(), "5000.0000", null);

        // Set is_eligible_for_lwf = true
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_statutory_profile
                            (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi,
                             is_eligible_for_pt, is_eligible_for_lwf)
                        VALUES (gen_random_uuid(), ?, ?, false, false, false, false, true)
                        ON CONFLICT (tenant_id, employee_id) DO UPDATE SET is_eligible_for_lwf = true
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }

        // Configure mock permissionService
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

        LwfController controller = new LwfController(lwfService);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup((LwfController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() throws SQLException {
        PayRunTestSchema.clean();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Pay run in June computes and exposes LWF return report")
    void computeJuneRun_andExportLwfReturn() throws Exception {
        juneRunId = payRunService.create(JUNE).id();
        payRunService.lock(juneRunId);
        assertThat(worker.computeNow(juneRunId).status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(TENANT_A);

        // Verify LWF return XLSX download
        MvcResult result = mvc.perform(get("/api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX", juneRunId))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"lwf-return-" + juneRunId + ".xlsx\""))
                .andReturn();

        byte[] xlsxBytes = result.getResponse().getContentAsByteArray();
        assertThat(xlsxBytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            XSSFSheet sheet = workbook.getSheet("LWF Return Statement");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Employee Number");
        }
    }

    @Test
    @DisplayName("Guarded by payroll.lwf.read permission")
    void exportLwfReturn_withoutPermission_returns403() throws Exception {
        juneRunId = payRunService.create(JUNE).id();
        payRunService.lock(juneRunId);
        worker.computeNow(juneRunId);
        TenantContext.set(TENANT_A);

        when(permissionService.holds("payroll.lwf.read")).thenReturn(false);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX", juneRunId))
                .andExpect(status().isForbidden());
    }
}
