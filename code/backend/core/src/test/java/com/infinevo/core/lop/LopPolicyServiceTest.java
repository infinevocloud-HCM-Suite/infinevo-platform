package com.infinevo.core.lop;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the one {@link LopPolicyService} path an integration test cannot reach on demand:
 * two saves racing past the existence check (W-18.1 F-3).
 */
class LopPolicyServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private LopPolicyRepository repository;
    private LopPolicyService service;

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
    @DisplayName("F-3: a unique-key violation on save becomes a conflict (IllegalStateException, 409)")
    void uniqueKeyViolation_becomesConflict() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        when(repository.findByTenantIdAndEffectiveFrom(tenantId, date)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(LopPolicy.class)))
                .thenThrow(new DataIntegrityViolationException("uk_lop_policy_tenant_effective_from"));

        assertThatThrownBy(() -> service.savePolicy(
                        new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, date)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists for effective date 2026-03-01")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
