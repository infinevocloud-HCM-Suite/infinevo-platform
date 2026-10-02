package com.infinevo.payroll.payslip;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tenant isolation and public link tenant binding test (W-36.2 §7).
 * As app_user, tenant A cannot read tenant B's slip by the officer path;
 * a tenant B token presented under tenant A's context still resolves tenant B from the token and returns
 * tenant B's slip — the anonymous path binds from the token only, as DocumentDownloadController.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
public class PayslipRlsIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayslipService payslipService;

    @Autowired
    private PayslipLinkService linkService;

    @Autowired
    private PayslipOpenController openController;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private EmployeePayRunRepository employeePayRuns;

    private MockMvc mvc;
    private UUID employeeB;
    private UUID runB;
    private UUID employeePayrunB;
    private String tokenB;

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

        // Prepare Tenant B
        TenantContext.set(TENANT_B);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogueB = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_B);
        employeeB = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_B, "B-01", catalogueB);

        PayRunResponse rB = payRunService.create(JULY);
        runB = rB.id();
        payRunService.lock(runB);
        worker.computeNow(runB);
        payRunService.approve(runB);
        payRunService.pay(runB, LocalDate.of(2026, 7, 31));

        employeePayrunB = PayRunTestSchema.getEmployeePayRunId(TENANT_B, runB, employeeB);
        PayslipLinkService.SignedLink link = linkService.signedLink(employeePayrunB, Duration.ofDays(7));
        tokenB = link.url().substring(link.url().indexOf("?t=") + 3);

        // Prepare Tenant A
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogueA = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "A-01", catalogueA);

        mvc = MockMvcBuilders.standaloneSetup(openController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A cannot read Tenant B's slip by officer path; anonymous path binds Tenant B from token")
    void crossTenantIsolationAndTokenBinding() throws Exception {
        // As Tenant A, trying to read Tenant B's payslip by officer path throws PayslipNotFoundException
        TenantContext.set(TENANT_A);
        assertThatThrownBy(() -> payslipService.forOfficer(runB, employeeB))
                .isInstanceOf(PayslipNotFoundException.class);

        // Present Tenant B token with Tenant A context bound:
        // anonymous endpoint binds from token only and returns Tenant B's slip
        TenantContext.set(TENANT_A);
        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.run.payrun_id").value(runB.toString()))
                .andExpect(jsonPath("$.data.employee.id").value(employeeB.toString()))
                .andExpect(jsonPath("$.data.employer.name").value("Globex Corporation"));

        // Verify TenantContext was restored/cleared properly
        assertThat(TenantContext.isBound()).isFalse();
    }
}
