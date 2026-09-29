package com.infinevo.core.lop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying effective_from date versioning of loss-of-pay policies (W-18.1 §7).
 */
class LopPolicyVersionTest {

    private LopPolicyRepository repository;
    private LopPolicyService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(LopPolicyRepository.class);
        service = new LopPolicyService(repository);
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The policy in force on a date is the latest version with effective_from <= date")
    void policyInForce_resolvesLatestVersionUpToDate() {
        LopPolicy v1 = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                true,
                true,
                LopRounding.HALF_UP_2,
                LocalDate.of(1900, 1, 1));

        LopPolicy v2 = new LopPolicy(
                tenantId,
                WorkingDayBasis.ORG_DAYS,
                new BigDecimal("26.00"),
                true,
                true,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 4, 1));

        // Mock repository returning v1 for dates before 2026-04-01
        when(repository.findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(tenantId), eq(LocalDate.of(2026, 3, 31))))
                .thenReturn(Optional.of(v1));

        // Mock repository returning v2 for dates on or after 2026-04-01
        when(repository.findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(tenantId), eq(LocalDate.of(2026, 4, 1))))
                .thenReturn(Optional.of(v2));
        when(repository.findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(tenantId), eq(LocalDate.of(2026, 7, 1))))
                .thenReturn(Optional.of(v2));

        LopPolicyResponse marchResponse = service.getPolicyInForce(LocalDate.of(2026, 3, 31));
        assertThat(marchResponse.workingDayBasis()).isEqualTo(WorkingDayBasis.ACTUAL_DAYS);
        assertThat(marchResponse.effectiveFrom()).isEqualTo(LocalDate.of(1900, 1, 1));

        LopPolicyResponse aprilResponse = service.getPolicyInForce(LocalDate.of(2026, 4, 1));
        assertThat(aprilResponse.workingDayBasis()).isEqualTo(WorkingDayBasis.ORG_DAYS);
        assertThat(aprilResponse.configuredDaysPerMonth()).isEqualByComparingTo(new BigDecimal("26.00"));
        assertThat(aprilResponse.effectiveFrom()).isEqualTo(LocalDate.of(2026, 4, 1));

        LopPolicyResponse julyResponse = service.getPolicyInForce(LocalDate.of(2026, 7, 1));
        assertThat(julyResponse.workingDayBasis()).isEqualTo(WorkingDayBasis.ORG_DAYS);
    }

    @Test
    @DisplayName("Throws NoLopPolicyException when no policy has effective_from <= date")
    void dateBeforeAllVersions_throwsNoLopPolicyException() {
        when(repository.findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(eq(tenantId), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPolicyInForce(LocalDate.of(1899, 12, 31)))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("No loss-of-pay policy in force");
    }

    @Test
    @DisplayName("Changing policy in April creates a new version; March policy remains intact")
    void savePolicy_createsNewVersion_andDoesNotOverwriteMarchPolicy() {
        LocalDate marchDate = LocalDate.of(2026, 3, 1);
        LocalDate aprilDate = LocalDate.of(2026, 4, 1);

        LopPolicy marchPolicy =
                new LopPolicy(tenantId, WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, marchDate);

        // April has no version yet
        when(repository.findByTenantIdAndEffectiveFrom(eq(tenantId), eq(aprilDate)))
                .thenReturn(Optional.empty());
        when(repository.save(any(LopPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        LopPolicyRequest aprilRequest =
                new LopPolicyRequest(WorkingDayBasis.ACTUAL_DAYS, null, true, true, LopRounding.HALF_UP_2, aprilDate);

        LopPolicyResponse aprilResponse = service.savePolicy(aprilRequest);

        // April response reflects the new policy version
        assertThat(aprilResponse.workingDayBasis()).isEqualTo(WorkingDayBasis.ACTUAL_DAYS);
        assertThat(aprilResponse.effectiveFrom()).isEqualTo(aprilDate);

        // March policy entity was never overwritten or modified
        assertThat(marchPolicy.getWorkingDayBasis()).isEqualTo(WorkingDayBasis.FIXED_30);
        assertThat(marchPolicy.getEffectiveFrom()).isEqualTo(marchDate);
    }

    @Test
    @DisplayName("Attempting to overwrite an existing policy version in place throws IllegalStateException")
    void savePolicy_sameDateDifferentSettings_throwsIllegalStateException() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        LopPolicy existingMarch =
                new LopPolicy(tenantId, WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, date);

        when(repository.findByTenantIdAndEffectiveFrom(eq(tenantId), eq(date))).thenReturn(Optional.of(existingMarch));

        LopPolicyRequest overwriteAttempt =
                new LopPolicyRequest(WorkingDayBasis.ACTUAL_DAYS, null, true, true, LopRounding.HALF_UP_2, date);

        assertThatThrownBy(() -> service.savePolicy(overwriteAttempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot edit loss-of-pay policy in place")
                .hasMessageContaining("Policy versions are immutable");
    }

    @Test
    @DisplayName("Saving identical settings for an existing effective date is idempotent")
    void savePolicy_sameDateSameSettings_isIdempotent() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        LopPolicy existingMarch =
                new LopPolicy(tenantId, WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, date);

        when(repository.findByTenantIdAndEffectiveFrom(eq(tenantId), eq(date))).thenReturn(Optional.of(existingMarch));

        LopPolicyRequest sameRequest =
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, date);

        LopPolicyResponse response = service.savePolicy(sameRequest);
        assertThat(response.workingDayBasis()).isEqualTo(WorkingDayBasis.FIXED_30);
        assertThat(response.effectiveFrom()).isEqualTo(date);
    }

    @Test
    @DisplayName("When effectiveFrom is null, it defaults to today's date")
    void savePolicy_nullEffectiveFrom_defaultsToToday() {
        LocalDate today = LocalDate.now();
        when(repository.findByTenantIdAndEffectiveFrom(eq(tenantId), eq(today))).thenReturn(Optional.empty());
        when(repository.save(any(LopPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        LopPolicyRequest request =
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, null);

        LopPolicyResponse response = service.savePolicy(request);
        assertThat(response.effectiveFrom()).isEqualTo(today);
    }
}
