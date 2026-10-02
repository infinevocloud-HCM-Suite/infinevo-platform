package com.infinevo.payroll.payslip;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.IllegalPayRunTransitionException;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
 * Acceptance integration test for payslip release and public access (W-36.2 §7).
 * Compute 2-employee run -> approve -> pay -> verify 2 notifications with links -> open with no bearer ->
 * verify slip contents -> cancel refused, link still opens -> a cancelled run's link is 404 -> document
 * domain token is 404.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
public class PayslipReleaseIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayslipOpenController payslipOpenController;

    @Autowired
    private PayslipLinkService payslipLinkService;

    private MockMvc mvc;
    private UUID firstEmployeeId;
    private UUID secondEmployeeId;

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
        firstEmployeeId = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);
        secondEmployeeId = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-02", catalogue);

        mvc = MockMvcBuilders.standaloneSetup(payslipOpenController)
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
    @DisplayName(
            "Acceptance test: compute -> approve -> pay -> 2 notifications -> open without bearer -> paid stays paid")
    void releaseAndOpenPayslipAcceptanceFlow() throws Exception {
        // 1. Create, lock and compute a two-employee pay run
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);

        // 2. Approve
        PayRunResponse approved = payRunService.approve(run.id());
        assertThat(approved.status()).isEqualTo(PayRunStatus.APPROVED);
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.approvedBy()).isEqualTo("system");

        // 3. Pay
        LocalDate paidOn = LocalDate.of(2026, 7, 31);
        PayRunResponse paid = payRunService.pay(run.id(), paidOn);
        assertThat(paid.status()).isEqualTo(PayRunStatus.PAID);
        assertThat(paid.paidOn()).isEqualTo(paidOn);
        assertThat(paid.paidAt()).isNotNull();
        assertThat(paid.paidBy()).isEqualTo("system");
        assertThat(paid.payslipsReleasedAt()).isNotNull();

        // 4. Verify two core.notification rows for PAYSLIP_READY whose data/body holds a link
        List<String> notificationBodies = new ArrayList<>();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT body FROM core.notification WHERE tenant_id = ? AND event = 'PAYSLIP_READY'")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notificationBodies.add(rs.getString("body"));
                }
            }
        }
        assertThat(notificationBodies).hasSize(2);

        Pattern linkPattern = Pattern.compile("href=\"([^\"]+)\"");
        List<String> tokens = new ArrayList<>();
        for (String body : notificationBodies) {
            Matcher m = linkPattern.matcher(body);
            assertThat(m.find()).isTrue();
            String fullUrl = m.group(1);
            assertThat(fullUrl).contains("?t=");
            String token = fullUrl.substring(fullUrl.indexOf("?t=") + 3);
            tokens.add(token);
        }

        // 5. Open payslip by signed link with no bearer token
        TenantContext.clear();
        String token = tokens.get(0);
        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.run.payrun_id").value(run.id().toString()))
                .andExpect(jsonPath("$.data.run.status").value("PAID"))
                .andExpect(jsonPath("$.data.totals.gross_earnings").isNumber())
                .andExpect(jsonPath("$.data.totals.net_pay").isNumber())
                .andExpect(jsonPath("$.data.earnings").isArray())
                .andExpect(jsonPath("$.data.link.expires_at").isNotEmpty());

        // 6. A paid run stays paid (W-36.2 §10): cancel is refused and the link still opens
        TenantContext.set(TENANT_A);
        assertThatThrownBy(() -> payRunService.cancel(run.id())).isInstanceOf(IllegalPayRunTransitionException.class);
        TenantContext.clear();

        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", token)).andExpect(status().isOk());

        // 6b. A run cancelled after approve was never paid: a well-signed link to one of its rows is 404
        TenantContext.set(TENANT_A);
        PayRunResponse august = payRunService.create(AUGUST);
        payRunService.lock(august.id());
        worker.computeNow(august.id());
        payRunService.approve(august.id());
        payRunService.cancel(august.id());
        UUID cancelledRow = PayRunTestSchema.getEmployeePayRunId(TENANT_A, august.id(), firstEmployeeId);
        String cancelledUrl =
                payslipLinkService.signedLink(cancelledRow, Duration.ofDays(1)).url();
        String cancelledToken = cancelledUrl.substring(cancelledUrl.indexOf("?t=") + 3);
        TenantContext.clear();

        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", cancelledToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(PayslipOpenController.NOT_A_LINK));

        // 7. A link built with the same secret and the document domain is 404
        String[] parts = token.split("\\.");
        UUID employeePayrunId = UUID.fromString(parts[1]);
        long expires = Long.parseLong(parts[2]);
        String documentDomainToken = createDocumentDomainToken(TENANT_A, employeePayrunId, expires);

        mvc.perform(get(PublicEndpoints.PAYSLIP_OPEN).param("t", documentDomainToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(PayslipOpenController.NOT_A_LINK));
    }

    private String createDocumentDomainToken(UUID tenantId, UUID employeePayrunId, long expires) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                "integration-test-document-link-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update("infinevo:document-link:v1".getBytes(StandardCharsets.UTF_8));
        mac.update((byte) ':');
        mac.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(employeePayrunId.toString().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(Long.toString(expires).getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        return tenantId + "." + employeePayrunId + "." + expires + "." + signature;
    }
}
