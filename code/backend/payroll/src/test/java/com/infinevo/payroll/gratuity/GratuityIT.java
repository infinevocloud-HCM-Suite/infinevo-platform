package com.infinevo.payroll.gratuity;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration tests for W-91 Gratuity estimation, REST controller, and settlement persistence.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class GratuityIT extends AbstractIntegrationTest {

    @Autowired
    private GratuityService gratuityService;

    @Autowired
    private EmployeeSalaryService employeeSalaryService;

    @Autowired
    private EarningRepository earningRepository;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mvc;
    private UUID employeeId;

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
        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-GRAT-01", LocalDate.of(2020, 1, 1));

        Earning basic = new Earning(TENANT_A, "system");
        basic.setCode("BASIC");
        basic.setName("Basic");
        basic.setEarningType("BASIC");
        basic.setCalculationType(CalculationType.FLAT);
        basic.setIncludedInCtc(true);
        basic = earningRepository.saveAndFlush(basic);

        Earning da = new Earning(TENANT_A, "system");
        da.setCode("DA");
        da.setName("Dearness Allowance");
        da.setEarningType("ALLOWANCE");
        da.setCalculationType(CalculationType.FLAT);
        da.setIncludedInCtc(true);
        da = earningRepository.saveAndFlush(da);

        employeeSalaryService.create(
                employeeId,
                new SalaryVersionRequest(
                        new BigDecimal("624000.00"),
                        LocalDate.of(2020, 1, 1),
                        "Initial salary",
                        List.of(
                                new SalaryComponentItemRequest(
                                        basic.getId(),
                                        CalculationType.FLAT,
                                        new BigDecimal("40000.00"),
                                        null,
                                        true,
                                        null,
                                        null),
                                new SalaryComponentItemRequest(
                                        da.getId(),
                                        CalculationType.FLAT,
                                        new BigDecimal("12000.00"),
                                        null,
                                        true,
                                        null,
                                        null)),
                        List.of(),
                        List.of()));

        mvc = MockMvcBuilders.standaloneSetup(new GratuityController(gratuityService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Estimates gratuity, creates settlement, retrieves it, and enforces uniqueness and tenant isolation")
    void estimateCreateAndRetrieveSettlement() throws Exception {
        TenantContext.set(TENANT_A);
        LocalDate exitDate = LocalDate.of(2025, 8, 1); // 5y 7m -> 6 years

        String estimateJson = mvc.perform(post("/api/v1/payroll/gratuity/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new GratuityEstimateRequest(employeeId, exitDate, GratuityReason.RESIGNATION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.completedYears").value(6))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode estNode = mapper.readTree(estimateJson);
        assertThat(estNode.get("lastDrawnBasic").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(estNode.get("lastDrawnDa").decimalValue()).isEqualByComparingTo("12000.00");
        // 15 * 52000 * 6 / 26 = 180000.00
        assertThat(estNode.get("calculatedAmount").decimalValue()).isEqualByComparingTo("180000.00");
        assertThat(estNode.get("grossGratuityPayable").decimalValue()).isEqualByComparingTo("180000.00");
        assertThat(estNode.get("taxExemptAmount").decimalValue()).isEqualByComparingTo("180000.00");
        assertThat(estNode.get("taxableAmount").decimalValue()).isEqualByComparingTo("0.00");

        String createdJson = mvc.perform(post("/api/v1/payroll/gratuity/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new GratuitySettlementCreateRequest(employeeId, exitDate, GratuityReason.RESIGNATION))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID settlementId =
                UUID.fromString(mapper.readTree(createdJson).get("id").asText());

        mvc.perform(get("/api/v1/payroll/gratuity/settlements/{id}", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.completedYears").value(6))
                .andExpect(jsonPath("$.grossGratuityPayable").value(180000.0));

        // Duplicate active settlement is rejected with 409 Conflict
        mvc.perform(post("/api/v1/payroll/gratuity/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new GratuitySettlementCreateRequest(employeeId, exitDate, GratuityReason.RESIGNATION))))
                .andExpect(status().isConflict());

        // Cross-tenant read is isolated (404 Not Found)
        TenantContext.set(TENANT_B);
        mvc.perform(get("/api/v1/payroll/gratuity/settlements/{id}", settlementId))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> gratuityService.getSettlement(settlementId))
                .isInstanceOf(GratuityNotFoundException.class);
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, LocalDate joiningDate)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, ?, 'Gratuity', 'Employee', ?, ?, 'ACTIVE') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, code.toLowerCase() + "@example.com");
            ps.setObject(5, joiningDate);
            ps.executeUpdate();
        }
    }
}
