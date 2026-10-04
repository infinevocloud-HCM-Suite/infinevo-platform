package com.infinevo.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.core.portal.PortalPanelService;
import com.infinevo.hrms.portal.TimesheetPanelProvider;
import com.infinevo.hrms.timesheet.TimesheetService;
import com.infinevo.payroll.portal.PayslipPanelProvider;
import com.infinevo.payroll.portal.TaxDeclarationPanelProvider;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Integration test for portal panel discovery across modules (W-25, spec section 7).
 *
 * <p>Verifies:
 * With {@code payroll} and {@code hrms} on the classpath, both module providers are discovered
 * by core without core importing either.
 */
@SpringBootTest(
        classes = PortalPanelDiscoveryIT.TestApp.class,
        properties = {
            "KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/infinevo",
            "KEYCLOAK_JWK_SET_URI=http://localhost:8081/realms/infinevo/protocol/openid-connect/certs",
            "DB_URL=jdbc:postgresql://localhost:5432/test",
            "DB_USERNAME=test",
            "DB_PASSWORD=test"
        })
class PortalPanelDiscoveryIT {

    @SpringBootApplication(
            exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                ManagementWebSecurityAutoConfiguration.class
            })
    @ComponentScan(
            basePackages = {"com.infinevo.core.portal", "com.infinevo.payroll.portal", "com.infinevo.hrms.portal"})
    static class TestApp {}

    @Autowired
    private PortalPanelService portalPanelService;

    @Autowired
    private List<PortalPanelProvider> providers;

    @MockBean
    private EntitlementService entitlementService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private EmployeeService employeeService;

    // The hrms portal package now holds the real MyTimesheetController (W-42.1), which needs this.
    @MockBean
    private TimesheetService timesheetService;

    @Test
    @DisplayName("PortalPanelService discovers all 7 providers including module providers from payroll and hrms")
    void discoversAllSevenProviders() {
        assertThat(providers).hasSize(7);

        List<String> codes = providers.stream().map(PortalPanelProvider::code).toList();
        assertThat(codes)
                .containsExactlyInAnyOrder(
                        "profile", "leave", "documents", "payslips", "taxDeclaration", "timesheet", "claims");

        assertThat(providers.stream().anyMatch(p -> p instanceof PayslipPanelProvider))
                .isTrue();
        assertThat(providers.stream().anyMatch(p -> p instanceof TaxDeclarationPanelProvider))
                .isTrue();
        assertThat(providers.stream().anyMatch(p -> p instanceof TimesheetPanelProvider))
                .isTrue();
    }

    @Test
    @DisplayName("PortalPanelService filters panels by module entitlement, action check, and portalEnabled")
    void filtersAndOrdersPanelsProperly() {
        UUID employeeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        EmployeeResponse employeeResponse = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-001",
                "Jane",
                null,
                "Doe",
                "FEMALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "jane@test.local",
                "9876543210",
                true,
                UUID.randomUUID(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        when(employeeService.currentEmployee()).thenReturn(Optional.of(employeeResponse));

        // Entitled to PAYROLL only (like Acme)
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(false);

        // Holds all own actions
        when(permissionService.holds(anyString())).thenReturn(true);

        List<PanelDescriptor> panels = portalPanelService.getPanels();
        List<String> panelCodes = panels.stream().map(PanelDescriptor::code).toList();

        // Acme sees profile, leave, documents, payslips, taxDeclaration, claims - but NOT timesheet
        assertThat(panelCodes).containsExactly("profile", "leave", "documents", "payslips", "taxDeclaration", "claims");

        // W-47.4: an HRMS-only tenant never sees the claims panel
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(false);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        List<String> hrmsOnly = portalPanelService.getPanels().stream()
                .map(PanelDescriptor::code)
                .toList();
        assertThat(hrmsOnly)
                .doesNotContain("claims", "payslips", "taxDeclaration")
                .contains("timesheet");
    }
}
