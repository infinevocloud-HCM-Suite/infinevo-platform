package com.infinevo.core.org;

import static com.infinevo.core.org.OrgTestSchema.TENANT_A;
import static com.infinevo.core.org.OrgTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
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
 * W-14.1 — an employee cannot be assigned a master belonging to another tenant (spec section 7).
 *
 * <p><strong>This test exists because the foreign key does not do it.</strong> Spec section 7 is
 * explicit and {@code V014__employee_org_columns.sql} repeats it: {@code migration_user} owns these
 * tables, PostgreSQL runs referential-integrity checks as the owner with row security switched off,
 * and so the key on {@code department_id} resolves happily against a row in another tenant. The test
 * at {@link #theForeignKeyAloneDoesNotStopIt()} demonstrates precisely that, as {@code app_user},
 * and is the reason {@code EmployeeServiceImpl.resolve} is load-bearing rather than belt and braces.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class EmployeeAssignmentIT extends AbstractIntegrationTest {

    private static final LocalDate JOINED = LocalDate.of(2026, 4, 1);

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private DepartmentService departmentService;

    private UUID departmentOfA;
    private UUID designationOfA;
    private UUID workLocationOfA;
    private UUID departmentOfB;

    @BeforeAll
    static void applySchema() throws Exception {
        OrgTestSchema.apply();
    }

    /** See {@code OrgMasterRlsIT.cleanUp} — the tenant foreign key makes leftovers another test's failure. */
    @AfterAll
    static void cleanUp() throws SQLException {
        OrgTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        OrgTestSchema.seedTenants();
        OrgTestSchema.clearAll();
        departmentOfA = OrgTestSchema.seedDepartment(TENANT_A, "FIN", "Finance", true);
        designationOfA = OrgTestSchema.seedDesignation(TENANT_A, "MGR", "Manager", true);
        workLocationOfA = OrgTestSchema.seedWorkLocation(TENANT_A, "HQ", "Head office", false);
        departmentOfB = OrgTestSchema.seedDepartment(TENANT_B, "FIN", "Finanzas", true);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("An employee is assigned the three masters of its own tenant — the control")
    void assignmentWithinTheTenantWorks() throws SQLException {
        TenantContext.set(TENANT_A);

        EmployeeResponse created =
                employeeService.create(request("A-001", departmentOfA, designationOfA, workLocationOfA));

        assertThat(created.departmentId()).isEqualTo(departmentOfA);
        assertThat(created.designationId()).isEqualTo(designationOfA);
        assertThat(created.workLocationId()).isEqualTo(workLocationOfA);
        assertThat(OrgTestSchema.readColumn("employee", created.id(), "department_id"))
                .isEqualTo(departmentOfA);
    }

    @Test
    @DisplayName("Creating an employee in tenant A with tenant B's department is refused, and nothing is written")
    void createWithAnotherTenantsDepartmentIsRefused() throws SQLException {
        TenantContext.set(TENANT_A);

        EmployeeService.ValidationException thrown = catchThrowableOfType(
                () -> employeeService.create(request("A-001", departmentOfB, null, null)),
                EmployeeService.ValidationException.class);

        assertThat(thrown.fieldErrors()).containsOnlyKeys("departmentId");
        assertThat(thrown.fieldErrors().get("departmentId")).contains("in this tenant");
        assertThat(OrgTestSchema.countFor("employee", TENANT_A)).isZero();
    }

    @Test
    @DisplayName("Updating an employee onto tenant B's department is refused, and the column is untouched")
    void updateOntoAnotherTenantsDepartmentIsRefused() throws SQLException {
        TenantContext.set(TENANT_A);
        UUID employeeId = employeeService
                .create(request("A-001", departmentOfA, null, null))
                .id();

        EmployeeService.ValidationException thrown = catchThrowableOfType(
                () -> employeeService.update(employeeId, request("A-001", departmentOfB, null, null)),
                EmployeeService.ValidationException.class);

        assertThat(thrown.fieldErrors()).containsOnlyKeys("departmentId");
        assertThat(OrgTestSchema.readColumn("employee", employeeId, "department_id"))
                .as("the employee keeps the department it had")
                .isEqualTo(departmentOfA);
    }

    @Test
    @DisplayName("The message does not say whether the id exists elsewhere — a random id reads the same")
    void anUnknownIdAndAnotherTenantsIdReadAlike() {
        TenantContext.set(TENANT_A);

        EmployeeService.ValidationException foreign = catchThrowableOfType(
                () -> employeeService.create(request("A-001", departmentOfB, null, null)),
                EmployeeService.ValidationException.class);
        EmployeeService.ValidationException unknown = catchThrowableOfType(
                () -> employeeService.create(request("A-002", UUID.randomUUID(), null, null)),
                EmployeeService.ValidationException.class);

        assertThat(foreign.fieldErrors().keySet())
                .isEqualTo(unknown.fieldErrors().keySet());
        assertThat(withoutIds(foreign.fieldErrors().get("departmentId")))
                .as("telling the two apart would let a caller enumerate another tenant's ids")
                .isEqualTo(withoutIds(unknown.fieldErrors().get("departmentId")));
    }

    @Test
    @DisplayName("A deactivated department cannot be taken on, but an employee already holding one keeps it")
    void aDeactivatedDepartmentIsHiddenFromNewAssignmentsOnly() throws SQLException {
        TenantContext.set(TENANT_A);
        UUID employeeId = employeeService
                .create(request("A-001", departmentOfA, null, null))
                .id();
        UUID retired = OrgTestSchema.seedDepartment(TENANT_A, "OLD", "Retired unit", false);

        // Moving into the retired department is refused ...
        EmployeeService.ValidationException thrown = catchThrowableOfType(
                () -> employeeService.update(employeeId, request("A-001", retired, null, null)),
                EmployeeService.ValidationException.class);
        assertThat(thrown.fieldErrors().get("departmentId")).contains("inactive");

        // ... and so is hiring into it.
        assertThat(catchThrowableOfType(
                        () -> employeeService.create(request("A-002", retired, null, null)),
                        EmployeeService.ValidationException.class))
                .isNotNull();

        // But an employee who already holds a department that is retired afterwards can still be
        // updated, and keeps it. That is the whole point of deactivation over deletion.
        deactivate("department", departmentOfA);
        EmployeeResponse renamed = employeeService.update(employeeId, request("A-001", departmentOfA, null, null));
        assertThat(renamed.departmentId()).isEqualTo(departmentOfA);
    }

    @Test
    @DisplayName("Clearing the assignment is allowed — null means no department, not 'leave it'")
    void assignmentCanBeCleared() {
        TenantContext.set(TENANT_A);
        UUID employeeId = employeeService
                .create(request("A-001", departmentOfA, null, null))
                .id();

        EmployeeResponse cleared = employeeService.update(employeeId, request("A-001", null, null, null));

        assertThat(cleared.departmentId()).isNull();
    }

    /**
     * The demonstration spec section 7 is built on: the database does <em>not</em> refuse this.
     *
     * <p>As {@code app_user}, bound to tenant A, pointing tenant A's employee at tenant B's
     * department succeeds — one row updated. Row-level security hides tenant B's department from
     * every {@code SELECT} this connection can issue, and the foreign key still validates against it,
     * because referential-integrity checks run as the table owner with row security off.
     *
     * <p>So the isolation of an assignment is the service's job and nothing else's. If this test ever
     * starts failing because PostgreSQL refuses the write, that is good news and the assertion should
     * be inverted deliberately — not a reason to delete {@code EmployeeServiceImpl.resolve}.
     *
     * <p>Rolled back, so no cross-tenant row survives this method.
     */
    @Test
    @DisplayName("The foreign key alone does not stop it — which is why the service has to")
    void theForeignKeyAloneDoesNotStopIt() throws SQLException {
        UUID employeeId = OrgTestSchema.seedEmployee(TENANT_A, "A-009", "Asha", null);

        try (Connection conn = OrgTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            OrgTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE core.employee SET department_id = ? WHERE id = ?")) {
                ps.setObject(1, departmentOfB);
                ps.setObject(2, employeeId);
                assertThat(ps.executeUpdate())
                        .as("the database accepts a cross-tenant foreign key; only the service refuses it")
                        .isEqualTo(1);
            } finally {
                conn.rollback();
            }
        }

        assertThat(OrgTestSchema.readColumn("employee", employeeId, "department_id"))
                .as("rolled back, so nothing cross-tenant is left behind")
                .isNull();
    }

    /** Blanks any UUID in a message, so two messages can be compared for shape rather than content. */
    private static String withoutIds(String message) {
        return message.replaceAll("[0-9a-fA-F-]{36}", "ID");
    }

    private static EmployeeRequest request(
            String employeeNumber, UUID departmentId, UUID designationId, UUID workLocationId) {
        return new EmployeeRequest(
                employeeNumber,
                "Asha",
                null,
                "Rao",
                null,
                JOINED,
                null,
                null,
                null,
                null,
                null,
                departmentId,
                designationId,
                workLocationId);
    }

    @Test
    @DisplayName("A department held only by a SOFT-DELETED employee is still in use and cannot be deleted")
    void aSoftDeletedEmployeeStillHoldsItsDepartment() {
        // The count behind "delete refused while assigned" deliberately does NOT filter deleted
        // employees, and until now that was written down and nothing checked it. Adding
        // AndDeletedFalse would leave every unit test green — they stub the count — while turning
        // this 409 into a foreign-key violation from the database, because a soft-deleted row still
        // holds the key. This is the test that fails if someone "tidies" that finder.
        TenantContext.set(TENANT_A);
        UUID employeeId = employeeService
                .create(request("A-DEL", departmentOfA, null, null))
                .id();
        employeeService.delete(employeeId);

        assertThatThrownBy(() -> departmentService.delete(departmentOfA))
                .isInstanceOf(OrgMasterService.RecordInUseException.class);
    }

    private static void deactivate(String table, UUID id) throws SQLException {
        try (Connection conn = OrgTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE core." + table + " SET is_active = false WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }
}
