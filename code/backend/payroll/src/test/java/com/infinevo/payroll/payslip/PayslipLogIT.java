package com.infinevo.payroll.payslip;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Log verification test (W-36.2 §7).
 * Asserts that after issuing, paying, verifying, and opening payslips,
 * the captured log output contains no token, no signature, and no "?t=".
 */
@SpringBootTest(classes = PayrollTestApp.class)
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
public class PayslipLogIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

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
    private UUID employeeId;
    private UUID runId;
    private UUID employeePayrunId;

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
        employeeId = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);

        PayRunResponse run = payRunService.create(JULY);
        runId = run.id();
        payRunService.lock(runId);
        worker.computeNow(runId);
        payRunService.approve(runId);
        payRunService.pay(runId, LocalDate.of(2026, 7, 31));

        employeePayrunId = PayRunTestSchema.getEmployeePayRunId(TENANT_A, runId, employeeId);

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
    @DisplayName("Captured log output contains no token and no signature")
    void logOutputContainsNoTokenOrSignature(CapturedOutput output) throws Exception {
        TenantContext.set(TENANT_A);
        PayslipLinkService.SignedLink link = linkService.signedLink(employeePayrunId, Duration.ofDays(7));
        String token = link.url().substring(link.url().indexOf("?t=") + 3);
        String[] parts = token.split("\\.");
        String signature = parts[3];

        // 1. Verify token
        assertThat(linkService.verify(token)).isPresent();

        // 2. Perform open endpoint call
        TenantContext.clear();
        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", token)).andExpect(status().isOk());

        // 3. Perform failed open with tampered token
        String tampered = parts[0] + "." + parts[1] + "." + parts[2] + ".bad" + signature.substring(3);
        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", tampered)).andExpect(status().isNotFound());

        // 4. Verify log output
        String logs = output.getAll();
        assertThat(logs).isNotEmpty();

        // Assert no token, signature, or token query string appears in logs
        assertThat(logs).as("Logs must not contain the token").doesNotContain(token);
        assertThat(logs).as("Logs must not contain the signature").doesNotContain(signature);
        assertThat(logs).as("Logs must not log token param").doesNotContain("?t=" + token);

        // Assert the expected audit log line is present
        assertThat(logs).contains("Served payslip " + employeePayrunId + " in tenant " + TENANT_A + " by signed link");
    }
}
