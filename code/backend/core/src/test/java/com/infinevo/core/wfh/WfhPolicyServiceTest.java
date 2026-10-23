package com.infinevo.core.wfh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WfhPolicyServiceTest {

    private WfhPolicyRepository policyRepository;
    private WfhPolicyServiceImpl policyService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policyRepository = mock(WfhPolicyRepository.class);
        policyService = new WfhPolicyServiceImpl(policyRepository);
    }

    @Test
    @DisplayName("Creating policy with duplicate code throws IllegalArgumentException")
    void createPolicyDuplicateCodeThrows() {
        when(policyRepository.existsByTenantIdAndCode(tenantId, "STANDARD")).thenReturn(true);

        CreateWfhPolicyRequest request = new CreateWfhPolicyRequest(
                "STANDARD",
                "Standard Policy",
                new BigDecimal("12.00"),
                new BigDecimal("2.00"),
                true,
                1,
                3,
                true,
                false,
                LocalDate.of(2026, 1, 1),
                true);

        assertThatThrownBy(() -> policyService.createPolicy(tenantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WFH policy code already exists: STANDARD");
    }

    @Test
    @DisplayName("Creating valid policy persists and returns DTO response")
    void createPolicySuccess() {
        when(policyRepository.existsByTenantIdAndCode(tenantId, "REMOTE2026")).thenReturn(false);
        when(policyRepository.save(any(WfhPolicy.class))).thenAnswer(inv -> {
            WfhPolicy p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreateWfhPolicyRequest request = new CreateWfhPolicyRequest(
                "REMOTE2026",
                "Remote Work 2026",
                new BigDecimal("12.00"),
                new BigDecimal("2.00"),
                true,
                1,
                3,
                true,
                false,
                LocalDate.of(2026, 1, 1),
                true);

        WfhPolicyResponse response = policyService.createPolicy(tenantId, request);
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("REMOTE2026");
        assertThat(response.annualDays()).isEqualByComparingTo("12.00");
        assertThat(response.monthlyLimitDays()).isEqualByComparingTo("2.00");
        assertThat(response.allowHalfDay()).isTrue();
    }

    @Test
    @DisplayName("getOrCreateDefaultPolicy returns existing policy when present")
    void getOrCreateDefaultPolicyReturnsExisting() {
        WfhPolicy existing = new WfhPolicy();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setCode("EXISTING");
        existing.setName("Existing Policy");
        existing.setAnnualDays(new BigDecimal("15.00"));

        when(policyRepository.findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(tenantId))
                .thenReturn(Optional.of(existing));

        WfhPolicy result = policyService.getOrCreateDefaultPolicy(tenantId);
        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getAnnualDays()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("getOrCreateDefaultPolicy creates 12-day standard default when none exists")
    void getOrCreateDefaultPolicyCreatesDefault() {
        when(policyRepository.findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(tenantId))
                .thenReturn(Optional.empty());
        when(policyRepository.save(any(WfhPolicy.class))).thenAnswer(inv -> {
            WfhPolicy p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        WfhPolicy result = policyService.getOrCreateDefaultPolicy(tenantId);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("DEFAULT_WFH");
        assertThat(result.getAnnualDays()).isEqualByComparingTo("12.00");
        assertThat(result.getMonthlyLimitDays()).isEqualByComparingTo("2.00");
        assertThat(result.getMaxConsecutiveDays()).isEqualTo(3);
        verify(policyRepository).save(any(WfhPolicy.class));
    }
}
