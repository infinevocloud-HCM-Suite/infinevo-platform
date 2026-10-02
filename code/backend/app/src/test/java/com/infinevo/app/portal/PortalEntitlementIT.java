package com.infinevo.app.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.portal.MyTimesheetController;
import com.infinevo.hrms.timesheet.TimesheetService;
import com.infinevo.payroll.payslip.PayslipController;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.entitlement.EntitlementDeniedException;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.entitlement.RequiresModuleAspect;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.tenant.TenantContext;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for portal endpoint entitlement and action guards (W-25, spec section 7).
 *
 * <p>Verifies:
 * <ul>
 *   <li>A Payroll-only tenant's employee gets 403 from the timesheet endpoint, not just a missing menu.
 *   <li>A caller stripped of {@code payroll.payslip.read_own} gets 403 from {@code /me/payslips} while entitled.
 *   <li>A caller with both module entitlement and action grant gets 200.
 * </ul>
 */
@SpringBootTest(
        classes = PortalEntitlementIT.TestApp.class,
        properties = {
            "KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/infinevo",
            "KEYCLOAK_JWK_SET_URI=http://localhost:8081/realms/infinevo/protocol/openid-connect/certs",
            "DB_URL=jdbc:postgresql://localhost:5432/test",
            "DB_USERNAME=test",
            "DB_PASSWORD=test"
        })
@AutoConfigureMockMvc(addFilters = false)
class PortalEntitlementIT {

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
    @EnableAspectJAutoProxy
    static class TestApp {

        // W-42.1 replaced W-25's timesheet placeholder with the real controller, over a mocked service: this test is
        // about the module and action guards in front of it, not about timesheets.
        @Bean
        MyTimesheetController myTimesheetController(TimesheetService timesheetService) {
            return new MyTimesheetController(timesheetService);
        }

        // W-36.2 replaced W-25's payslips placeholder with the real controller.
        @Bean
        PayslipController payslipController(PayslipService payslipService) {
            return new PayslipController(payslipService);
        }

        @Bean
        AuthzExceptionHandler authzExceptionHandler() {
            return new AuthzExceptionHandler();
        }

        @Bean
        RequiresModuleAspect requiresModuleAspect(EntitlementService entitlementService) {
            return new RequiresModuleAspect(entitlementService);
        }

        @Bean
        RequiresActionAspect requiresActionAspect(PermissionService permissionService) {
            return new RequiresActionAspect(permissionService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntitlementService entitlementService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private PayslipService payslipService;

    @MockBean
    private TimesheetService timesheetService;

    private final UUID acmeTenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID globexTenant = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Set<PlatformModule> entitledModules = new HashSet<>();
    private final Set<String> grantedActions = new HashSet<>();

    @BeforeEach
    void setUp() {
        entitledModules.clear();
        grantedActions.clear();

        when(entitlementService.holds(any(PlatformModule.class))).thenAnswer(inv -> {
            PlatformModule mod = inv.getArgument(0);
            return entitledModules.contains(mod);
        });

        doAnswer(inv -> {
                    PlatformModule mod = inv.getArgument(0);
                    if (!entitledModules.contains(mod)) {
                        throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, null);
                    }
                    return null;
                })
                .when(entitlementService)
                .require(any(PlatformModule.class), any(RequiresModule.Mode.class), anyBoolean());

        when(permissionService.holds(any(String.class))).thenAnswer(inv -> {
            String action = inv.getArgument(0);
            return grantedActions.contains(action);
        });

        doAnswer(inv -> {
                    String action = inv.getArgument(0);
                    if (!grantedActions.contains(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any(String.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A Payroll-only tenant employee gets 403 MODULE_NOT_ENTITLED from timesheet endpoint")
    void payrollOnlyTenantGets403OnTimesheet() throws Exception {
        TenantContext.set(acmeTenant);
        entitledModules.add(PlatformModule.PAYROLL);
        grantedActions.add("hrms.timesheet.read_own");

        mockMvc.perform(get("/api/v1/me/timesheet"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    @Test
    @DisplayName("A caller stripped of payroll.payslip.read_own gets 403 FORBIDDEN from /me/payslips while entitled")
    void strippedPayslipActionGets403() throws Exception {
        TenantContext.set(acmeTenant);
        entitledModules.add(PlatformModule.PAYROLL);
        // Action NOT added to grantedActions

        mockMvc.perform(get("/api/v1/me/payslips"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A caller with PAYROLL entitlement and payroll.payslip.read_own gets 200 from /me/payslips")
    void entitledAndPermittedCallerGets200OnPayslips() throws Exception {
        TenantContext.set(acmeTenant);
        entitledModules.add(PlatformModule.PAYROLL);
        grantedActions.add("payroll.payslip.read_own");
        when(payslipService.listOwn(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/me/payslips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("A Globex caller with HRMS entitlement and hrms.timesheet.read_own gets 200 from /me/timesheet")
    void entitledAndPermittedCallerGets200OnTimesheet() throws Exception {
        TenantContext.set(globexTenant);
        entitledModules.add(PlatformModule.HRMS);
        entitledModules.add(PlatformModule.PAYROLL);
        grantedActions.add("hrms.timesheet.read_own");
        when(timesheetService.forWeek(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/me/timesheet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
}
