package com.infinevo.payroll.gratuity;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for W-91 Gratuity estimation and settlement persistence.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class GratuityIT extends AbstractIntegrationTest {

    @Autowired
    private GratuityController gratuityController;

    @Autowired
    private EmployeeSalaryService employeeSalaryService;

    @Autowired
    private EarningRepository earningRepository;

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
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Estimates gratuity, creates settlement, retrieves it, and enforces uniqueness and tenant isolation")
    void estimateCreateAndRetrieveSettlement() {
        TenantContext.set(TENANT_A);
        LocalDate exitDate = LocalDate.of(2025, 8, 1); // 5y 7m -> 6 years

        GratuityEstimateResponse estimate = gratuityController.estimate(
                new GratuityEstimateRequest(employeeId, exitDate, GratuityReason.RESIGNATION));

        assertThat(estimate.eligible()).isTrue();
        assertThat(estimate.completedYears()).isEqualTo(6);
        assertThat(estimate.lastDrawnBasic()).isEqualByComparingTo("40000.00");
        assertThat(estimate.lastDrawnDa()).isEqualByComparingTo("12000.00");
        // 15 * 52000 * 6 / 26 = 180000.00
        assertThat(estimate.calculatedAmount()).isEqualByComparingTo("180000.00");
        assertThat(estimate.grossGratuityPayable()).isEqualByComparingTo("180000.00");
        assertThat(estimate.taxExemptAmount()).isEqualByComparingTo("180000.00");
        assertThat(estimate.taxableAmount()).isEqualByComparingTo("0.00");

        ResponseEntity<GratuitySettlementResponse> createdResp = gratuityController.createSettlement(
                new GratuitySettlementCreateRequest(employeeId, exitDate, GratuityReason.RESIGNATION));
        assertThat(createdResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        GratuitySettlementResponse created = createdResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(GratuitySettlementStatus.DRAFT);
        assertThat(created.grossGratuityPayable()).isEqualByComparingTo("180000.00");

        GratuitySettlementResponse fetched = gratuityController.getSettlement(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.employeeId()).isEqualTo(employeeId);
        assertThat(fetched.completedYears()).isEqualTo(6);

        // Duplicate active settlement is rejected with conflict
        assertThatThrownBy(() -> gratuityController.createSettlement(
                        new GratuitySettlementCreateRequest(employeeId, exitDate, GratuityReason.RESIGNATION)))
                .isInstanceOf(GratuityConflictException.class);

        // Cross-tenant read is isolated
        TenantContext.set(TENANT_B);
        assertThatThrownBy(() -> gratuityController.getSettlement(created.id()))
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
