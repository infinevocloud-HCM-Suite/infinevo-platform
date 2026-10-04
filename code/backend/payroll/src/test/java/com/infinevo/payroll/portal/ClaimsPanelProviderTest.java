package com.infinevo.payroll.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-47.4 §4 — the claims panel's descriptor. */
class ClaimsPanelProviderTest {

    @Test
    @DisplayName("ClaimsPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        ClaimsPanelProvider provider = new ClaimsPanelProvider();
        assertThat(provider.code()).isEqualTo("claims");
        assertThat(provider.module()).isEqualTo(PlatformModule.PAYROLL);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("claims");
        assertThat(desc.title()).isEqualTo("Claims and deductions");
        assertThat(desc.displayOrder()).isEqualTo(6);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/me/reimbursement-claims");
        assertThat(desc.requiredAction()).isEqualTo("payroll.reimbursement_claim.read_own");
    }
}
