package com.infinevo.hrms.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewsPanelProviderTest {

    @Test
    @DisplayName("ReviewsPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        ReviewsPanelProvider provider = new ReviewsPanelProvider();
        assertThat(provider.code()).isEqualTo("reviews");
        assertThat(provider.module()).isEqualTo(PlatformModule.HRMS);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("reviews");
        assertThat(desc.title()).isEqualTo("Reviews");
        assertThat(desc.displayOrder()).isEqualTo(10);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/performance/reviews/my");
        assertThat(desc.requiredAction()).isEqualTo("hrms.performance.read_own");
    }
}
