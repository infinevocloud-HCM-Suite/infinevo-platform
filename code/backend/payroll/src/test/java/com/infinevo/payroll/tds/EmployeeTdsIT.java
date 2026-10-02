package com.infinevo.payroll.tds;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
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
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration tests for TDS API and entity lifecycle (W-36.1 §7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeTdsIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeTdsService employeeTdsService;

    @Autowired
    private EmployeeTdsRepository repository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private PermissionService permissionService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;
    private UUID employeeAId;
    private UUID employeeBId;

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
        TenantContext.set(TENANT_A);

        employeeAId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeAId, "EMP-TDS-01", "Alice", "Smith");

        employeeBId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeBId, "EMP-TDS-02", "Bob", "Jones");

        permissionService = mock(PermissionService.class);
        when(permissionService.holds(anyString())).thenReturn(true);

        EmployeeTdsController controller = new EmployeeTdsController(employeeTdsService);
        EmployeeTdsController proxied = proxyWithAuthz(controller, permissionService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mvc = MockMvcBuilders.standaloneSetup(proxied)
                .setMessageConverters(converter)
                .build();
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
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
    @DisplayName(
            "PUT twice leaves two rows: one active (superseded_at null) and one superseded; GET returns active; history returns both")
    void putTwiceSupersedesAndReturnsActiveAndHistory() throws Exception {
        RecordTdsRequest req1 = new RecordTdsRequest(
                TaxRegime.OLD,
                new BigDecimal("600000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("100000.00"),
                "2026-04",
                "Initial computation");

        mvc.perform(put("/api/v1/payroll/employees/" + employeeAId + "/tds/2026-2027")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annual_tax").value(100000.0))
                .andExpect(jsonPath("$.data.is_active").value(true))
                .andExpect(jsonPath("$.data.superseded_at").doesNotExist());

        RecordTdsRequest req2 = new RecordTdsRequest(
                TaxRegime.NEW,
                new BigDecimal("650000.00"),
                new BigDecimal("550000.00"),
                new BigDecimal("120000.00"),
                "2026-04",
                "Revised computation");

        mvc.perform(put("/api/v1/payroll/employees/" + employeeAId + "/tds/2026-2027")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annual_tax").value(120000.0))
                .andExpect(jsonPath("$.data.is_active").value(true))
                .andExpect(jsonPath("$.data.regime").value("NEW"));

        List<EmployeeTds> rows = new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> repository.findByTenantIdAndEmployeeIdAndFinancialYearOrderByCreatedAtDesc(
                        TENANT_A, employeeAId, "2026-2027"));
        assertThat(rows).hasSize(2);

        EmployeeTds newest = rows.get(0);
        EmployeeTds older = rows.get(1);

        assertThat(newest.isActive()).isTrue();
        assertThat(newest.getSupersededAt()).isNull();
        assertThat(newest.getAnnualTax()).isEqualByComparingTo("120000.0000");

        assertThat(older.isActive()).isFalse();
        assertThat(older.getSupersededAt()).isNotNull();
        assertThat(older.getAnnualTax()).isEqualByComparingTo("100000.0000");

        // GET returns the active one
        mvc.perform(get("/api/v1/payroll/employees/" + employeeAId + "/tds/2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annual_tax").value(120000.0))
                .andExpect(jsonPath("$.data.is_active").value(true));

        // GET history returns both rows, newest first
        mvc.perform(get("/api/v1/payroll/employees/" + employeeAId + "/tds/2026-2027/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].annual_tax").value(120000.0))
                .andExpect(jsonPath("$.data[1].annual_tax").value(100000.0));
    }

    @Test
    @DisplayName("GET /me/tds/{fy} for user without linked active row is 404")
    void meTdsNotFoundWhenNoActiveRow() throws Exception {
        PayrollTestApp.CURRENT_EMPLOYEE.set(new EmployeeResponse(
                employeeBId,
                TENANT_A,
                "EMP-TDS-02",
                "Bob",
                null,
                "Jones",
                "MALE",
                LocalDate.of(2025, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "bob@example.com",
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null));
        mvc.perform(get("/api/v1/me/tds/2026-2027")).andExpect(status().isNotFound());
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (id, tenant_id, employee_number, first_name, last_name, gender, date_of_joining, status,
                             created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, 'MALE', DATE '2023-04-01', 'ACTIVE', 'test', 'test')
                        """)) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.executeUpdate();
        }
    }
}
