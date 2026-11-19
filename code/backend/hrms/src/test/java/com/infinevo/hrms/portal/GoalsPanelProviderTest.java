package com.infinevo.hrms.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoalsPanelProviderTest {

    @Test
    @DisplayName("GoalsPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        GoalsPanelProvider provider = new GoalsPanelProvider();
        assertThat(provider.code()).isEqualTo("goals");
        assertThat(provider.module()).isEqualTo(PlatformModule.HRMS);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("goals");
        assertThat(desc.title()).isEqualTo("Goals & OKRs");
        assertThat(desc.displayOrder()).isEqualTo(11);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/hrms/goals/me");
        assertThat(desc.requiredAction()).isEqualTo("hrms.goal.read_own");
    }
}
