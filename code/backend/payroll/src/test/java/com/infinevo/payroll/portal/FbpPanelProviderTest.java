package com.infinevo.payroll.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-27.2, BUG-D5-025 — the FBP portal panel descriptor. */
class FbpPanelProviderTest {

    @Test
    @DisplayName("FbpPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        FbpPanelProvider provider = new FbpPanelProvider();
        assertThat(provider.code()).isEqualTo("fbp");
        assertThat(provider.module()).isEqualTo(PlatformModule.PAYROLL);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("fbp");
        assertThat(desc.title()).isEqualTo("Flexible Benefits");
        assertThat(desc.displayOrder()).isEqualTo(8);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/me/fbp-declaration");
        assertThat(desc.requiredAction()).isEqualTo("payroll.fbp.read_own");
    }
}
