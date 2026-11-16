package com.infinevo.payroll.statutory.report;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration test verifying ESI and PT return report generation endpoints (W-95).
 * Tests HTTP status, content types, Content-Disposition headers, @RequiresAction guards, and 409 on uncomputed runs.
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
        PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "X-01", catalogue);
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
    @DisplayName("GET /reports/esi with format=XLSX returns 200 with Excel content type and Content-Disposition")
    void getEsiReturn_Xlsx() throws Exception {
        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/esi?format=XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_TYPE,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("esi-return-2026-07.xlsx")));
    }

    @Test
    @DisplayName("GET /reports/esi with format=CSV returns 200 with text/csv content type and Content-Disposition")
    void getEsiReturn_Csv() throws Exception {
        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/esi?format=CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("esi-return-2026-07.csv")));
    }

    @Test
    @DisplayName("GET /reports/pt with format=XLSX returns 200 with Excel content type and Content-Disposition")
    void getPtReturn_Xlsx() throws Exception {
        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/pt?format=XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_TYPE,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("pt-return-2026-07.xlsx")));
    }

    @Test
    @DisplayName("GET /reports/pt with format=CSV and state=KA returns 200 with state in filename")
    void getPtReturn_Csv_WithState() throws Exception {
        mvc.perform(get("/api/v1/payroll/payruns/" + computedRunId + "/reports/pt?format=CSV&state=KA"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(
                        header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("pt-return-ka-2026-07.csv")));
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
    @DisplayName("Non-existent pay run returns 404 Not Found")
    void unknownRun_Returns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        mvc.perform(get("/api/v1/payroll/payruns/" + randomId + "/reports/esi?format=XLSX"))
                .andExpect(status().isNotFound());
    }
}
