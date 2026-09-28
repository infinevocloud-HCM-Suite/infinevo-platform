package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * W-16.1, spec section 7 — {@code LeavePolicyEligibilityIT}.
 *
 * <p>Verifies constraints on {@code core.leave_policy_eligibility}:
 * <ul>
 *   <li>A second row for the same {@code (policy, dimension, value_id)} is refused by the unique constraint.
 *   <li>A {@code dimension} outside the four is refused by the {@code CHECK} constraint.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
class LeavePolicyEligibilityIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveTypeService leaveTypeService;

    private UUID policyId;

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

        TenantContext.set(TENANT_A);
        LeaveTypeResponse type = leaveTypeService.createLeaveType(new LeaveTypeRequest(
                "Maternity Leave", "ML", true, LeaveUnit.DAYS, false, LocalDate.of(2026, 1, 1), null));

        LeavePolicyResponse policy = leaveTypeService.setPolicy(
                type.id(),
                new LeavePolicyRequest(
                        BigDecimal.valueOf(90),
                        false,
                        null,
                        null,
                        false,
                        null,
                        false,
                        null,
                        null,
                        true,
                        null,
                        null,
                        false,
                        false,
                        ExceedBalanceMode.NO_LIMIT,
                        null,
                        false,
                        null,
                        "FEMALE",
                        LocalDate.of(2026, 1, 1),
                        List.of()));
        policyId = policy.id();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Duplicate (policy, dimension, value_id) is refused by unique constraint")
    void duplicateRowIsRefusedByUniqueConstraint() throws SQLException {
        UUID valueId = UUID.randomUUID();

        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            // First insert succeeds
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.leave_policy_eligibility (tenant_id, policy_id, dimension, value_id, created_by, updated_by)
                    VALUES (?, ?, 'department', ?, 'test', 'test')
                    """)) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, policyId);
                ps.setObject(3, valueId);
                ps.executeUpdate();
            }

            // Second insert with exact same (policy, dimension, value_id) fails
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.leave_policy_eligibility (tenant_id, policy_id, dimension, value_id, created_by, updated_by)
                    VALUES (?, ?, 'department', ?, 'test', 'test')
                    """)) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, policyId);
                ps.setObject(3, valueId);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uk_leave_policy_eligibility");
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Dimension outside the allowed four is refused by CHECK constraint")
    void dimensionOutsideVocabularyIsRefusedByCheck() throws SQLException {
        UUID valueId = UUID.randomUUID();

        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.leave_policy_eligibility (tenant_id, policy_id, dimension, value_id, created_by, updated_by)
                    VALUES (?, ?, 'invalid_dimension', ?, 'test', 'test')
                    """)) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, policyId);
                ps.setObject(3, valueId);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_leave_policy_eligibility_dimension");
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("All four allowed dimensions satisfy the database check constraint")
    void allFourDimensionsAllowedInDatabase() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            String[] allowedDims = new String[] {"department", "designation", "work_location", "employment_type"};
            for (String dim : allowedDims) {
                try (PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.leave_policy_eligibility (tenant_id, policy_id, dimension, value_id, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'test', 'test')
                        """)) {
                    ps.setObject(1, TENANT_A);
                    ps.setObject(2, policyId);
                    ps.setString(3, dim);
                    ps.setObject(4, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }
            conn.rollback();
        }
    }
}
