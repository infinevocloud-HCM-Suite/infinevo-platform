package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static com.infinevo.core.leave.LeaveTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
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
import org.springframework.test.context.ContextConfiguration;

/**
 * W-16.2, spec section 7 — {@code LeaveAllocationRlsIT}.
 *
 * <p>Verifies tenant isolation on {@code core.leave_allocation}:
 * <ul>
 *   <li>Tenant A cannot read Tenant B's allocations as {@code app_user}.
 *   <li>Row-level security policy {@code tenant_isolation} strictly enforces visibility at DB level.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class LeaveAllocationRlsIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeaveAllocationService allocationService;

    @Autowired
    private LeaveBalanceService balanceService;

    private UUID empAId;
    private UUID empBId;
    private UUID allocAId;
    private UUID allocBId;

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

        // Seed Tenant A
        TenantContext.set(TENANT_A);
        empAId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-A1", "Alice", "alice@acme.com");
        LeaveTypeResponse typeA = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Annual Leave", "AL", true, LeaveUnit.DAYS, true, LocalDate.of(2026, 1, 1), null));
        leaveTypeService.setPolicy(
                typeA.id(),
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
                        LocalDate.of(2026, 1, 1),
                        List.of()));
        LeaveAllocationResponse allocA = allocationService.createAllocation(new LeaveAllocationRequest(
                empAId,
                typeA.id(),
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(20)));
        allocAId = allocA.id();

        // Seed Tenant B
        TenantContext.set(TENANT_B);
        empBId = LeaveTestSchema.insertEmployee(TENANT_B, "EMP-B1", "Bob", "bob@globex.com");
        LeaveTypeResponse typeB = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Sick Leave", "SL", true, LeaveUnit.DAYS, false, LocalDate.of(2026, 1, 1), null));
        leaveTypeService.setPolicy(
                typeB.id(),
                new LeavePolicyRequest(
                        BigDecimal.valueOf(10),
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
                        LocalDate.of(2026, 1, 1),
                        List.of()));
        LeaveAllocationResponse allocB = allocationService.createAllocation(new LeaveAllocationRequest(
                empBId,
                typeB.id(),
                "2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(10)));
        allocBId = allocB.id();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Service level balances only see the bound tenant")
    void serviceBalancesOnlySeeBoundTenant() {
        TenantContext.set(TENANT_A);
        List<LeaveBalanceResponse> balancesA = balanceService.getBalancesForEmployee(empAId, LocalDate.of(2026, 6, 1));
        assertThat(balancesA).hasSize(1);
        assertThat(balancesA.get(0).leaveTypeCode()).isEqualTo("AL");

        // Requesting Tenant B employee from Tenant A context returns empty list
        List<LeaveBalanceResponse> crossBalances =
                balanceService.getBalancesForEmployee(empBId, LocalDate.of(2026, 6, 1));
        assertThat(crossBalances).isEmpty();
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant's allocations on raw app_user connection")
    void rlsHidesTheOtherTenantOnRawConnection() throws SQLException {
        assertThat(LeaveTestSchema.visibleLeaveAllocationCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveAllocationCount(TENANT_B)).isEqualTo(1);

        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.leave_allocation WHERE id = ?")) {
                ps.setObject(1, allocBId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must not see Tenant B's allocation")
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Raw UPDATE across tenant boundary affects 0 rows")
    void rawUpdateAcrossBoundaryAffectsZeroRows() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE core.leave_allocation SET entitlement_days = 99 WHERE id = ?")) {
                ps.setObject(1, allocBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("UPDATE across boundary must affect 0 rows")
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

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.leave_allocation WHERE id = ?")) {
                ps.setObject(1, allocBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("DELETE across boundary must affect 0 rows")
                        .isZero();
            }
        }

        // Verify Tenant B's allocation row is still there
        assertThat(LeaveTestSchema.visibleLeaveAllocationCount(TENANT_B)).isEqualTo(1);
    }
}
