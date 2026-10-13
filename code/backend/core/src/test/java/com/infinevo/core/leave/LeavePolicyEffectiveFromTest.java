package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * D-55: a policy saved without an effective date starts from the current leave year.
 *
 * <p>The drawer used to fail with {@code VALIDATION_FAILED: effectiveFrom cannot be null} because the
 * validator refused a missing date before the service could default it. The service now defaults it
 * to the first day of the tenant's current leave year (April for a tenant with no setting, as
 * {@link LeaveDateUtils#getTenantLeaveYearStartMonth} answers without a database), and the validator
 * leaves the field alone. A date before that start is still refused (W-16.1 spec section 6).
 */
class LeavePolicyEffectiveFromTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();

    private LeavePolicyRepository leavePolicyRepository;
    private LeaveTypeService service;

    @BeforeEach
    void setUp() {
        LeaveTypeRepository leaveTypeRepository = mock(LeaveTypeRepository.class);
        leavePolicyRepository = mock(LeavePolicyRepository.class);
        LeavePolicyEligibilityRepository eligibilityRepository = mock(LeavePolicyEligibilityRepository.class);

        LeaveType type = new LeaveType();
        type.setId(leaveTypeId);
        type.setTenantId(tenantId);
        type.setCode("AL");
        type.setName("Annual Leave");
        type.setValidFrom(LocalDate.of(2020, 1, 1));
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leavePolicyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> {
            LeavePolicy saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        service = new LeaveTypeServiceImpl(leaveTypeRepository, leavePolicyRepository, eligibilityRepository);
    }

    private static LeavePolicyRequest request(LocalDate effectiveFrom) {
        return new LeavePolicyRequest(
                BigDecimal.valueOf(12),
                false,
                null,
                null,
                true,
                ResetFrequency.YEARLY,
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
                effectiveFrom,
                List.of());
    }

    private static LocalDate currentLeaveYearStart() {
        return LeaveDateUtils.getCurrentLeaveYearStartDate(
                LocalDate.now(ZoneOffset.UTC), LeaveDateUtils.getTenantLeaveYearStartMonth(null, null));
    }

    @Test
    @DisplayName("No effective date: the policy starts on the first day of the current leave year")
    void missingEffectiveFromDefaultsToCurrentLeaveYearStart() {
        LeavePolicyResponse response = service.configurePolicy(tenantId, leaveTypeId, request(null));

        ArgumentCaptor<LeavePolicy> saved = ArgumentCaptor.forClass(LeavePolicy.class);
        org.mockito.Mockito.verify(leavePolicyRepository).save(saved.capture());
        assertThat(saved.getValue().getEffectiveFrom()).isEqualTo(currentLeaveYearStart());
        assertThat(response.effectiveFrom()).isEqualTo(currentLeaveYearStart());
    }

    @Test
    @DisplayName(
            "No effective date with a mid-year version in force: the new version is dated the same day, so it wins")
    void missingEffectiveFromNeverFallsBehindTheVersionInForce() {
        LocalDate midYear = currentLeaveYearStart().plusMonths(2);
        LeavePolicy inForce = new LeavePolicy();
        inForce.setId(UUID.randomUUID());
        inForce.setTenantId(tenantId);
        inForce.setLeaveTypeId(leaveTypeId);
        inForce.setAnnualDays(BigDecimal.TEN);
        inForce.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
        inForce.setEffectiveFrom(midYear);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(UUID.class), any(UUID.class), any(LocalDate.class)))
                .thenReturn(Optional.of(inForce));

        LeavePolicyResponse response = service.configurePolicy(tenantId, leaveTypeId, request(null));

        assertThat(response.effectiveFrom()).isEqualTo(midYear);
    }

    @Test
    @DisplayName("An effective date on or after the leave year start is kept as given")
    void explicitEffectiveFromIsKept() {
        LocalDate chosen = currentLeaveYearStart().plusMonths(2);

        LeavePolicyResponse response = service.configurePolicy(tenantId, leaveTypeId, request(chosen));

        assertThat(response.effectiveFrom()).isEqualTo(chosen);
    }

    @Test
    @DisplayName("An effective date before the current leave year is refused, not silently moved")
    void effectiveFromBeforeLeaveYearIsRefused() {
        LocalDate tooEarly = currentLeaveYearStart().minusDays(1);

        assertThatThrownBy(() -> service.configurePolicy(tenantId, leaveTypeId, request(tooEarly)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be before the current leave year start date");
    }
}
