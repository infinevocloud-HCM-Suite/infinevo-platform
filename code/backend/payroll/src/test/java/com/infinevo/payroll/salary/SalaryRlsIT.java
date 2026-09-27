package com.infinevo.payroll.salary;

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
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Integration tests verifying PostgreSQL Row-Level Security on salary tables (W-26.2).
 * Verifies that Tenant A cannot read, mutate, or access Tenant B's CTC structure,
 * allocated component lines, or statutory profile rows.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class SalaryRlsIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EmployeeStatutoryProfileService statutoryProfileService;

    @Autowired
    private EarningRepository earningRepository;

    private UUID employeeA;
    private UUID employeeB;
    private UUID ctcStructureB;
    private UUID earningLineB;
    private UUID statutoryProfileB;

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

        employeeA = UUID.randomUUID();
        employeeB = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeA, "EMP-A01", "TenantA", "User");
        seedEmployee(TENANT_B, employeeB, "EMP-B01", "TenantB", "User");

        // Seed data for Tenant B
        TenantContext.set(TENANT_B);
        Earning basicB = new Earning(TENANT_B, "system");
        basicB.setCode("BASIC");
        basicB.setName("Basic Salary");
        basicB.setEarningType("BASIC");
        basicB.setCalculationType(CalculationType.FLAT);
        basicB.setIncludedInCtc(true);
        basicB = earningRepository.save(basicB);

        SalaryComponentItemRequest itemReq = new SalaryComponentItemRequest(
                basicB.getId(), CalculationType.FLAT, new BigDecimal("50000.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest versionReq = new SalaryVersionRequest(
                new BigDecimal("600000.00"),
                LocalDate.of(2026, 1, 1),
                "Tenant B Initial",
                List.of(itemReq),
                List.of(),
                List.of());

        SalaryVersionResponse vB = salaryService.create(employeeB, versionReq);
        ctcStructureB = vB.id();
        earningLineB = vB.earnings().get(0).id();

        StatutoryProfileRequest statReq =
                new StatutoryProfileRequest(true, true, false, false, true, false, false, "PFB123", "UANB123", null);
        StatutoryProfileResponse statB = statutoryProfileService.upsert(employeeB, statReq);
        statutoryProfileB = statB.id();

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Unbound app_user sees no rows in any salary or statutory profile tables")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            for (String table : new String[] {
                "ctc_structure",
                "employee_earning",
                "employee_benefit",
                "employee_reimbursement",
                "employee_statutory_profile"
            }) {
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Table %s must return 0 rows when unbound", table)
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A raw app_user connection cannot see Tenant B's salary or statutory rows")
    void tenantACannotSeeTenantBRowsRawSql() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            assertThat(countVisibleById(conn, "ctc_structure", ctcStructureB)).isZero();
            assertThat(countVisibleById(conn, "employee_earning", earningLineB)).isZero();
            assertThat(countVisibleById(conn, "employee_statutory_profile", statutoryProfileB))
                    .isZero();

            // Control: Tenant B sees its own rows
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(countVisibleById(conn, "ctc_structure", ctcStructureB)).isEqualTo(1);
            assertThat(countVisibleById(conn, "employee_earning", earningLineB)).isEqualTo(1);
            assertThat(countVisibleById(conn, "employee_statutory_profile", statutoryProfileB))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Tenant A cannot access Tenant B salary data through service methods")
    void tenantACannotAccessTenantBViaServices() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> salaryService.getAsOf(employeeB, LocalDate.now()))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        assertThatThrownBy(() -> salaryService.listVersions(employeeB))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        assertThatThrownBy(() -> salaryService.cancel(employeeB, ctcStructureB))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        assertThatThrownBy(() -> statutoryProfileService.get(employeeB))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        StatutoryProfileRequest request =
                new StatutoryProfileRequest(true, false, false, false, false, false, false, null, null, null);
        assertThatThrownBy(() -> statutoryProfileService.upsert(employeeB, request))
                .isInstanceOf(EmployeeService.NotFoundException.class);
    }

    private static int countVisibleById(Connection conn, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
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
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, official_email) "
                                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
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
