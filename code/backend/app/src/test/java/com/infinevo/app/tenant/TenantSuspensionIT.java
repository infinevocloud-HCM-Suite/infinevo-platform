package com.infinevo.app.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.invitation.AcceptOutcome;
import com.infinevo.core.invitation.InvitationAcceptanceController;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.navigation.NavigationController;
import com.infinevo.core.navigation.NavigationResponse;
import com.infinevo.core.navigation.NavigationService;
import com.infinevo.core.notification.NotificationController;
import com.infinevo.core.notification.NotificationResponse;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.report.ExportController;
import com.infinevo.core.report.ExportService;
import com.infinevo.core.report.ReportDefinitionRepository;
import com.infinevo.payroll.payslip.PayslipController;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.EntitlementSnapshot;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.entitlement.RequiresModuleAspect;
import com.infinevo.shared.impersonation.ActingAs;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.tenant.TenantSuspensionAspect;
import java.time.Instant;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration verification for W-85.1 (spec §7 & §8).
 *
 * <p>Verifies write guards, exempt endpoints, statutory reads and exports across
 * ACTIVE, PAST_DUE, SUSPENDED and CANCELLED tenants.
 */
@SpringBootTest(
        classes = TenantSuspensionIT.TestApp.class,
        properties = {
            "KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/infinevo",
            "KEYCLOAK_JWK_SET_URI=http://localhost:8081/realms/infinevo/protocol/openid-connect/certs",
            "DB_URL=jdbc:postgresql://localhost:5432/test",
            "DB_USERNAME=test",
            "DB_PASSWORD=test"
        })
@AutoConfigureMockMvc(addFilters = false)
class TenantSuspensionIT {

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

        @Bean
        PlatformTenant platformTenant() {
            return new PlatformTenant(PlatformTenant.DEFAULT_PLATFORM_TENANT_ID);
        }

        @Bean
        TenantSuspensionAspect tenantSuspensionAspect(
                EntitlementService entitlementService, PlatformTenant platformTenant) {
            return new TenantSuspensionAspect(entitlementService, platformTenant, true);
        }

        @Bean
        RequiresModuleAspect requiresModuleAspect(EntitlementService entitlementService) {
            return new RequiresModuleAspect(entitlementService);
        }

        @Bean
        RequiresActionAspect requiresActionAspect(PermissionService permissionService) {
            return new RequiresActionAspect(permissionService);
        }

        @Bean
        AuthzExceptionHandler authzExceptionHandler() {
            return new AuthzExceptionHandler();
        }

        @Bean
        EmployeeTestController employeeTestController() {
            return new EmployeeTestController();
        }

        @Bean
        TenantProfileTestController tenantProfileTestController() {
            return new TenantProfileTestController();
        }

        @Bean
        HrmsTestController hrmsTestController() {
            return new HrmsTestController();
        }

        @Bean
        ExportController exportController(
                ExportService exportService, ReportDefinitionRepository defs, PermissionService perms) {
            return new ExportController(exportService, defs, perms, null, () -> null, null);
        }

        @Bean
        NotificationController notificationController(NotificationService notificationService) {
            return new NotificationController(notificationService);
        }

        @Bean
        InvitationAcceptanceController invitationAcceptanceController(InvitationService invitationService) {
            return new InvitationAcceptanceController(invitationService);
        }

        @Bean
        PayslipController payslipController(PayslipService payslipService) {
            return new PayslipController(payslipService);
        }

        @Bean
        NavigationController navigationController(NavigationService navigationService) {
            return new NavigationController(navigationService);
        }

        @Bean
        PlatformTenantAdminController platformTenantAdminController() {
            return new PlatformTenantAdminController();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntitlementService entitlementService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private ExportService exportService;

    @MockBean
    private ReportDefinitionRepository reportDefinitionRepository;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private InvitationService invitationService;

    @MockBean
    private PayslipService payslipService;

    @MockBean
    private NavigationService navigationService;

    private static final UUID SUSPENDED_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CANCELLED_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTIVE_TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PAST_DUE_TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PLATFORM_TENANT_ID = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;

    @BeforeEach
    void setUp() {
        // Mock snapshots for each tenant state
        when(entitlementService.snapshotOf(SUSPENDED_TENANT))
                .thenReturn(
                        new EntitlementSnapshot(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL), Set.of(), true));
        when(entitlementService.snapshotOf(CANCELLED_TENANT))
                .thenReturn(
                        new EntitlementSnapshot(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL), Set.of(), true));
        when(entitlementService.snapshotOf(ACTIVE_TENANT))
                .thenReturn(
                        new EntitlementSnapshot(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL), Set.of(), false));
        when(entitlementService.snapshotOf(PAST_DUE_TENANT))
                .thenReturn(
                        new EntitlementSnapshot(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL), Set.of(), false));
        when(entitlementService.snapshotOf(PLATFORM_TENANT_ID))
                .thenReturn(
                        new EntitlementSnapshot(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL), Set.of(), false));

        // Let action checks pass by default for authenticated actions
        when(permissionService.currentActions())
                .thenReturn(Set.of(
                        "core.employee.read",
                        "core.employee.create",
                        "core.tenant.manage",
                        "core.report.read",
                        "hrms.attendance.read",
                        "hrms.attendance.write",
                        "payroll.payslip.read_own"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ActingAs.clear();
    }

    @Test
    @DisplayName("GET /api/v1/employees returns 200 for SUSPENDED tenant")
    void getEmployeesReturns200WhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        mockMvc.perform(get("/api/v1/employees")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/employees returns 403 TENANT_SUSPENDED for SUSPENDED tenant")
    void postEmployeesReturns403WhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/current/profile returns 403 TENANT_SUSPENDED for SUSPENDED tenant")
    void putProfileReturns403WhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        mockMvc.perform(put("/api/v1/tenants/current/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("POST /api/v1/exports returns 201 with download link for SUSPENDED tenant")
    void postExportAllowedWhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        UUID defId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(exportService.export(eq(defId), any()))
                .thenReturn(new ExportService.ExportResponse(
                        docId,
                        "report.csv",
                        com.infinevo.core.report.ExportFormat.CSV,
                        100L,
                        "/url",
                        Instant.now().plusSeconds(900)));

        mockMvc.perform(post("/api/v1/exports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + defId + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/read returns 200 for SUSPENDED tenant")
    void postNotificationReadAllowedWhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        UUID notifId = UUID.randomUUID();
        when(notificationService.markRead(notifId))
                .thenReturn(new NotificationResponse(
                        notifId,
                        com.infinevo.core.notification.NotificationEvent.PAYSLIP_READY,
                        "Title",
                        "Body",
                        "Ref",
                        Instant.now(),
                        Instant.now()));

        mockMvc.perform(post("/api/v1/notifications/" + notifId + "/read")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/invitations/accept is not TENANT_SUSPENDED")
    void postInvitationAcceptAllowed() throws Exception {
        // Invitations are public, no tenant bound
        TenantContext.clear();
        when(invitationService.acceptInvitation("valid-token", "valid-password123"))
                .thenReturn(AcceptOutcome.PASSWORD_SET);

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\",\"password\":\"valid-password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HRMS read GET /api/v1/hrms/attendance/today returns 200 for SUSPENDED tenant")
    void hrmsReadAllowedWhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        mockMvc.perform(get("/api/v1/hrms/attendance/today")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HRMS write POST /api/v1/hrms/attendance/clock-in returns 403 TENANT_SUSPENDED for SUSPENDED tenant")
    void hrmsWriteRefusedWhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        mockMvc.perform(post("/api/v1/hrms/attendance/clock-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("Payroll read GET /api/v1/me/payslips returns 200 for SUSPENDED tenant")
    void payrollReadAllowedWhenSuspended() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        when(payslipService.listOwn(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/me/payslips")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Same reads and writes on CANCELLED tenant return same results")
    void cancelledTenantHasSameBehavior() throws Exception {
        TenantContext.set(CANCELLED_TENANT);

        mockMvc.perform(get("/api/v1/employees")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("Platform admin PUT /api/v1/tenants/{id}/subscription/status to ACTIVE returns 200")
    void platformAdminCanChangeSubscriptionStatus() throws Exception {
        TenantContext.set(PLATFORM_TENANT_ID);
        mockMvc.perform(put("/api/v1/tenants/" + SUSPENDED_TENANT + "/subscription/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mutating write inside impersonation session passes even for SUSPENDED tenant")
    void impersonationSessionAllowsWrite() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        ActingAs.set(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "support@infinevo.com", Set.of());

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PAST_DUE tenant POST /api/v1/employees returns 201")
    void pastDueTenantCanWrite() throws Exception {
        TenantContext.set(PAST_DUE_TENANT);
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /api/v1/navigation returns subscriptionStatus SUSPENDED")
    void navigationReturnsSubscriptionStatus() throws Exception {
        TenantContext.set(SUSPENDED_TENANT);
        when(navigationService.navigation())
                .thenReturn(new NavigationResponse(
                        List.of(), Set.of(), Set.of(), "Suspended Company", "/me", null, null, "SUSPENDED"));

        mockMvc.perform(get("/api/v1/navigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionStatus").value("SUSPENDED"));
    }

    @RestController
    @RequestMapping("/api/v1/employees")
    static class EmployeeTestController {
        @GetMapping
        public List<String> list() {
            return List.of("EMP-001");
        }

        @PostMapping
        public ResponseEntity<?> create() {
            return ResponseEntity.status(HttpStatus.CREATED).body("created");
        }
    }

    @RestController
    @RequestMapping("/api/v1/tenants/current")
    static class TenantProfileTestController {
        @PutMapping("/profile")
        public ResponseEntity<?> updateProfile() {
            return ResponseEntity.ok("updated");
        }
    }

    @RestController
    @RequestMapping("/api/v1/hrms/attendance")
    @RequiresModule(PlatformModule.HRMS)
    static class HrmsTestController {
        @GetMapping("/today")
        public ResponseEntity<?> today() {
            return ResponseEntity.ok("today");
        }

        @PostMapping("/clock-in")
        public ResponseEntity<?> clockIn() {
            return ResponseEntity.ok("clocked-in");
        }
    }

    @RestController
    @RequestMapping("/api/v1/tenants")
    static class PlatformTenantAdminController {
        @PutMapping("/{id}/subscription/status")
        public ResponseEntity<?> updateStatus(@PathVariable("id") UUID id) {
            return ResponseEntity.ok("status-updated");
        }
    }
}
