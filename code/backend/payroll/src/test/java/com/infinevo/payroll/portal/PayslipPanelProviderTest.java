package com.infinevo.payroll.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayslipPanelProviderTest {

    @Test
    @DisplayName("PayslipPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        PayslipPanelProvider provider = new PayslipPanelProvider();
        assertThat(provider.code()).isEqualTo("payslips");
        assertThat(provider.module()).isEqualTo(PlatformModule.PAYROLL);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("payslips");
        assertThat(desc.title()).isEqualTo("My Payslips");
        assertThat(desc.displayOrder()).isEqualTo(4);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/me/payslips");
        assertThat(desc.requiredAction()).isEqualTo("payroll.payslip.read_own");
    }
}
