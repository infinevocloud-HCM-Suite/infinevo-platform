package com.infinevo.payroll.taxdeductor;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Acceptance integration tests for tax deductor settings (W-36.3, spec section 7).
 * Covers GET before PUT (404), PUT-then-GET round trip, second PUT leaving 1 row,
 * cross-tenant signatory (400), and permission enforcement (403).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class TaxDeductorIT extends AbstractIntegrationTest {

    @Autowired
    private TaxDeductorService taxDeductorService;

    @Autowired
    private TaxDeductorRepository repository;

    @Autowired
    private EmployeeService employeeService;

    private PermissionService permissionService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        permissionService = mock(PermissionService.class);
        when(permissionService.holds(anyString())).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new com.infinevo.shared.authz.PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(org.mockito.ArgumentMatchers.any());

        TaxDeductorController controller = new TaxDeductorController(taxDeductorService);
        TaxDeductorController proxied = proxyWithAuthz(controller, permissionService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mvc = MockMvcBuilders.standaloneSetup(proxied)
                .setMessageConverters(converter)
                .build();

        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyWithAuthz(T target, PermissionService permService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permService));
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("GET before any PUT returns 404")
    void getBeforeAnyPut_returns404() throws Exception {
        mvc.perform(get("/api/v1/payroll/settings/tax-deductor"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT then GET round-trips correctly")
    void putThenGet_roundTrips() throws Exception {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "Head of Payroll");

        mvc.perform(put("/api/v1/payroll/settings/tax-deductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.tan").value("MUMT12345A"))
                .andExpect(jsonPath("$.data.pan").value("ABCDE1234F"))
                .andExpect(jsonPath("$.data.tds_circle").value("MUM/TD/001/01"))
                .andExpect(jsonPath("$.data.signatory_name").value("Rajesh Sharma"))
                .andExpect(jsonPath("$.data.signatory_designation").value("Head of Payroll"));

        mvc.perform(get("/api/v1/payroll/settings/tax-deductor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.tan").value("MUMT12345A"))
                .andExpect(jsonPath("$.data.pan").value("ABCDE1234F"))
                .andExpect(jsonPath("$.data.tds_circle").value("MUM/TD/001/01"))
                .andExpect(jsonPath("$.data.signatory_name").value("Rajesh Sharma"))
                .andExpect(jsonPath("$.data.signatory_parent_name").value("Ramesh Sharma"))
                .andExpect(jsonPath("$.data.signatory_designation").value("Head of Payroll"));
    }

    @Test
    @DisplayName("A second PUT updates details and still leaves exactly one row")
    void secondPut_updatesAndLeavesOneRow() throws Exception {
        TaxDeductorRequest first = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "Head of Payroll");

        mvc.perform(put("/api/v1/payroll/settings/tax-deductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        TaxDeductorRequest second = new TaxDeductorRequest(
                "MUMT12345B", "ABCDE1234G", "MUM/TD/002/02", null, "Sunil Varma", null, "VP Finance");

        mvc.perform(put("/api/v1/payroll/settings/tax-deductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tan").value("MUMT12345B"))
                .andExpect(jsonPath("$.data.pan").value("ABCDE1234G"))
                .andExpect(jsonPath("$.data.signatory_name").value("Sunil Varma"));

        assertThat(countDbRows("payroll.tax_deductor", TENANT_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("A signatory from another tenant is refused with 400")
    void signatoryFromAnotherTenant_isRefusedWith400() throws Exception {
        UUID empB = UUID.randomUUID();
        seedEmployee(TENANT_B, empB, "EMPB001", "Bob", "Smith");

        TaxDeductorRequest req =
                new TaxDeductorRequest("MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", empB, "Bob Smith", null, "Manager");

        mvc.perform(put("/api/v1/payroll/settings/tax-deductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Without payroll.settings.manage the PUT call is 403")
    void withoutManageAction_putIs403() throws Exception {
        when(permissionService.holds("payroll.settings.manage")).thenReturn(false);

        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", null, null, "Rajesh Sharma", null, "Head of Payroll");

        mvc.perform(put("/api/v1/payroll/settings/tax-deductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static int countDbRows(String table, UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM " + table + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, '2026-01-01', 'ACTIVE') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, code.toLowerCase() + "@example.com");
            ps.executeUpdate();
        }
    }
}
