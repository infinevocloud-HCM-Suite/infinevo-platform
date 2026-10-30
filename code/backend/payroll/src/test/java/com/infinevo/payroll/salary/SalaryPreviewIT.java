package com.infinevo.payroll.salary;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.PercentageOf;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-75.1 §7 — {@code POST …/salary/preview} and a create carrying a balancing earning, through the real controller
 * behind the real {@link RequiresActionAspect} and the real service against Postgres as {@code app_user}. Whether a
 * role holds {@code payroll.salary.manage} is read from the seeded {@code core.role_action} grants, not assumed.
 *
 * <p>Tenant settings: employer EPF, EDLI and admin in CTC, employer side restricted to the 15,000 ceiling. With a
 * 6,00,000 CTC, Basic 50% of CTC and HRA 50% of Basic, the employer lines are 1,800 + 75 + 75 = 1,950 a month, so
 * Special balances to 50,000 − 25,000 − 12,500 − 1,950 = 10,550.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class SalaryPreviewIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/payroll/employees/{employeeId}/salary";

    /** A tenant inserted here, so its system roles come from the provisioning trigger alone. */
    private static final UUID ROLE_TENANT = UUID.randomUUID();

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private final AtomicReference<String> role = new AtomicReference<>();

    private MockMvc mvc;
    private UUID employeeId;
    private UUID basicId;
    private UUID hraId;
    private UUID specialId;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, 'Salary preview roles')")) {
            ps.setObject(1, ROLE_TENANT);
            ps.executeUpdate();
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId);
        seedStatutoryProfile(TENANT_A, employeeId);
        seedEpfSetting(TENANT_A);

        TenantContext.set(TENANT_A);
        basicId = saveEarning("BASIC", "BASIC");
        hraId = saveEarning("HRA", "ALLOWANCE");
        specialId = saveEarning("SPECIAL", "ALLOWANCE");

        mvc = mockMvc();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Preview: 200 for payroll officer with Special balanced and nothing saved; 403 for finance")
    void previewGuardAndNoWrite() throws Exception {
        role.set("payroll-officer");
        String body = mvc.perform(post(BASE + "/preview", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(previewRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balancingComponentId").value(specialId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = mapper.readTree(body);
        assertThat(monthlyOf(json, specialId)).isEqualByComparingTo("10550.00");
        assertThat(json.get("statutory")).hasSize(5);
        assertThat(json.get("grossMonthly").decimalValue()).isEqualByComparingTo("48050.00");
        // 48,050 gross − 1,800 employee EPF (12% of the 15,000 ceiling); PT not eligible
        assertThat(json.get("takeHomeMonthlyEstimate").decimalValue()).isEqualByComparingTo("46250.00");
        assertThat(countRows("payroll.ctc_structure")).isZero();
        assertThat(countRows("payroll.ctc_epf_component")).isZero();

        role.set("finance");
        mvc.perform(post(BASE + "/preview", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(previewRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Create with a balancing earning saves a version whose in-CTC total equals the CTC")
    void createWithBalancingBalances() throws Exception {
        role.set("payroll-officer");
        String body = mvc.perform(post(BASE, employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(versionRequest(true))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = mapper.readTree(body);
        assertThat(monthlyOf(json, specialId)).isEqualByComparingTo("10550.00");

        BigDecimal included = BigDecimal.ZERO;
        for (String list : List.of("earnings", "benefits", "reimbursements", "statutory")) {
            for (JsonNode item : json.get(list)) {
                if (item.get("includedInCtc").asBoolean()) {
                    included = included.add(item.get("annualAmount").decimalValue());
                }
            }
        }
        assertThat(included).isEqualByComparingTo("600000.00");

        TenantContext.set(TENANT_A);
        SalaryVersionResponse stored = salaryService.getAsOf(employeeId, LocalDate.of(2026, 4, 1));
        assertThat(stored.earnings())
                .filteredOn(e -> specialId.equals(e.componentId()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.calculationType()).isEqualTo(CalculationType.FLAT);
                    assertThat(e.value()).isEqualByComparingTo("10550.00");
                });
    }

    @Test
    @DisplayName("Without balancing, an unbalanced request is refused with the CTC difference as before")
    void withoutBalancingRefusedAsBefore() throws Exception {
        role.set("payroll-officer");
        mvc.perform(post(BASE, employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(versionRequest(false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.annualCtc").value(containsString("Difference: ")));
        assertThat(countRows("payroll.ctc_structure")).isZero();
    }

    private SalaryPreviewRequest previewRequest() {
        SalaryVersionRequest v = versionRequest(true);
        return new SalaryPreviewRequest(
                v.annualCtc(), v.effectiveFrom(), v.notes(), v.earnings(), v.benefits(), v.reimbursements(), null);
    }

    /** With balancing, Special's sent 1.00 is ignored; without, 1.00 leaves the version unbalanced. */
    private SalaryVersionRequest versionRequest(boolean balancing) {
        return new SalaryVersionRequest(
                new BigDecimal("600000.00"),
                LocalDate.of(2026, 4, 1),
                null,
                List.of(
                        new SalaryComponentItemRequest(
                                basicId,
                                CalculationType.PERCENTAGE,
                                new BigDecimal("50"),
                                PercentageOf.CTC,
                                true,
                                "MONTHLY",
                                null),
                        new SalaryComponentItemRequest(
                                hraId,
                                CalculationType.PERCENTAGE,
                                new BigDecimal("50"),
                                PercentageOf.BASIC,
                                true,
                                "MONTHLY",
                                null),
                        new SalaryComponentItemRequest(
                                specialId,
                                CalculationType.FLAT,
                                new BigDecimal("1.00"),
                                null,
                                true,
                                "MONTHLY",
                                null,
                                balancing ? Boolean.TRUE : null)),
                List.of(),
                List.of());
    }

    private static BigDecimal monthlyOf(JsonNode json, UUID componentId) {
        for (JsonNode e : json.get("earnings")) {
            if (componentId.toString().equals(e.get("componentId").asText())) {
                return e.get("monthlyAmount").decimalValue();
            }
        }
        throw new AssertionError("No earning " + componentId);
    }

    private UUID saveEarning(String code, String type) {
        Earning e = new Earning(TENANT_A, "system");
        e.setCode(code);
        e.setName(code);
        e.setEarningType(type);
        e.setCalculationType(CalculationType.FLAT);
        e.setIncludedInCtc(true);
        return earningRepository.save(e).getId();
    }

    /** The controller behind the real aspect; {@code holds} answers from the current role's seeded grants. */
    private MockMvc mockMvc() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenAnswer(inv -> grants(role.get(), inv.getArgument(0)) > 0);
        doAnswer(inv -> {
                    String action = inv.getArgument(0);
                    if (!permissions.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissions)
                .require(any());

        AspectJProxyFactory factory = new AspectJProxyFactory(new EmployeeSalaryController(salaryService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissions));
        EmployeeSalaryController controller = factory.getProxy();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private static int grants(String roleCode, String actionCode) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.role_action ra "
                        + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                        + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code = ?")) {
            ps.setObject(1, ROLE_TENANT);
            ps.setString(2, roleCode);
            ps.setString(3, actionCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countRows(String table) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM " + table)) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, 'EMP-PRV-01', 'Priya', 'Rao', 'emp-prv-01@example.com', '2026-01-01', 'ACTIVE') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private static void seedStatutoryProfile(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.employee_statutory_profile (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi, contributes_eps_on_higher_wages) "
                                + "VALUES (gen_random_uuid(), ?, ?, true, true, false, false) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }
    }

    private static void seedEpfSetting(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.epf_setting (id, tenant_id, is_enabled, wage_ceiling, restrict_employee_to_ceiling, restrict_employer_to_ceiling, employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, eps_senior_age, include_employer_in_ctc, include_edli_in_ctc, include_admin_in_ctc) "
                                + "VALUES (gen_random_uuid(), ?, true, 15000.0000, true, true, 12.0000, 12.0000, 8.3300, 0.5000, 0.5000, 58, true, true, true) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }
}
