package com.infinevo.payroll.fbp;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

/**
 * Integration tests verifying Row-Level Security on payroll.employee_fbp_component (W-27.2).
 * Verifies as app_user that Tenant A cannot read, query or declare against Tenant B's rows.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class FbpDeclarationRlsIT extends AbstractIntegrationTest {

    @Autowired
    private FbpDeclarationService fbpDeclarationService;

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    private UUID employeeBId;
    private UUID earningBId;

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

        // 1. Seed Tenant B Employee & FBP component
        TenantContext.set(TENANT_B);
        employeeBId = UUID.randomUUID();
        seedEmployee(TENANT_B, employeeBId, "EMP-B", "Brian", "TenantB");

        Earning fuelB = new Earning(TENANT_B, "test");
        fuelB.setCode("FUEL-B");
        fuelB.setName("Fuel B");
        fuelB.setEarningType("ALLOWANCE");
        fuelB.setCalculationType(CalculationType.FLAT);
        fuelB.setIncludedInCtc(true);
        fuelB.setFbpComponent(true);
        fuelB.setActive(true);
        fuelB = earningRepository.save(fuelB);
        earningBId = fuelB.getId();

        SalaryComponentItemRequest itemB = new SalaryComponentItemRequest(
                earningBId, CalculationType.FLAT, new BigDecimal("4000.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest reqB = new SalaryVersionRequest(
                new BigDecimal("48000.00"),
                LocalDate.of(2026, 1, 1),
                "Tenant B Structure",
                List.of(itemB),
                List.of(),
                List.of());
        SalaryVersionResponse vB = salaryService.create(employeeBId, reqB);

        // 2. Set declaration for Tenant B via officer
        FbpDeclarationRequest declReqB = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", earningBId, new BigDecimal("36000.0000"))));
        fbpDeclarationService.set(employeeBId, declReqB);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Tenant A cannot select or update Tenant B's employee_fbp_component via raw app_user connection under RLS")
    void tenantACannotSeeTenantBDeclarationsRawSql() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM payroll.employee_fbp_component WHERE employee_id = ?")) {
                ps.setObject(1, employeeBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must see 0 declaration rows for Tenant B under RLS")
                            .isZero();
                }
            }

            // Tenant A cannot update Tenant B's employee_fbp_component under RLS
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE payroll.employee_fbp_component SET annual_amount = 10000 WHERE employee_id = ?")) {
                ps.setObject(1, employeeBId);
                int updated = ps.executeUpdate();
                assertThat(updated)
                        .as("Tenant A updating Tenant B's employee_fbp_component must affect 0 rows under RLS")
                        .isZero();
            }

            // Tenant A cannot insert a row with tenant_id = Tenant B under RLS
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payroll.employee_fbp_component (id, tenant_id, ctc_structure_id, employee_id, annual_amount, monthly_amount, declared_at, declared_by, created_by) "
                            + "VALUES (?, ?, ?, ?, 1000, 100, now(), 'CARRIED', 'system')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, employeeBId);
                assertThatThrownBy(ps::executeUpdate)
                        .as("Tenant A inserting with tenant_id B must be blocked by RLS WITH CHECK")
                        .isInstanceOf(SQLException.class);
            }

            // Control check: bound to Tenant B
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM payroll.employee_fbp_component WHERE employee_id = ?")) {
                ps.setObject(1, employeeBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A service call cannot read or declare against Tenant B employee")
    void tenantACannotAccessTenantBViaService() {
        TenantContext.set(TENANT_A);

        // Tenant A officer reading Tenant B employee declaration -> fails with 404 EmployeeService.NotFoundException
        assertThatThrownBy(() -> fbpDeclarationService.read(employeeBId, LocalDate.now()))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        // Tenant A officer updating Tenant B employee declaration -> fails with 404 EmployeeService.NotFoundException
        FbpDeclarationRequest hackReq = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", earningBId, new BigDecimal("20000.0000"))));
        assertThatThrownBy(() -> fbpDeclarationService.set(employeeBId, hackReq))
                .isInstanceOf(EmployeeService.NotFoundException.class);
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
