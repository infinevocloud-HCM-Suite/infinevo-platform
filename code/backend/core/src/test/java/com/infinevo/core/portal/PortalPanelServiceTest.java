package com.infinevo.core.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PortalPanelService} (W-25, spec section 7).
 *
 * <p>Covers:
 * <ul>
 *   <li>All three purchase combinations return the right panel set, from stub providers;</li>
 *   <li>A provider whose action the caller lacks is dropped;</li>
 *   <li>Ordering follows {@code displayOrder};</li>
 *   <li>Disabled portal flag yields empty list (strictest wins).</li>
 * </ul>
 */
class PortalPanelServiceTest {

    private EmployeeService employeeService;
    private PermissionService permissionService;
    private EntitlementService entitlementService;

    private UUID tenantId;
    private UUID employeeId;
    private EmployeeResponse enabledEmployee;

    private PortalPanelProvider profileProvider;
    private PortalPanelProvider leaveProvider;
    private PortalPanelProvider documentProvider;
    private PortalPanelProvider payslipProvider;
    private PortalPanelProvider timesheetProvider;

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);
        entitlementService = mock(EntitlementService.class);

        tenantId = UUID.randomUUID();
        employeeId = UUID.randomUUID();

        enabledEmployee = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-001",
                "Ravi",
                null,
                "Kumar",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "ravi@test.local",
                "9876543210",
                true, // portalEnabled
                UUID.randomUUID(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        profileProvider = new ProfilePanelProvider();
        leaveProvider = new LeavePanelProvider();
        documentProvider = new DocumentPanelProvider();

        payslipProvider = new PortalPanelProvider() {
            @Override
            public String code() {
                return "payslips";
            }

            @Override
            public PlatformModule module() {
                return PlatformModule.PAYROLL;
            }

            @Override
            public PanelDescriptor panel(UUID empId) {
                return new PanelDescriptor(
                        "payslips", "My Payslips", 4, "/api/v1/me/payslips", "payroll.payslip.read_own");
            }
        };

        timesheetProvider = new PortalPanelProvider() {
            @Override
            public String code() {
                return "timesheet";
            }

            @Override
            public PlatformModule module() {
                return PlatformModule.HRMS;
            }

            @Override
            public PanelDescriptor panel(UUID empId) {
                return new PanelDescriptor(
                        "timesheet", "My Timesheet", 5, "/api/v1/me/timesheet", "hrms.timesheet.read_own");
            }
        };

        when(employeeService.currentEmployee()).thenReturn(Optional.of(enabledEmployee));
    }

    @Test
    @DisplayName("Purchased both HRMS and PAYROLL (Globex): returns all 5 panels ordered by displayOrder")
    void bothModulesReturnsAllFivePanels() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);

        when(permissionService.holds("core.employee.read_own")).thenReturn(true);
        when(permissionService.holds("core.leave.read_own")).thenReturn(true);
        when(permissionService.holds("core.document.read_own")).thenReturn(true);
        when(permissionService.holds("payroll.payslip.read_own")).thenReturn(true);
        when(permissionService.holds("hrms.timesheet.read_own")).thenReturn(true);

        // Providers passed in shuffled order
        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(timesheetProvider, profileProvider, payslipProvider, documentProvider, leaveProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels)
                .extracting(PanelDescriptor::code)
                .containsExactly("profile", "leave", "documents", "payslips", "timesheet");
    }

    @Test
    @DisplayName("Payroll-only purchase (Acme): returns 4 panels, timesheet is omitted")
    void payrollOnlyOmitsTimesheet() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(false);

        when(permissionService.holds("core.employee.read_own")).thenReturn(true);
        when(permissionService.holds("core.leave.read_own")).thenReturn(true);
        when(permissionService.holds("core.document.read_own")).thenReturn(true);
        when(permissionService.holds("payroll.payslip.read_own")).thenReturn(true);
        when(permissionService.holds("hrms.timesheet.read_own")).thenReturn(true);

        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(profileProvider, leaveProvider, documentProvider, payslipProvider, timesheetProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels)
                .extracting(PanelDescriptor::code)
                .containsExactly("profile", "leave", "documents", "payslips");
    }

    @Test
    @DisplayName("Core-only tenant: returns 3 core panels only")
    void coreOnlyReturnsCorePanels() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(false);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(false);

        when(permissionService.holds("core.employee.read_own")).thenReturn(true);
        when(permissionService.holds("core.leave.read_own")).thenReturn(true);
        when(permissionService.holds("core.document.read_own")).thenReturn(true);

        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(profileProvider, leaveProvider, documentProvider, payslipProvider, timesheetProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels).extracting(PanelDescriptor::code).containsExactly("profile", "leave", "documents");
    }

    @Test
    @DisplayName("A provider whose action the caller lacks is dropped")
    void missingActionDropsPanel() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);

        when(permissionService.holds("core.employee.read_own")).thenReturn(true);
        when(permissionService.holds("core.leave.read_own")).thenReturn(false); // Lack leave action
        when(permissionService.holds("core.document.read_own")).thenReturn(true);
        when(permissionService.holds("payroll.payslip.read_own")).thenReturn(true);
        when(permissionService.holds("hrms.timesheet.read_own")).thenReturn(false); // Lack timesheet action

        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(profileProvider, leaveProvider, documentProvider, payslipProvider, timesheetProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels).extracting(PanelDescriptor::code).containsExactly("profile", "documents", "payslips");
    }

    @Test
    @DisplayName("is_portal_enabled = false returns empty panel list even if all modules and actions are held")
    void disabledPortalReturnsEmptyList() {
        EmployeeResponse disabledEmployee = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-001",
                "Ravi",
                null,
                "Kumar",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "ravi@test.local",
                "9876543210",
                false, // portalEnabled = false
                UUID.randomUUID(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        when(employeeService.currentEmployee()).thenReturn(Optional.of(disabledEmployee));
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        when(permissionService.holds("core.employee.read_own")).thenReturn(true);

        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(profileProvider, leaveProvider, documentProvider, payslipProvider, timesheetProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels).isEmpty();
    }

    @Test
    @DisplayName("No current employee returns empty panel list")
    void noCurrentEmployeeReturnsEmptyList() {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());

        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(profileProvider, leaveProvider, documentProvider, payslipProvider, timesheetProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels).isEmpty();
    }
}
