package com.infinevo.app.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.portal.DocumentPanelProvider;
import com.infinevo.core.portal.LeavePanelProvider;
import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.core.portal.ProfilePanelProvider;
import com.infinevo.hrms.portal.TimesheetPanelProvider;
import com.infinevo.payroll.portal.ClaimsPanelProvider;
import com.infinevo.payroll.portal.PayslipPanelProvider;
import com.infinevo.payroll.portal.TaxDeclarationPanelProvider;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-87: every portal panel carries its own display order, so the /me tab order is defined. Only {@code app}
 * sees the providers of all three modules, so the check lives here.
 */
class PortalPanelOrderTest {

    private static final List<PortalPanelProvider> PROVIDERS = List.of(
            new ProfilePanelProvider(),
            new LeavePanelProvider(),
            new DocumentPanelProvider(),
            new PayslipPanelProvider(),
            new TimesheetPanelProvider(),
            new TaxDeclarationPanelProvider(),
            new ClaimsPanelProvider());

    private static List<PanelDescriptor> descriptors() {
        UUID employeeId = UUID.randomUUID();
        return PROVIDERS.stream().map(p -> p.panel(employeeId)).toList();
    }

    @Test
    @DisplayName("no two portal panels share a display order")
    void displayOrdersAreDistinct() {
        List<Integer> orders =
                descriptors().stream().map(PanelDescriptor::displayOrder).toList();
        assertThat(orders).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("tabs run profile, leave, documents, payslips, timesheet, tax declaration, claims")
    void tabOrderIsTheAgreedOne() {
        List<String> codes = descriptors().stream()
                .sorted(Comparator.comparingInt(PanelDescriptor::displayOrder))
                .map(PanelDescriptor::code)
                .toList();
        assertThat(codes)
                .containsExactly("profile", "leave", "documents", "payslips", "timesheet", "taxDeclaration", "claims");
    }
}
