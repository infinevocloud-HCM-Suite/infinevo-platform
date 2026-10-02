package com.infinevo.payroll.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaxDeclarationPanelProviderTest {

    @Test
    @DisplayName("TaxDeclarationPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        TaxDeclarationPanelProvider provider = new TaxDeclarationPanelProvider();
        assertThat(provider.code()).isEqualTo("taxDeclaration");
        assertThat(provider.module()).isEqualTo(PlatformModule.PAYROLL);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("taxDeclaration");
        assertThat(desc.title()).isEqualTo("Tax declaration");
        assertThat(desc.displayOrder()).isEqualTo(5);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/me/tax-declaration");
        assertThat(desc.requiredAction()).isEqualTo("payroll.tax_declaration.read_own");
    }
}
