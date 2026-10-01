package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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

/**
 * W-16.2, spec section 7 &amp; 13 — {@code MidYearPolicyChangeIT}.
 *
 * <p>Verifies mid-year policy change behavior (Decision 2):
 * <ul>
 *   <li>Cutting entitlement mid-year recalculates every current-year allocation.
 *   <li>Reports who becomes over-drawn before applying the policy change.
 *   <li>Writes an audit row to {@code core.audit_log} carrying the previous entitlement.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
@org.springframework.test.context.ContextConfiguration(
        initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class MidYearPolicyChangeIT extends AbstractIntegrationTest {

    /**
     * The leave year in progress. A policy dated before it is refused and a closed year is never
     * rewritten, so a fixed year here would start failing the day that year ends.
     */
    private static final int YEAR = LocalDate.now(java.time.ZoneOffset.UTC).getYear();

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeaveAllocationService allocationService;

    @Autowired
    private LeaveAllocationRepository allocationRepository;

    @Autowired
    private LeavePolicyRepository policyRepository;

    @Autowired
    private LeaveAccrualService accrualService;

    private UUID employeeId;
    private UUID leaveTypeId;
    private UUID initialPolicyId;
    private UUID allocationId;

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
        employeeId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-01", "Diana", "diana@acme.com");

        LeaveTypeResponse type = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Annual Leave", "AL", true, LeaveUnit.DAYS, true, LocalDate.of(YEAR, 1, 1), null));
        leaveTypeId = type.id();

        LeavePolicyResponse policy = leaveTypeService.setPolicy(
                leaveTypeId,
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
        initialPolicyId = policy.id();

        LeaveAllocationResponse alloc = allocationService.createAllocation(new LeaveAllocationRequest(
                employeeId,
                leaveTypeId,
                String.valueOf(YEAR),
                LocalDate.of(YEAR, 1, 1),
                LocalDate.of(YEAR, 12, 31),
                BigDecimal.valueOf(20)));
        allocationId = alloc.id();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Cutting entitlement mid-year recalculates current allocations and writes audit row")
    void midYearPolicyChangeRecalculatesAndAudits() throws SQLException {
        TenantContext.set(TENANT_A);

        LocalDate midYearDate = LocalDate.of(YEAR, 7, 1);

        // Configure new policy with 5 annual days via configurePolicy
        LeavePolicyResponse newPolicyResponse = leaveTypeService.setPolicy(
                leaveTypeId,
                new LeavePolicyRequest(
                        BigDecimal.valueOf(5),
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
                        midYearDate,
                        List.of()));

        // Verify configurePolicy returns overdrawn preview
        assertThat(newPolicyResponse.overdrawnEmployees()).isNotNull();

        // Verify allocation automatically reflects the updated entitlement and new policy_id
        LeaveAllocation updatedAllocation =
                allocationRepository.findById(allocationId).orElseThrow();
        assertThat(updatedAllocation.getEntitlementDays()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(updatedAllocation.getPolicyId()).isEqualTo(newPolicyResponse.id());

        // Verify audit log has captured the UPDATE operation on leave_allocation
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT operation, entity_table, entity_id, old_values, new_values
                    FROM core.audit_log
                    WHERE entity_table = 'leave_allocation' AND entity_id = ?
                    ORDER BY occurred_at DESC
                    """)) {
                ps.setString(1, allocationId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("Audit row must exist for leave_allocation update")
                            .isTrue();
                    assertThat(rs.getString("operation")).isEqualTo("UPDATE");
                    assertThat(rs.getString("entity_table")).isEqualTo("leave_allocation");
                    String oldValues = rs.getString("old_values");
                    assertThat(oldValues).contains("20");
                }
            }
        }
    }

    @Test
    @DisplayName("Preview endpoint returns impact without modifying policy or allocations (F-7)")
    void previewPolicyChangeDoesNotMutatePolicyOrAllocations() {
        TenantContext.set(TENANT_A);
        LocalDate midYearDate = LocalDate.of(YEAR, 7, 1);

        List<OverdrawnEmployee> preview =
                leaveTypeService.previewPolicyChange(leaveTypeId, BigDecimal.valueOf(5), midYearDate);
        assertThat(preview).isNotNull();

        // Verify allocation in DB is STILL untouched (entitlement = 20, policyId = initialPolicyId)
        LeaveAllocation allocation = allocationRepository.findById(allocationId).orElseThrow();
        assertThat(allocation.getEntitlementDays()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(allocation.getPolicyId()).isEqualTo(initialPolicyId);
    }

    @Test
    @DisplayName("Mid-year policy change on accrual-based leave type does not overwrite entitlement days (F-2)")
    void accrualPolicyMidYearChangePreservesAccruedEntitlement() {
        TenantContext.set(TENANT_A);

        LeaveTypeResponse type = leaveTypeService.createLeaveType(new LeaveTypeRequest(
                "Earned Leave", "EL", true, LeaveUnit.DAYS, false, LocalDate.of(YEAR, 1, 1), null));
        UUID elTypeId = type.id();

        LeavePolicyResponse policy = leaveTypeService.setPolicy(
                elTypeId,
                new LeavePolicyRequest(
                        BigDecimal.valueOf(24),
                        true,
                        AccrualFrequency.MONTHLY,
                        BigDecimal.valueOf(2),
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

        // Create allocation with 6 days accrued so far
        LeaveAllocationResponse alloc = allocationService.createAllocation(new LeaveAllocationRequest(
                employeeId,
                elTypeId,
                String.valueOf(YEAR),
                LocalDate.of(YEAR, 1, 1),
                LocalDate.of(YEAR, 12, 31),
                BigDecimal.valueOf(6)));

        // Mid-year update reducing annual days to 12
        leaveTypeService.setPolicy(
                elTypeId,
                new LeavePolicyRequest(
                        BigDecimal.valueOf(12),
                        true,
                        AccrualFrequency.MONTHLY,
                        BigDecimal.valueOf(1),
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
                        LocalDate.of(YEAR, 7, 1),
                        List.of()));

        // Entitlement days must NOT be overwritten with 12
        LeaveAllocation updated = allocationRepository.findById(alloc.id()).orElseThrow();
        assertThat(updated.getEntitlementDays()).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    @DisplayName("Accrual to fixed: the new entitlement replaces the accrued days, it is not added on top")
    void accrualToFixedDoesNotCountLeaveTwice() {
        TenantContext.set(TENANT_A);

        UUID elTypeId = leaveTypeService
                .createLeaveType(new LeaveTypeRequest(
                        "Earned Leave", "EL", true, LeaveUnit.DAYS, false, LocalDate.of(YEAR, 1, 1), null))
                .id();
        leaveTypeService.setPolicy(elTypeId, policy(24, true, 2, LocalDate.of(YEAR, 1, 1)));

        UUID elAllocationId = allocationService
                .createAllocation(new LeaveAllocationRequest(
                        employeeId,
                        elTypeId,
                        String.valueOf(YEAR),
                        LocalDate.of(YEAR, 1, 1),
                        LocalDate.of(YEAR, 12, 31),
                        BigDecimal.ZERO))
                .id();
        LeaveAllocation accruing = allocationRepository.findById(elAllocationId).orElseThrow();
        accruing.setAccruedDays(BigDecimal.valueOf(6));
        allocationRepository.save(accruing);

        leaveTypeService.setPolicy(elTypeId, policy(24, false, 0, LocalDate.of(YEAR, 7, 1)));

        LeaveAllocation updated = allocationRepository.findById(elAllocationId).orElseThrow();
        assertThat(updated.getEntitlementDays()).isEqualByComparingTo(BigDecimal.valueOf(24));
        assertThat(updated.getAccruedDays())
                .as("24 held, not 24 + the 6 already accrued")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Fixed to accrual: the year's grant stands and nothing accrues on top of it this year")
    void fixedToAccrualDoesNotAccrueOnTopOfTheGrant() {
        TenantContext.set(TENANT_A);

        leaveTypeService.setPolicy(leaveTypeId, policy(24, true, 2, LocalDate.of(YEAR, 7, 1)));

        LeaveAllocation updated = allocationRepository.findById(allocationId).orElseThrow();
        assertThat(updated.getEntitlementDays()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(updated.getLastAccruedOn()).isEqualTo(LocalDate.of(YEAR, 12, 31));

        boolean accrued = accrualService.accrueAllocation(TENANT_A, updated, LocalDate.of(YEAR, 12, 1));

        assertThat(accrued).isFalse();
        LeaveAllocation afterSweep = allocationRepository.findById(allocationId).orElseThrow();
        assertThat(afterSweep.getAccruedDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Preview and apply agree across an accrual switch")
    void previewUsesTheNewPolicysAccrualFlag() {
        TenantContext.set(TENANT_A);
        LocalDate midYearDate = LocalDate.of(YEAR, 7, 1);

        // The allocation holds a fixed 20. Kept as fixed and cut to nothing, it would be reported as it
        // stands; switched to accrual, the 20 already granted stands, so nobody is over-drawn either way.
        // What must differ is the entitlement the preview works from.
        LeaveAllocation allocation = allocationRepository.findById(allocationId).orElseThrow();
        allocation.setCarriedForwardDays(BigDecimal.valueOf(-25));
        allocationRepository.save(allocation);

        List<OverdrawnEmployee> keptFixed =
                leaveTypeService.previewPolicyChange(TENANT_A, leaveTypeId, BigDecimal.valueOf(5), false, midYearDate);
        List<OverdrawnEmployee> toAccrual =
                leaveTypeService.previewPolicyChange(TENANT_A, leaveTypeId, BigDecimal.valueOf(5), true, midYearDate);

        // fixed: 5 - 25 = 20 short. accrual: the 20 granted stands, 20 - 25 = 5 short.
        assertThat(keptFixed).singleElement().satisfies(o -> assertThat(o.overdrawnDays())
                .isEqualByComparingTo(BigDecimal.valueOf(20)));
        assertThat(toAccrual).singleElement().satisfies(o -> assertThat(o.overdrawnDays())
                .isEqualByComparingTo(BigDecimal.valueOf(5)));
    }

    private static LeavePolicyRequest policy(int annualDays, boolean accrual, int monthlyUnits, LocalDate from) {
        return new LeavePolicyRequest(
                BigDecimal.valueOf(annualDays),
                accrual,
                accrual ? AccrualFrequency.MONTHLY : null,
                accrual ? BigDecimal.valueOf(monthlyUnits) : null,
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
                from,
                List.of());
    }
}
