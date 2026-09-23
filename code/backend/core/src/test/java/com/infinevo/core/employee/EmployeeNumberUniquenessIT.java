package com.infinevo.core.employee;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
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
 * W-13.1 — {@code employee_number} is unique within a tenant and not across them (spec section 7).
 *
 * <p>Two halves, and both are needed. The same number in two tenants is <strong>accepted</strong>:
 * the frozen column is globally unique
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:24-25},
 * and {@code employeeUniqueId} at line 139-140 likewise), which in a shared database lets one
 * customer's numbering block another's — spec section 9 names carrying that across as a risk. The
 * same number twice in one tenant is <strong>refused</strong>, as a conflict naming the number, not a
 * constraint-violation stack trace.
 *
 * <p>The last test goes round the service to the index itself, because the two enforce different
 * things: the service's check is what produces a usable message, and the index at
 * {@code V010__employee.sql:47} is what holds when two requests race. A test that only exercised the
 * service would still pass if the index were dropped.
 */
@SpringBootTest(classes = EmployeeTestApp.class)
class EmployeeNumberUniquenessIT extends AbstractIntegrationTest {

    private static final String SHARED_NUMBER = "EMP-0001";
    private static final LocalDate JOINED = LocalDate.of(2026, 4, 1);

    @Autowired
    private EmployeeService employeeService;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
    }

    /** See {@code EmployeeRlsIT.cleanUp} — the tenant foreign key makes leftovers another test's failure. */
    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The same employee number is accepted in two tenants")
    void acceptedInTwoTenants() throws SQLException {
        TenantContext.set(TENANT_A);
        EmployeeResponse inA = employeeService.create(request("Asha"));

        TenantContext.set(TENANT_B);
        EmployeeResponse inB = employeeService.create(request("Bharat"));

        assertThat(inA.employeeNumber()).isEqualTo(SHARED_NUMBER);
        assertThat(inB.employeeNumber()).isEqualTo(SHARED_NUMBER);
        assertThat(inA.id()).isNotEqualTo(inB.id());
        assertThat(EmployeeTestSchema.countEmployees(TENANT_A)).isEqualTo(1);
        assertThat(EmployeeTestSchema.countEmployees(TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("The same employee number twice in one tenant is refused, and nothing is written")
    void refusedTwiceInOneTenant() throws SQLException {
        TenantContext.set(TENANT_A);
        employeeService.create(request("Asha"));

        assertThatThrownBy(() -> employeeService.create(request("Anil")))
                .isInstanceOf(EmployeeService.DuplicateEmployeeNumberException.class)
                .hasMessageContaining(SHARED_NUMBER)
                .hasMessageContaining("this tenant");

        assertThat(EmployeeTestSchema.countEmployees(TENANT_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("An update cannot take a number another employee in the same tenant already holds")
    void updateCannotTakeAnotherNumber() {
        TenantContext.set(TENANT_A);
        employeeService.create(request("Asha"));
        UUID second = employeeService
                .create(new EmployeeRequest("EMP-0002", "Anil", null, null, null, JOINED, null, null, null, null, null))
                .id();

        assertThatThrownBy(() -> employeeService.update(second, request("Anil")))
                .isInstanceOf(EmployeeService.DuplicateEmployeeNumberException.class);
    }

    @Test
    @DisplayName("The index itself refuses the duplicate, with the service out of the way")
    void theIndexHoldsOnItsOwn() throws SQLException {
        EmployeeTestSchema.seedEmployee(TENANT_A, SHARED_NUMBER, "Asha");

        // As the owner, so this is the index refusing and not row-level security.
        assertThatThrownBy(() -> EmployeeTestSchema.seedEmployee(TENANT_A, SHARED_NUMBER, "Anil"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("idx_employee_tenant_employee_number");

        // ... and the same number in the other tenant still goes in.
        assertThat(EmployeeTestSchema.seedEmployee(TENANT_B, SHARED_NUMBER, "Bharat"))
                .isNotNull();
    }

    @Test
    @DisplayName("A tenant cannot be stated by the caller — a row whose tenant is not the bound one is refused")
    void aRowCannotBeWrittenIntoAnotherTenant() throws SQLException {
        // The service offers no way to do this, so it is attempted at the level below: app_user bound
        // to tenant A, inserting a row claiming tenant B. The policy's USING expression serves as the
        // WITH CHECK for INSERT, so the write is refused rather than quietly landing.
        try (Connection conn = EmployeeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, TENANT_A.toString());
                bind.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.employee
                        (tenant_id, employee_number, first_name, date_of_joining, status, created_by, updated_by)
                    VALUES (?, ?, 'Mallory', ?, 'ACTIVE', 'test', 'test')
                    """)) {
                ps.setObject(1, TENANT_B);
                ps.setString(2, "SMUGGLED");
                ps.setObject(3, JOINED);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            } finally {
                conn.rollback();
            }
        }
        assertThat(EmployeeTestSchema.countEmployees(TENANT_B)).isZero();
    }

    private static EmployeeRequest request(String firstName) {
        return new EmployeeRequest(SHARED_NUMBER, firstName, null, null, null, JOINED, null, null, null, null, null);
    }
}
