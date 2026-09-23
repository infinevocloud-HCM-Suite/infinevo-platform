package com.infinevo.core.employee;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-13.1 — tenant A cannot read, update or delete tenant B's employee, as {@code app_user}
 * (spec section 7).
 *
 * <p>Asserted at two levels, because they fail independently.
 *
 * <ul>
 *   <li>Through the service, which is how the application reaches the row.
 *   <li>On a raw {@code app_user} connection, which is the policy at
 *       {@code V010__employee.sql:55-62} alone with no Java in the way.
 * </ul>
 *
 * <p><strong>The raw read is the one that matters</strong>, and spec section 9 says why: this table
 * is soft-deleted, and the service excludes deleted rows in Java. A test that only asked the service
 * would see "not found" and could not tell an enforced policy from a {@code WHERE} clause that
 * happens to filter the same row. So every cross-tenant assertion below ends at a hard SQL read as
 * {@code app_user}, and the control — the same read with the owning tenant bound — proves the row was
 * there to be hidden.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = EmployeeTestApp.class)
class EmployeeRlsIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private UUID employeeOfA;
    private UUID employeeOfB;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
    }

    /**
     * Leaves no employee row behind. {@code core.employee} has a foreign key to {@code core.tenant},
     * and other integration tests in this module clear the tenant table — a leftover row here would
     * fail one of those with a constraint error that says nothing about either feature.
     */
    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();
        employeeOfA = EmployeeTestSchema.seedEmployee(TENANT_A, "A-001", "Asha");
        employeeOfB = EmployeeTestSchema.seedEmployee(TENANT_B, "B-001", "Bharat");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Both rows exist — the control for everything below")
    void bothRowsWereWritten() throws SQLException {
        assertThat(EmployeeTestSchema.countEmployees(TENANT_A)).isEqualTo(1);
        assertThat(EmployeeTestSchema.countEmployees(TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant, on a raw app_user connection")
    void rlsHidesTheOtherTenantOnARawConnection() throws SQLException {
        assertThat(EmployeeTestSchema.visibleRowCount(TENANT_A)).isEqualTo(1);
        assertThat(EmployeeTestSchema.visibleToAppUser(TENANT_A, employeeOfA)).isTrue();
        assertThat(EmployeeTestSchema.visibleToAppUser(TENANT_A, employeeOfB))
                .as("app_user bound to tenant A must not see tenant B's employee")
                .isFalse();
    }

    @Test
    @DisplayName("With no tenant bound, app_user sees no employee at all")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.employee")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("Bound to A, the repository sees A's employee and no other")
    void repositorySeesOnlyTheBoundTenant() {
        TenantContext.set(TENANT_A);
        List<Employee> rows = transactionTemplate.execute(status -> employeeRepository.findAll());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTenantId()).isEqualTo(TENANT_A);
        assertThat(rows.get(0).getEmployeeNumber()).isEqualTo("A-001");
    }

    @Test
    @DisplayName("Tenant A cannot read tenant B's employee")
    void cannotRead() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> employeeService.get(employeeOfB))
                .isInstanceOf(EmployeeService.NotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot update tenant B's employee, and the row is untouched")
    void cannotUpdate() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> employeeService.update(
                        employeeOfB,
                        new EmployeeRequest(
                                "HIJACKED",
                                "Mallory",
                                null,
                                null,
                                null,
                                LocalDate.of(2026, 4, 1),
                                null,
                                EmploymentStatus.ACTIVE,
                                null,
                                null,
                                null)))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        assertThat(EmployeeTestSchema.readColumn(employeeOfB, "first_name")).isEqualTo("Bharat");
        assertThat(EmployeeTestSchema.readColumn(employeeOfB, "employee_number"))
                .isEqualTo("B-001");
    }

    @Test
    @DisplayName("Tenant A cannot delete tenant B's employee — and it is not even soft-deleted")
    void cannotDelete() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> employeeService.delete(employeeOfB))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        // The hard read, as the owner: the row is still there and its flag is still false. Asserting
        // only that tenant A cannot see it would pass even if the delete had gone through, because a
        // soft-deleted row is invisible to everyone.
        assertThat(EmployeeTestSchema.countEmployees(TENANT_B)).isEqualTo(1);
        assertThat(EmployeeTestSchema.readColumn(employeeOfB, "is_deleted")).isEqualTo(false);
        assertThat(EmployeeTestSchema.visibleToAppUser(TENANT_B, employeeOfB)).isTrue();
    }

    @Test
    @DisplayName("A raw UPDATE and a raw DELETE across the tenant boundary change nothing")
    void rawWritesAcrossTheBoundaryAffectNoRow() throws SQLException {
        assertThat(rowsAffectedAs(TENANT_A, "UPDATE core.employee SET first_name = 'Mallory' WHERE id = ?"))
                .as("app_user bound to tenant A must not be able to update tenant B's row")
                .isZero();
        assertThat(rowsAffectedAs(TENANT_A, "DELETE FROM core.employee WHERE id = ?"))
                .as("app_user bound to tenant A must not be able to delete tenant B's row")
                .isZero();

        assertThat(EmployeeTestSchema.countEmployees(TENANT_B)).isEqualTo(1);
        assertThat(EmployeeTestSchema.readColumn(employeeOfB, "first_name")).isEqualTo("Bharat");
    }

    /** Runs one statement against tenant B's row as {@code app_user} with {@code tenantId} bound. */
    private int rowsAffectedAs(UUID tenantId, String sql) throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, tenantId.toString());
                bind.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, employeeOfB);
                int affected = ps.executeUpdate();
                conn.rollback();
                return affected;
            }
        }
    }
}
