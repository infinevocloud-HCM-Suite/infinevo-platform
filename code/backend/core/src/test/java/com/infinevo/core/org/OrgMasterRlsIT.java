package com.infinevo.core.org;

import static com.infinevo.core.org.OrgTestSchema.TENANT_A;
import static com.infinevo.core.org.OrgTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-14.1 — tenant A cannot read, change or delete tenant B's org masters, as {@code app_user} (spec
 * section 7).
 *
 * <p>Asserted at two levels, because they fail independently.
 *
 * <ul>
 *   <li>Through the services, which is how the application reaches the rows.
 *   <li>On a raw {@code app_user} connection, which is the {@code tenant_isolation} policy from
 *       {@code V011}-{@code V013} alone with no Java in the way.
 * </ul>
 *
 * <p><strong>The raw read is the one that matters.</strong> Every repository method here names the
 * tenant, so a test that only asked the service would see "not found" and could not tell an enforced
 * policy from a {@code WHERE} clause that happens to filter the same row. Each cross-tenant assertion
 * therefore ends at a hard SQL read as {@code app_user}, and the control — the same read with the
 * owning tenant bound — proves the row was there to be hidden.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class OrgMasterRlsIT extends AbstractIntegrationTest {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DesignationService designationService;

    @Autowired
    private WorkLocationService workLocationService;

    private UUID departmentOfA;
    private UUID departmentOfB;
    private UUID designationOfB;
    private UUID workLocationOfB;

    @BeforeAll
    static void applySchema() throws Exception {
        OrgTestSchema.apply();
    }

    /**
     * Leaves no row behind. All four tables have a foreign key to {@code core.tenant}, and other
     * integration tests in this module clear the tenant table — a leftover here would fail one of
     * those with a constraint error that says nothing about either feature.
     */
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
        departmentOfB = OrgTestSchema.seedDepartment(TENANT_B, "FIN", "Finanzas", true);
        designationOfB = OrgTestSchema.seedDesignation(TENANT_B, "MGR", "Gerente", true);
        workLocationOfB = OrgTestSchema.seedWorkLocation(TENANT_B, "HQ", "Sede", false);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Both tenants hold a department with the same code — the control for everything below")
    void bothRowsWereWritten() throws SQLException {
        assertThat(OrgTestSchema.countFor("department", TENANT_A)).isEqualTo(1);
        assertThat(OrgTestSchema.countFor("department", TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant's masters, on a raw app_user connection")
    void rlsHidesTheOtherTenantOnARawConnection() throws SQLException {
        assertThat(OrgTestSchema.visibleRowCount("department", TENANT_A)).isEqualTo(1);
        assertThat(OrgTestSchema.visibleToAppUser("department", TENANT_A, departmentOfA))
                .isTrue();

        assertThat(OrgTestSchema.visibleToAppUser("department", TENANT_A, departmentOfB))
                .as("app_user bound to tenant A must not see tenant B's department")
                .isFalse();
        assertThat(OrgTestSchema.visibleToAppUser("designation", TENANT_A, designationOfB))
                .as("app_user bound to tenant A must not see tenant B's designation")
                .isFalse();
        assertThat(OrgTestSchema.visibleToAppUser("work_location", TENANT_A, workLocationOfB))
                .as("app_user bound to tenant A must not see tenant B's work location")
                .isFalse();
    }

    @Test
    @DisplayName("With no tenant bound, app_user sees no master at all")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = OrgTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : new String[] {"department", "designation", "work_location"}) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1)).as(table).isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Bound to A, a list returns A's masters and no other")
    void listSeesOnlyTheBoundTenant() {
        TenantContext.set(TENANT_A);

        assertThat(departmentService.list(false)).hasSize(1).allSatisfy(d -> assertThat(d.tenantId())
                .isEqualTo(TENANT_A));
        assertThat(designationService.list(false)).isEmpty();
        assertThat(workLocationService.list(false)).isEmpty();
    }

    @Test
    @DisplayName("Tenant A cannot update tenant B's department, and the row is untouched")
    void cannotUpdate() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> departmentService.update(departmentOfB, new DepartmentRequest("HIJACK", "Mine", true)))
                .isInstanceOf(OrgMasterService.NotFoundException.class);

        assertThat(OrgTestSchema.readColumn("department", departmentOfB, "code"))
                .isEqualTo("FIN");
        assertThat(OrgTestSchema.readColumn("department", departmentOfB, "name"))
                .isEqualTo("Finanzas");
    }

    @Test
    @DisplayName("Tenant A cannot delete tenant B's department, designation or work location")
    void cannotDelete() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> departmentService.delete(departmentOfB))
                .isInstanceOf(OrgMasterService.NotFoundException.class);
        assertThatThrownBy(() -> designationService.delete(designationOfB))
                .isInstanceOf(OrgMasterService.NotFoundException.class);
        assertThatThrownBy(() -> workLocationService.delete(workLocationOfB))
                .isInstanceOf(OrgMasterService.NotFoundException.class);

        assertThat(OrgTestSchema.countFor("department", TENANT_B)).isEqualTo(1);
        assertThat(OrgTestSchema.countFor("designation", TENANT_B)).isEqualTo(1);
        assertThat(OrgTestSchema.countFor("work_location", TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("A raw UPDATE and a raw DELETE across the tenant boundary change nothing")
    void rawWritesAcrossTheBoundaryAffectNoRow() throws SQLException {
        assertThat(rowsAffectedAs(TENANT_A, "UPDATE core.department SET name = 'Mallory' WHERE id = ?", departmentOfB))
                .as("app_user bound to tenant A must not be able to update tenant B's department")
                .isZero();
        assertThat(rowsAffectedAs(TENANT_A, "DELETE FROM core.department WHERE id = ?", departmentOfB))
                .as("app_user bound to tenant A must not be able to delete tenant B's department")
                .isZero();

        assertThat(OrgTestSchema.countFor("department", TENANT_B)).isEqualTo(1);
        assertThat(OrgTestSchema.readColumn("department", departmentOfB, "name"))
                .isEqualTo("Finanzas");
    }

    @Test
    @DisplayName("A master cannot be written into another tenant — the policy serves as the INSERT check")
    void aRowCannotBeWrittenIntoAnotherTenant() throws SQLException {
        try (Connection conn = OrgTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            OrgTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.department (tenant_id, code, name, created_by, updated_by)
                    VALUES (?, 'SMUGGLED', 'Mallory', 'test', 'test')
                    """)) {
                ps.setObject(1, TENANT_B);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            } finally {
                conn.rollback();
            }
        }
        assertThat(OrgTestSchema.countFor("department", TENANT_B)).isEqualTo(1);
    }

    /** Runs one statement against a tenant B row as {@code app_user} with {@code tenantId} bound. */
    private int rowsAffectedAs(UUID tenantId, String sql, UUID id) throws SQLException {
        try (Connection conn = OrgTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            OrgTestSchema.bindTenant(conn, tenantId);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                int affected = ps.executeUpdate();
                conn.rollback();
                return affected;
            }
        }
    }
}
