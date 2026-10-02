package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
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
 * W-16.2, spec section 7 — {@code LeaveCarryForwardExpiryIT}.
 *
 * <p>Verifies carry-forward expiration enforcement:
 * <ul>
 *   <li>Carried-forward days count towards remaining balance on or before {@code carry_forward_expires_on}.
 *   <li>Carried-forward days stop counting after {@code carry_forward_expires_on}.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
@org.springframework.test.context.ContextConfiguration(
        initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class LeaveCarryForwardExpiryIT extends AbstractIntegrationTest {

    /**
     * The leave year in progress. A policy dated before it is refused and a closed year is never
     * rewritten, so a fixed year here would start failing the day that year ends.
     */
    private static final int YEAR = LocalDate.now(java.time.ZoneOffset.UTC).getYear();

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeaveAllocationRepository allocationRepository;

    @Autowired
    private LeaveBalanceService balanceService;

    private UUID employeeId;
    private UUID leaveTypeId;

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
        employeeId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-01", "Charlie", "charlie@acme.com");

        LeaveTypeResponse type = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Earned Leave", "EL", true, LeaveUnit.DAYS, true, LocalDate.of(YEAR, 1, 1), null));
        leaveTypeId = type.id();

        LeavePolicyResponse policy = leaveTypeService.setPolicy(
                leaveTypeId,
                new LeavePolicyRequest(
                        BigDecimal.valueOf(18),
                        false,
                        null,
                        null,
                        false,
                        null,
                        true,
                        BigDecimal.valueOf(10),
                        3, // expires after 3 months
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

        // Create allocation with 18 entitlement and 5 carried forward expiring 2026-03-31
        LeaveAllocation allocation = new LeaveAllocation(
                TENANT_A,
                employeeId,
                leaveTypeId,
                String.valueOf(YEAR),
                LocalDate.of(YEAR, 1, 1),
                LocalDate.of(YEAR, 12, 31),
                BigDecimal.valueOf(18),
                BigDecimal.ZERO,
                BigDecimal.valueOf(5),
                LocalDate.of(YEAR, 3, 31),
                BigDecimal.ONE,
                policy.id());
        allocationRepository.save(allocation);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Carried-forward days stop counting after carry_forward_expires_on")
    void carriedForwardExpiresAfterExpiryDate() {
        TenantContext.set(TENANT_A);

        // Before expiry: 2026-03-31 (on expiry date)
        LeaveBalanceResponse beforeExpiry = balanceService
                .getBalance(employeeId, leaveTypeId, LocalDate.of(YEAR, 3, 31))
                .orElseThrow();

        assertThat(beforeExpiry.carriedForwardDays()).isEqualByComparingTo(BigDecimal.valueOf(5));
        // Remaining = 18 + 0 + 5 - 0 = 23
        assertThat(beforeExpiry.remainingDays()).isEqualByComparingTo(BigDecimal.valueOf(23));

        // After expiry: 2026-04-01
        LeaveBalanceResponse afterExpiry = balanceService
                .getBalance(employeeId, leaveTypeId, LocalDate.of(YEAR, 4, 1))
                .orElseThrow();

        assertThat(afterExpiry.carriedForwardDays()).isEqualByComparingTo(BigDecimal.ZERO);
        // Remaining = 18 + 0 + 0 - 0 = 18
        assertThat(afterExpiry.remainingDays()).isEqualByComparingTo(BigDecimal.valueOf(18));
    }
}
