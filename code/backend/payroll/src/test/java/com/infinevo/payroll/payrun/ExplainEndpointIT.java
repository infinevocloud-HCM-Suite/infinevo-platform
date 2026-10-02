package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.payroll.PayrollTestApp;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-18.2 §7 — {@code GET .../explain} over a real computed run, behind the real
 * {@link RequiresActionAspect}. The explanation matches the stored stamp exactly; an employee holding
 * only {@code payroll.payslip.read_own} reads their own and is {@code 403} on a colleague's;
 * {@code payroll.run.read} reads either; neither code is {@code 403}.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ExplainEndpointIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final String READ_ANY = "payroll.run.read";
    private static final String READ_OWN = "payroll.payslip.read_own";

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayFigureExplanationService explanationService;

    /** PayrollTestApp's mock, shared by the context: re-stubbed per test, restored to "holds all" after. */
    @Autowired
    private PermissionService permissionService;

    private MockMvc mvc;
    private UUID runId;
    private UUID me;
    private UUID colleague;

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
        me = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "X-01", catalogue);
        colleague = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "X-02", catalogue);
        runId = payRunService.create(JULY).id();
        payRunService.lock(runId);
        assertThat(worker.computeNow(runId).status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(TENANT_A);

        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());
        AspectJProxyFactory factory = new AspectJProxyFactory(new PayRunExplainController(explanationService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        mvc = MockMvcBuilders.standaloneSetup((PayRunExplainController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @AfterEach
    void tearDown() {
        when(permissionService.holds(anyString())).thenReturn(true);
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    @Test
    @DisplayName("payroll.run.read: either employee, and the answer is the stored stamp exactly")
    void runReadExplainsAnyRowFromTheStoredStamp() throws Exception {
        holds(READ_ANY);
        PayRunTestSchema.StampRow stored = PayRunTestSchema.stamps(TENANT_A, runId).stream()
                .filter(row -> row.employeeId().equals(colleague))
                .findFirst()
                .orElseThrow();

        mvc.perform(get(path(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employee_id").value(colleague.toString()))
                .andExpect(jsonPath("$.data.lop_policy_id")
                        .value(stored.lopPolicyId().toString()))
                .andExpect(jsonPath("$.data.working_day_basis").value(stored.workingDayBasis()))
                .andExpect(
                        jsonPath("$.data.pay_divisor").value(stored.payDivisor().doubleValue()))
                .andExpect(jsonPath("$.data.payable_days")
                        .value(stored.payableDays().doubleValue()))
                .andExpect(jsonPath("$.data.lop_rounding").value(stored.lopRounding()))
                .andExpect(jsonPath("$.data.net_pay").value(47000.0));
        mvc.perform(get(path(me))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("payroll.payslip.read_own only: their own row is 200, a colleague's is 403")
    void readOwnReadsOnlyTheirOwn() throws Exception {
        holds(READ_OWN);
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee(me));

        mvc.perform(get(path(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employee_id").value(me.toString()));
        mvc.perform(get(path(colleague))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Neither code: 403 before the service is reached")
    void neitherCodeIsForbidden() throws Exception {
        holds();
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee(me));

        mvc.perform(get(path(me))).andExpect(status().isForbidden());
    }

    private void holds(String... codes) {
        Set<String> held = Set.of(codes);
        when(permissionService.holds(anyString()))
                .thenAnswer(invocation -> held.contains(invocation.<String>getArgument(0)));
    }

    private String path(UUID employeeId) {
        return "/api/v1/payroll/payruns/" + runId + "/employees/" + employeeId + "/explain";
    }

    private static EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id,
                TENANT_A,
                "X",
                "First",
                null,
                "Last",
                "MALE",
                LocalDate.of(2020, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
