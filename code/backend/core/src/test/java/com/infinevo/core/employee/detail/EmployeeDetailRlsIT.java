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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * W-13.2 — tenant A cannot read any of the five detail tables for tenant B's employee, as
 * {@code app_user} (spec section 7).
 *
 * <p>Asserted at two levels, because they fail independently.
 *
 * <ul>
 *   <li>Through the five services, which is how the application reaches the rows.
 *   <li>On a raw {@code app_user} connection, which is the {@code tenant_isolation} policy from
 *       {@code V015}-{@code V019} alone with no Java in the way.
 * </ul>
 *
 * <p><strong>The raw read is the one that matters.</strong> Every service call checks the employee
 * through a tenant-scoped finder first, so a test that only asked the service would see "not found"
 * and could not tell an enforced policy from a {@code WHERE} clause that happens to filter the same
 * row. Every cross-tenant assertion below therefore ends at a hard SQL read as {@code app_user}, and
 * the control — the same read with the owning tenant bound — proves the row was there to be hidden.
 * This is the method {@code EmployeeRlsIT} uses, applied to five tables.
 *
 * <p>Spec section 9 names "five scripts, and one forgets its RLS policy" as this ticket's main
 * hazard, so nothing here names a single table: the assertions walk
 * {@link EmployeeDetailTestSchema#SECTION_TABLES} and a table that was missed fails the loop.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
@WithMockUser(authorities = "core.employee.update")
class EmployeeDetailRlsIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeePersonalService personalService;

    @Autowired
    private EmployeeContactService contactService;

    @Autowired
    private EmployeeIdentificationService identificationService;

    @Autowired
    private EmployeeEmploymentService employmentService;

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

    /**
     * Leaves no row behind. All five tables have a foreign key to {@code core.employee}, which has one
     * to {@code core.tenant}, and other integration tests in this module clear both — a leftover here
     * would fail one of those with a constraint error that says nothing about either feature.
     */
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
        EmployeeDetailTestSchema.seedAllSections(TENANT_A, employeeOfA);
        EmployeeDetailTestSchema.seedAllSections(TENANT_B, employeeOfB);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Both sets of rows exist — the control for everything below")
    void bothSetsWereWritten() throws SQLException {
        for (String table : EmployeeDetailTestSchema.SECTION_TABLES) {
            assertThat(EmployeeDetailTestSchema.countSection(table, TENANT_A))
                    .as("core.%s for tenant A", table)
                    .isEqualTo(1);
            assertThat(EmployeeDetailTestSchema.countSection(table, TENANT_B))
                    .as("core.%s for tenant B", table)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant, on a raw app_user connection, on all five tables")
    void rlsHidesTheOtherTenantOnARawConnection() throws SQLException {
        for (String table : EmployeeDetailTestSchema.SECTION_TABLES) {
            assertThat(EmployeeDetailTestSchema.visibleRowCount(table, TENANT_A))
                    .as("app_user bound to tenant A sees only its own core.%s rows", table)
                    .isEqualTo(1);
            assertThat(EmployeeDetailTestSchema.visibleToAppUser(table, TENANT_A, employeeOfA))
                    .as("the control: tenant A's own core.%s row is visible", table)
                    .isEqualTo(1);
            assertThat(EmployeeDetailTestSchema.visibleToAppUser(table, TENANT_A, employeeOfB))
                    .as("app_user bound to tenant A must not see tenant B's core.%s row", table)
                    .isZero();
        }
    }

    @Test
    @DisplayName("With no tenant bound, app_user sees no detail row at all")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : EmployeeDetailTestSchema.SECTION_TABLES) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("core.%s with no tenant bound", table)
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot read any of tenant B's five sections through the services")
    void cannotReadAnySectionOfTheOtherTenant() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> personalService.get(employeeOfB))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        assertThatThrownBy(() -> contactService.get(employeeOfB))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        assertThatThrownBy(() -> identificationService.get(employeeOfB))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        assertThatThrownBy(() -> employmentService.get(employeeOfB))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        assertThatThrownBy(() -> bankService.get(employeeOfB))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A can read its own five sections — the control for the refusals above")
    void canReadItsOwnSections() {
        TenantContext.set(TENANT_A);

        assertThat(personalService.get(employeeOfA).nationality()).isEqualTo("Indian");
        assertThat(contactService.get(employeeOfA).city()).isEqualTo("Pune");
        assertThat(identificationService.get(employeeOfA).panNumber()).isEqualTo("ABCDE1234F");
        assertThat(employmentService.get(employeeOfA).payGrade()).isEqualTo("G5");
        assertThat(bankService.get(employeeOfA).paymentMode()).isEqualTo(PaymentMode.BANK_TRANSFER);
    }

    @Test
    @DisplayName("Tenant A cannot write over tenant B's sections, and the rows are untouched")
    void cannotWriteOverTheOtherTenantsSections() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> personalService.put(
                        employeeOfB, new EmployeePersonalRequest(null, null, "Hijacked", null, null, null, null)))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        assertThatThrownBy(() -> bankService.put(
                        employeeOfB,
                        new EmployeeBankRequest(
                                PaymentMode.BANK_TRANSFER,
                                "Mallory",
                                "Hijack Bank",
                                "HIJK0000001",
                                "9999999999",
                                BankAccountType.CURRENT)))
                .isInstanceOf(EmployeeDetailService.NotFoundException.class);

        // The hard read, as the owner: nothing changed, and no second row was created.
        assertThat(EmployeeDetailTestSchema.readColumn("employee_personal", employeeOfB, "nationality"))
                .isEqualTo("Indian");
        assertThat(EmployeeDetailTestSchema.readColumn("employee_bank", employeeOfB, "bank_name"))
                .isNull();
        assertThat(EmployeeDetailTestSchema.countSectionForEmployee("employee_bank", employeeOfB))
                .isEqualTo(1);
    }
}
