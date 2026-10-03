package com.infinevo.hrms.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimesheetPanelProviderTest {

    @Test
    @DisplayName("TimesheetPanelProvider declares code, module and panel descriptor")
    void providerContract() {
        TimesheetPanelProvider provider = new TimesheetPanelProvider();
        assertThat(provider.code()).isEqualTo("timesheet");
        assertThat(provider.module()).isEqualTo(PlatformModule.HRMS);

        PanelDescriptor desc = provider.panel(UUID.randomUUID());
        assertThat(desc.code()).isEqualTo("timesheet");
        assertThat(desc.title()).isEqualTo("My Timesheet");
        assertThat(desc.displayOrder()).isEqualTo(5);
        assertThat(desc.endpoint()).isEqualTo("/api/v1/me/timesheet");
        assertThat(desc.requiredAction()).isEqualTo("hrms.timesheet.read_own");
    }
}
