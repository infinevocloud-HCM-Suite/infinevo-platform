package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.notification.ReminderRule;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProofPendingAudienceResolver} (W-34.3 spec section 7).
 */
@ExtendWith(MockitoExtension.class)
class ProofPendingAudienceResolverTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private ProofPendingQuery proofPendingQuery;

    @Mock
    private ReminderRule reminderRule;

    private ProofPendingAudienceResolver resolver;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-15T04:30:00Z"), TaxDeclarationRules.ZONE);
        resolver = new ProofPendingAudienceResolver(proofPendingQuery, clock);
    }

    @Test
    @DisplayName("audience() returns POI_PENDING")
    void audienceIsPoiPending() {
        assertThat(resolver.audience()).isEqualTo("POI_PENDING");
    }

    @Test
    @DisplayName("resolve resolves FY from clock and delegates to ProofPendingQuery")
    void resolveDelegatesToQuery() {
        UUID emp1 = UUID.randomUUID();
        UUID emp2 = UUID.randomUUID();
        when(proofPendingQuery.findPendingEmployeeIds(TENANT_ID, "2026-2027")).thenReturn(List.of(emp1, emp2));

        List<UUID> result = resolver.resolve(reminderRule, TENANT_ID);

        assertThat(result).containsExactly(emp1, emp2);
        verify(proofPendingQuery).findPendingEmployeeIds(TENANT_ID, "2026-2027");
    }
}
