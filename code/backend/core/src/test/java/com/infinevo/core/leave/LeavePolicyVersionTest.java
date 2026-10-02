package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link LeaveTypeService} policy versioning (W-16.1, spec section 7).
 * Asserts:
 * <ul>
 *   <li>The policy in force on a given date is the latest with {@code effective_from <= date}</li>
 * </ul>
 */
class LeavePolicyVersionTest {

    private LeaveTypeRepository leaveTypeRepository;
    private LeavePolicyRepository leavePolicyRepository;
    private LeavePolicyEligibilityRepository eligibilityRepository;
    private LeaveTypeService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();

    private LeavePolicy policy2024;
    private LeavePolicy policy2025;
    private LeavePolicy policy2026Mid;

    @BeforeEach
    void setUp() {
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        leavePolicyRepository = mock(LeavePolicyRepository.class);
        eligibilityRepository = mock(LeavePolicyEligibilityRepository.class);

        service = new LeaveTypeServiceImpl(leaveTypeRepository, leavePolicyRepository, eligibilityRepository);

        policy2024 = createPolicy(UUID.randomUUID(), BigDecimal.valueOf(15), LocalDate.of(2024, 1, 1));
        policy2025 = createPolicy(UUID.randomUUID(), BigDecimal.valueOf(20), LocalDate.of(2025, 1, 1));
        policy2026Mid = createPolicy(UUID.randomUUID(), BigDecimal.valueOf(24), LocalDate.of(2026, 6, 1));

        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policy2024.getId()))
                .thenReturn(List.of());
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policy2025.getId()))
                .thenReturn(List.of());
        when(eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policy2026Mid.getId()))
                .thenReturn(List.of());
    }

    private LeavePolicy createPolicy(UUID id, BigDecimal annualDays, LocalDate effectiveFrom) {
        LeavePolicy p = new LeavePolicy();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setLeaveTypeId(leaveTypeId);
        p.setAnnualDays(annualDays);
        p.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
        p.setEffectiveFrom(effectiveFrom);
        return p;
    }

    @Test
    @DisplayName("the policy in force on a given date is the latest with effective_from <= date")
    void policyInForceIsLatestWithEffectiveFromBeforeOrEqualToDate() {
        // Query before any policy effective date
        LocalDate dateBeforeAny = LocalDate.of(2023, 12, 31);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, dateBeforeAny))
                .thenReturn(Optional.empty());

        assertThat(service.getEffectivePolicy(tenantId, leaveTypeId, dateBeforeAny))
                .isEmpty();

        // Query in 2024 -> returns 2024 policy (15 days)
        LocalDate date2024 = LocalDate.of(2024, 6, 15);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, date2024))
                .thenReturn(Optional.of(policy2024));

        Optional<LeavePolicyResponse> resp2024 = service.getEffectivePolicy(tenantId, leaveTypeId, date2024);
        assertThat(resp2024).isPresent();
        assertThat(resp2024.get().annualDays()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(resp2024.get().effectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));

        // Query in 2025 -> returns 2025 policy (20 days)
        LocalDate date2025 = LocalDate.of(2025, 9, 1);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, date2025))
                .thenReturn(Optional.of(policy2025));

        Optional<LeavePolicyResponse> resp2025 = service.getEffectivePolicy(tenantId, leaveTypeId, date2025);
        assertThat(resp2025).isPresent();
        assertThat(resp2025.get().annualDays()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(resp2025.get().effectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));

        // Query after 2026-06-01 -> returns mid-2026 policy (24 days)
        LocalDate date2026 = LocalDate.of(2026, 9, 28);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, date2026))
                .thenReturn(Optional.of(policy2026Mid));

        Optional<LeavePolicyResponse> resp2026 = service.getEffectivePolicy(tenantId, leaveTypeId, date2026);
        assertThat(resp2026).isPresent();
        assertThat(resp2026.get().annualDays()).isEqualByComparingTo(BigDecimal.valueOf(24));
        assertThat(resp2026.get().effectiveFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
    }
}
