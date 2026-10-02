package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static com.infinevo.core.leave.LeaveTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * W-16.1, spec section 7 — {@code LeaveTypeRlsIT}.
 *
 * <p>Verifies tenant isolation on {@code core.leave_type} and {@code core.leave_policy}:
 * <ul>
 *   <li>Tenant A cannot read or write Tenant B's types or policies as {@code app_user}.
 *   <li>Row-level security policy {@code tenant_isolation} strictly enforces visibility at DB level.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
@org.springframework.test.context.ContextConfiguration(
        initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class LeaveTypeRlsIT extends AbstractIntegrationTest {

    /**
     * The leave year in progress. A policy dated before it is refused and a closed year is never
     * rewritten, so a fixed year here would start failing the day that year ends.
     */
    private static final int YEAR = LocalDate.now(java.time.ZoneOffset.UTC).getYear();

    @Autowired
    private LeaveTypeService leaveTypeService;

    private UUID typeAId;
    private UUID typeBId;

    @BeforeAll
    static void applySchema() throws Exception {
        LeaveTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        LeaveTestSchema.seedTenants();
        LeaveTestSchema.clearAll();

        // Seed Tenant A leave type and policy
        TenantContext.set(TENANT_A);
        LeaveTypeResponse typeA = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Annual Leave", "AL", true, LeaveUnit.DAYS, true, LocalDate.of(YEAR, 1, 1), null));
        typeAId = typeA.id();
        leaveTypeService.setPolicy(
                typeAId,
                new LeavePolicyRequest(
                        BigDecimal.valueOf(20),
                        false,
                        null,
                        null,
                        false,
                        null,
                        false,
                        null,
                        null,
                        false,
                        null,
                        null,
                        false,
                        false,
                        ExceedBalanceMode.NO_LIMIT,
                        null,
                        false,
                        null,
                        null,
                        LocalDate.of(YEAR, 1, 1),
                        List.of()));

        // Seed Tenant B leave type and policy
        TenantContext.set(TENANT_B);
        LeaveTypeResponse typeB = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Sick Leave", "SL", true, LeaveUnit.DAYS, false, LocalDate.of(YEAR, 1, 1), null));
        typeBId = typeB.id();
        leaveTypeService.setPolicy(
                typeBId,
                new LeavePolicyRequest(
                        BigDecimal.valueOf(12),
                        false,
                        null,
                        null,
                        false,
                        null,
                        false,
                        null,
                        null,
                        false,
                        null,
                        null,
                        false,
                        false,
                        ExceedBalanceMode.MARK_AS_LOP,
                        null,
                        false,
                        null,
                        null,
                        LocalDate.of(YEAR, 1, 1),
                        List.of()));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Service list only sees types belonging to the bound tenant")
    void listSeesOnlyTheBoundTenant() {
        TenantContext.set(TENANT_A);
        List<LeaveTypeResponse> listA = leaveTypeService.getLeaveTypes(null);
        assertThat(listA).hasSize(1);
        assertThat(listA.get(0).id()).isEqualTo(typeAId);
        assertThat(listA.get(0).code()).isEqualTo("AL");

        TenantContext.set(TENANT_B);
        List<LeaveTypeResponse> listB = leaveTypeService.getLeaveTypes(null);
        assertThat(listB).hasSize(1);
        assertThat(listB.get(0).id()).isEqualTo(typeBId);
        assertThat(listB.get(0).code()).isEqualTo("SL");
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant's rows on raw app_user connection")
    void rlsHidesTheOtherTenantOnRawConnection() throws SQLException {
        assertThat(LeaveTestSchema.visibleLeaveTypeCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveTypeCount(TENANT_B)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeavePolicyCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeavePolicyCount(TENANT_B)).isEqualTo(1);

        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.leave_type WHERE id = ?")) {
                ps.setObject(1, typeBId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must not see Tenant B's leave type")
                            .isZero();
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.leave_policy WHERE leave_type_id = ?")) {
                ps.setObject(1, typeBId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must not see Tenant B's leave policy")
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Unbound app_user connection sees no rows")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_type")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_policy")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot update Tenant B's leave type or policy")
    void cannotUpdateAcrossTenantBoundary() throws SQLException {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> leaveTypeService.updateLeaveType(
                        typeBId,
                        new LeaveTypeRequest(
                                "Hijacked", "HJ", false, LeaveUnit.DAYS, false, LocalDate.of(YEAR, 1, 1), null)))
                .isInstanceOf(IllegalArgumentException.class);

        // Raw SQL UPDATE under app_user bound to Tenant A
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE core.leave_type SET name = 'Hijacked' WHERE id = ?")) {
                ps.setObject(1, typeBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("UPDATE across tenant boundary must affect 0 rows")
                        .isZero();
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE core.leave_policy SET annual_days = 99 WHERE leave_type_id = ?")) {
                ps.setObject(1, typeBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("UPDATE policy across tenant boundary must affect 0 rows")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Raw DELETE across tenant boundary affects 0 rows")
    void rawDeleteAcrossBoundaryAffectsZeroRows() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.leave_type WHERE id = ?")) {
                ps.setObject(1, typeBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("DELETE across tenant boundary must affect 0 rows")
                        .isZero();
            }
        }

        // Verify Tenant B's row is still there
        assertThat(LeaveTestSchema.visibleLeaveTypeCount(TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("Row cannot be written into another tenant as app_user")
    void rowCannotBeWrittenIntoAnotherTenant() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.leave_type (tenant_id, code, name, is_paid, unit, allow_half_day, valid_from, created_by, updated_by)
                    VALUES (?, 'SMUGGLED', 'Smuggled Leave', true, 'DAYS', false, '2026-01-01', 'test', 'test')
                    """)) {
                ps.setObject(1, TENANT_B);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            } finally {
                conn.rollback();
            }
        }
    }
}
