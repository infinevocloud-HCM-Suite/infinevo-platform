package com.infinevo.core.employee.detail;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * W-13.2 — a detail row cannot reference an employee in another tenant (spec section 7).
 *
 * <p><strong>This matters more than it looks, and the reason is in the first test.</strong> The
 * {@code employee_id} column on all five tables carries a foreign key to {@code core.employee}, and a
 * foreign key alone does <em>not</em> stop a cross-tenant reference: PostgreSQL runs
 * referential-integrity checks as the table owner, the owner here is {@code migration_user}, and that
 * role bypasses row-level security. So the database accepts a row in tenant A naming tenant B's
 * employee — {@link #theDatabaseAloneAcceptsACrossTenantReference()} demonstrates exactly that on a
 * raw {@code app_user} connection, and it is the reason the rest of this class exists.
 *
 * <p>{@link AbstractEmployeeDetailServiceImpl} is what refuses it: every call resolves the employee
 * through {@code EmployeeRepository.findByIdAndTenantIdAndDeletedFalse} in the bound tenant before it
 * touches a section. Every assertion below ends at a hard read as {@code app_user} or as the owner,
 * because a service-level "not found" looks identical whether the check ran or not.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
@WithMockUser(authorities = "core.employee.update")
class EmployeeDetailCascadeIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeePersonalService personalService;

    @Autowired
    private EmployeeBankService bankService;

    @MockBean
    private PermissionService permissionService;

    private UUID employeeOfA;
    private UUID employeeOfB;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeDetailTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeDetailTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        BDDMockito.given(permissionService.holds("core.employee.update")).willReturn(true);
        EmployeeTestSchema.seedTenants();
        EmployeeDetailTestSchema.clearAll();
        employeeOfA = EmployeeTestSchema.seedEmployee(TENANT_A, "A-001", "Asha");
        employeeOfB = EmployeeTestSchema.seedEmployee(TENANT_B, "B-001", "Bharat");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The database alone accepts a cross-tenant reference — which is why the service must not")
    void theDatabaseAloneAcceptsACrossTenantReference() throws SQLException {
        // As app_user, bound to tenant A, inserting a row that claims tenant A but names tenant B's
        // employee. RLS passes: the policy compares tenant_id, and tenant_id is A. The foreign key
        // passes: it is checked as migration_user, which bypasses RLS and can see tenant B's row.
        // Rolled back immediately - this asserts what the database permits, it does not leave it.
        assertThat(EmployeeDetailTestSchema.tryInsertAsAppUser("employee_personal", TENANT_A, employeeOfB))
                .as("the foreign key does not stop a cross-tenant reference: "
                        + "migration_user owns the table and bypasses row-level security")
                .isTrue();
    }

    @Test
    @DisplayName("The service refuses to write a section against another tenant's employee")
    void theServiceRefusesACrossTenantWrite() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> personalService.put(
                        employeeOfB, new EmployeePersonalRequest(null, null, "Hijacked", null, null, null, null)))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        assertThatThrownBy(() -> bankService.put(
                        employeeOfB, new EmployeeBankRequest(PaymentMode.CASH, null, null, null, null, null)))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);

        // The hard reads. As the owner, so row-level security cannot hide a row that was written:
        // asserting only that tenant A cannot see it would pass even if the write had gone through.
        for (String table : EmployeeDetailTestSchema.SECTION_TABLES) {
            assertThat(EmployeeDetailTestSchema.countSectionForEmployee(table, employeeOfB))
                    .as("no core.%s row may exist for tenant B's employee", table)
                    .isZero();
            assertThat(EmployeeDetailTestSchema.countSection(table, TENANT_A))
                    .as("and no core.%s row was written under tenant A either", table)
                    .isZero();
        }
    }

    @Test
    @DisplayName("The same write against its own employee succeeds — the control for the refusal above")
    void theSameWriteAgainstItsOwnEmployeeSucceeds() throws SQLException {
        TenantContext.set(TENANT_A);

        personalService.put(employeeOfA, new EmployeePersonalRequest(null, null, "Indian", null, null, null, null));

        assertThat(EmployeeDetailTestSchema.countSectionForEmployee("employee_personal", employeeOfA))
                .isEqualTo(1);
        assertThat(EmployeeDetailTestSchema.readColumn("employee_personal", employeeOfA, "tenant_id"))
                .isEqualTo(TENANT_A);
        // As app_user with tenant A bound, through the policy rather than around it.
        assertThat(EmployeeDetailTestSchema.visibleToAppUser("employee_personal", TENANT_A, employeeOfA))
                .isEqualTo(1);
        assertThat(EmployeeDetailTestSchema.visibleToAppUser("employee_personal", TENANT_B, employeeOfA))
                .as("and tenant B cannot see it")
                .isZero();
    }

    @Test
    @DisplayName("A second PUT never writes a second row, against real Postgres")
    void secondPutNeverWritesASecondRow() throws SQLException {
        TenantContext.set(TENANT_A);

        personalService.put(employeeOfA, new EmployeePersonalRequest(null, null, "Indian", null, null, null, null));
        personalService.put(employeeOfA, new EmployeePersonalRequest(null, null, "British", null, null, null, null));

        // The unique index on (tenant_id, employee_id) is the backstop; this asserts the service never
        // reaches it, and that the row that survives is the second one.
        assertThat(EmployeeDetailTestSchema.countSectionForEmployee("employee_personal", employeeOfA))
                .isEqualTo(1);
        assertThat(EmployeeDetailTestSchema.readColumn("employee_personal", employeeOfA, "nationality"))
                .isEqualTo("British");
    }
}
