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
 * Unit tests asserting that portal visibility is gated by actions and NOT by the employee role (W-25, spec section 7).
 *
 * <p>Spec: "a caller without the employee role but holding core.employee.read_own and
 * payroll.payslip.read_own sees exactly those two panels — the role is not consulted"
 */
class PortalActionGateTest {

    private EmployeeService employeeService;
    private PermissionService permissionService;
    private EntitlementService entitlementService;

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

        EmployeeResponse employee = new EmployeeResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EMP-002",
                "Custom",
                null,
                "RoleUser",
                "OTHER",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "custom@test.local",
                "9876543210",
                true,
                UUID.randomUUID(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        when(employeeService.currentEmployee()).thenReturn(Optional.of(employee));

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
    }

    @Test
    @DisplayName(
            "Caller without employee role holding core.employee.read_own and payroll.payslip.read_own sees exactly those two")
    void customRoleWithSpecificActionsSeesOnlyThosePanels() {
        // Both modules entitled
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);

        // Caller only holds core.employee.read_own and payroll.payslip.read_own
        when(permissionService.holds("core.employee.read_own")).thenReturn(true);
        when(permissionService.holds("payroll.payslip.read_own")).thenReturn(true);
        when(permissionService.holds("core.leave.read_own")).thenReturn(false);
        when(permissionService.holds("core.document.read_own")).thenReturn(false);
        when(permissionService.holds("hrms.timesheet.read_own")).thenReturn(false);

        PortalPanelService service = new PortalPanelService(
                employeeService,
                permissionService,
                entitlementService,
                List.of(profileProvider, leaveProvider, documentProvider, payslipProvider, timesheetProvider));

        List<PanelDescriptor> panels = service.getPanels();

        assertThat(panels).extracting(PanelDescriptor::code).containsExactly("profile", "payslips");
    }
}
