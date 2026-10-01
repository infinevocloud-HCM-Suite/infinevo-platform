package com.infinevo.payroll.payslip;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.Instant;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration test for officer and employee payslip reads (W-36.2 §7).
 * Officer read on COMPUTED works and on LOCKED is 409; GET /me/payslips before pay is empty and
 * after has one row; another employee's payrunId under /me is 404; SKIPPED employee is 404.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(properties = "document.link.secret=integration-test-document-link-secret")
public class PayslipReadIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayslipService payslipService;

    @Autowired
    private com.infinevo.core.employee.EmployeeService employeeService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private EmployeePayRunRepository employeePayRuns;

    private MockMvc mvc;
    private UUID employeeId;
    private UUID skippedEmployeeId;
    private PayRunResponse run;

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
        employeeId = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "EMP-01", catalogue);
        skippedEmployeeId = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "EMP-02", catalogue);

        run = payRunService.create(JULY);

        mvc = MockMvcBuilders.standaloneSetup(new PayslipController(payslipService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Officer read: LOCKED is 409, COMPUTED works; SKIPPED employee is 404")
    void officerReadFlow() throws Exception {
        payRunService.lock(run.id());

        // On LOCKED run, officer read throws PayslipConflictException (409)
        assertThatThrownBy(() -> payslipService.forOfficer(run.id(), employeeId))
                .isInstanceOf(PayslipConflictException.class);

        // Compute run
        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);

        // On COMPUTED run, officer read succeeds
        PayslipResponse slip = payslipService.forOfficer(run.id(), employeeId);
        assertThat(slip).isNotNull();
        assertThat(slip.employee().id()).isEqualTo(employeeId);
        assertThat(slip.run().payrunId()).isEqualTo(run.id());
        assertThat(slip.run().status()).isEqualTo("COMPUTED");
        assertThat(slip.totals().netPay()).isNotNull();

        // Mark second employee as SKIPPED
        PayRunTestSchema.execute(
                "UPDATE payroll.employee_payrun SET inclusion_status = 'SKIPPED', skip_reason = 'NO_SALARY' WHERE tenant_id = ? AND payrun_id = ? AND employee_id = ?",
                TENANT_A,
                run.id(),
                skippedEmployeeId);

        // Officer read for SKIPPED employee is 404
        assertThatThrownBy(() -> payslipService.forOfficer(run.id(), skippedEmployeeId))
                .isInstanceOf(PayslipNotFoundException.class)
                .hasMessageContaining("skipped");
    }

    @Test
    @DisplayName("Employee read /me: empty before pay, 1 row after pay; other run is 404")
    void employeeMeReadFlow() throws Exception {
        payRunService.lock(run.id());
        worker.computeNow(run.id());

        EmployeeResponse empResp = new EmployeeResponse(
                employeeId,
                TENANT_A,
                "EMP-01",
                "Worker",
                null,
                "One",
                "MALE",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "worker1@test.com",
                null,
                true,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        PayrollTestApp.CURRENT_EMPLOYEE.set(empResp);

        // Before pay: GET /api/v1/me/payslips is empty
        Page<PayslipSummaryResponse> beforePay = payslipService.listOwn(PageRequest.of(0, 12));
        assertThat(beforePay.getContent()).isEmpty();

        mvc.perform(get("/api/v1/me/payslips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        // Approve and pay
        payRunService.approve(run.id());
        payRunService.pay(run.id(), LocalDate.of(2026, 7, 31));

        // After pay: GET /api/v1/me/payslips has one row
        Page<PayslipSummaryResponse> afterPay = payslipService.listOwn(PageRequest.of(0, 12));
        assertThat(afterPay.getContent()).hasSize(1);
        PayslipSummaryResponse summary = afterPay.getContent().get(0);
        assertThat(summary.payrunId()).isEqualTo(run.id());
        assertThat(summary.period()).isEqualTo("2026-07");
        assertThat(summary.paidOn()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(summary.netPay()).isNotNull();

        mvc.perform(get("/api/v1/me/payslips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(
                        jsonPath("$.data.content[0].payrun_id").value(run.id().toString()))
                .andExpect(jsonPath("$.data.content[0].period").value("2026-07"));

        // GET /api/v1/me/payslips/{payrunId} for own run succeeds
        mvc.perform(get("/api/v1/me/payslips/" + run.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.payrun_id").value(run.id().toString()))
                .andExpect(jsonPath("$.data.run.status").value("PAID"))
                .andExpect(jsonPath("$.data.employee.id").value(employeeId.toString()));

        // Another payrunId under /me that employee does not own is 404
        UUID randomRunId = UUID.randomUUID();
        mvc.perform(get("/api/v1/me/payslips/" + randomRunId)).andExpect(status().isNotFound());
    }
}
