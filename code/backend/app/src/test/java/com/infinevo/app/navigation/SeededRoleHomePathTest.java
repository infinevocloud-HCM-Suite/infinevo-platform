package com.infinevo.app.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.navigation.NavigationCatalogue;
import com.infinevo.core.navigation.NavigationItemResponse;
import com.infinevo.core.navigation.NavigationResponse;
import com.infinevo.core.navigation.NavigationService;
import com.infinevo.hrms.navigation.HrmsNavigation;
import com.infinevo.payroll.navigation.PayrollNavigation;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-35, D-75, D-76: where each seeded role lands, over the <em>shipped</em> menu - core's catalogue plus the HRMS and
 * Payroll items - in a tenant holding both modules. Only this module sees all three catalogues, so the role-by-role
 * check lives here rather than in core's {@code NavigationServiceTest}, which runs over core's items alone.
 *
 * <p>The action sets are the seeded system roles: {@code V158__platform_tenant_role_scope.sql} plus the later grants
 * (hr: documents, reports, proofs; payroll-officer: jobs, tax declarations, proofs, LOP policy in {@code V170};
 * hr, manager, payroll-officer and finance: {@code core.employee.read_own} in {@code V171}, D-75).
 */
class SeededRoleHomePathTest {

    private static final Set<String> HR = Set.of(
            "core.employee.read_own",
            "core.tenant.read",
            "core.user.read",
            "core.role.read",
            "core.employee.read",
            "core.employee.create",
            "core.employee.update",
            "core.employee.delete",
            "core.employee.export",
            "core.employee_identification.read",
            "core.employee_identification.update",
            "core.employee_bank.read",
            "core.employee_bank.update",
            "core.org.read",
            "core.org.manage",
            "core.audit.read",
            "core.leave.read",
            "core.leave.approve",
            "core.leave_type.manage",
            "core.leave_balance.manage",
            "core.attendance.read",
            "core.attendance.manage",
            "core.attendance.export",
            "core.overtime.read",
            "hrms.timesheet.read",
            "hrms.timesheet.approve",
            "hrms.timesheet.export",
            "core.holiday.read",
            "core.holiday.manage",
            "hrms.project.read",
            "hrms.project.manage",
            "core.approval.decide",
            "core.document.read",
            "core.document.upload",
            "core.report.read",
            "payroll.proof.read",
            "payroll.proof.review");

    private static final Set<String> MANAGER = Set.of(
            "core.employee.read_own",
            "core.employee.read_team",
            "core.org.read",
            "core.leave.read_team",
            "core.leave.approve",
            "core.attendance.read_team",
            "hrms.timesheet.read_team",
            "hrms.timesheet.approve",
            "core.holiday.read",
            "hrms.project.read_team",
            "hrms.project.manage",
            "core.approval.decide");

    private static final Set<String> PAYROLL_OFFICER = Set.of(
            "core.employee.read_own",
            "core.employee.read",
            "core.employee_identification.read",
            "core.employee_bank.read",
            "core.org.read",
            "core.attendance.read",
            "core.overtime.read",
            "core.leave.read",
            "payroll.settings.manage",
            "payroll.structure.read",
            "payroll.structure.manage",
            "payroll.salary.read",
            "payroll.salary.manage",
            "payroll.run.read",
            "payroll.run.execute",
            "payroll.payslip.read",
            "payroll.payslip.publish",
            "payroll.tax_declaration.read",
            "payroll.tax_declaration.verify",
            "payroll.statutory_report.read",
            "payroll.statutory_report.generate",
            "payroll.bank_file.export",
            "payroll.report.export",
            "payroll.fbp.read",
            "payroll.reimbursement_claim.read",
            "payroll.employee_deduction.read",
            "payroll.employee_deduction.manage",
            "core.job.read",
            "payroll.tax_declaration.manage",
            "payroll.proof.read",
            "core.lop_policy.read",
            "core.lop_policy.manage");

    private static final Set<String> FINANCE = Set.of(
            "core.employee.read_own",
            "core.org.read",
            "payroll.structure.read",
            "payroll.salary.read",
            "payroll.run.read",
            "payroll.run.approve",
            "payroll.payslip.read",
            "payroll.statutory_report.read",
            "payroll.bank_file.export",
            "payroll.report.export",
            "payroll.reimbursement_claim.read",
            "payroll.employee_deduction.read");

    private static final Set<String> EMPLOYEE = Set.of(
            "core.employee.read_own",
            "core.employee.update_own",
            "core.org.read",
            "core.leave.apply",
            "core.leave.read_own",
            "hrms.attendance.mark",
            "core.attendance.read_own",
            "hrms.timesheet.submit",
            "hrms.timesheet.read_own",
            "core.holiday.read",
            "payroll.payslip.read_own",
            "payroll.tax_declaration.submit",
            "payroll.tax_declaration.read_own",
            "payroll.fbp.read_own",
            "payroll.fbp.declare_own",
            "payroll.reimbursement_claim.read_own",
            "payroll.reimbursement_claim.submit_own",
            "payroll.employee_deduction.read_own",
            "hrms.project.read_own",
            "hrms.overtime.request");

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("D-76: the manager lands on the approvals inbox, and sees the HRMS dashboard")
    void managerLandsOnApprovals() {
        NavigationResponse feed = feedFor(MANAGER);
        assertThat(feed.homePath()).isEqualTo("/approvals");
        assertThat(paths(feed)).contains("/hrms/dashboard");
    }

    @Test
    @DisplayName("D-76: hr lands on the HRMS dashboard")
    void hrLandsOnTheHrmsDashboard() {
        NavigationResponse feed = feedFor(HR);
        assertThat(feed.homePath()).isEqualTo("/hrms/dashboard");
    }

    @Test
    @DisplayName("D-35: the payroll officer and finance land on the payroll dashboard")
    void payrollRolesLandOnThePayrollDashboard() {
        assertThat(feedFor(PAYROLL_OFFICER).homePath()).isEqualTo("/payroll/dashboard");
        assertThat(feedFor(FINANCE).homePath()).isEqualTo("/payroll/dashboard");
    }

    @Test
    @DisplayName("D-75: the employee lands on the portal, which their menu names first")
    void employeeLandsOnThePortalNamedFirst() {
        NavigationResponse feed = feedFor(EMPLOYEE);
        assertThat(feed.homePath()).isEqualTo("/me");
        assertThat(feed.items().get(0).key()).isEqualTo("core.me");
        assertThat(feed.items().get(0).path()).isEqualTo("/me");
    }

    @Test
    @DisplayName("D-75, V171: every seeded role with a linked employee record gets the portal item, home unchanged")
    void everyRoleWithARecordGetsThePortalItem() {
        EmployeeService linked = mock(EmployeeService.class);
        when(linked.currentEmployee()).thenReturn(Optional.of(mock(EmployeeResponse.class)));

        for (Set<String> role : List.of(HR, MANAGER, PAYROLL_OFFICER, FINANCE, EMPLOYEE)) {
            assertThat(feedFor(role, linked).items().get(0).key()).isEqualTo("core.me");
        }
        assertThat(feedFor(HR, linked).homePath()).isEqualTo("/hrms/dashboard");
        assertThat(feedFor(MANAGER, linked).homePath()).isEqualTo("/approvals");
        assertThat(feedFor(PAYROLL_OFFICER, linked).homePath()).isEqualTo("/payroll/dashboard");
        assertThat(feedFor(FINANCE, linked).homePath()).isEqualTo("/payroll/dashboard");
    }

    @Test
    @DisplayName("D-75: a seeded role without a linked employee record gets no portal item, whatever it holds")
    void rolesWithoutARecordHaveNoPortalItem() {
        EmployeeService unlinked = mock(EmployeeService.class);
        when(unlinked.currentEmployee()).thenReturn(Optional.empty());

        for (Set<String> role : List.of(HR, MANAGER, PAYROLL_OFFICER, FINANCE, EMPLOYEE)) {
            assertThat(feedFor(role, unlinked).items())
                    .extracting(NavigationItemResponse::key)
                    .doesNotContain("core.me");
        }
    }

    /** The feed with no employee service, as the unit tests build it: the action alone decides the portal item. */
    private static NavigationResponse feedFor(Set<String> actions) {
        return feedFor(actions, null);
    }

    private static NavigationResponse feedFor(Set<String> actions, EmployeeService employees) {
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any())).thenReturn(true);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.currentActions()).thenReturn(actions);
        TenantContext.set(UUID.randomUUID());
        List<NavigationCatalogue.ItemDefinition> shipped =
                NavigationCatalogue.withContributed(List.of(new HrmsNavigation(), new PayrollNavigation()));
        return new NavigationService(
                        entitlements, permissions, shipped, null, null, new PlatformTenant(), null, employees)
                .navigation();
    }

    private static List<String> paths(NavigationResponse feed) {
        List<String> paths = new ArrayList<>();
        collect(feed.items(), paths);
        return paths;
    }

    private static void collect(List<NavigationItemResponse> items, List<String> into) {
        for (NavigationItemResponse item : items) {
            if (item.children() != null && !item.children().isEmpty()) {
                collect(item.children(), into);
            } else {
                into.add(item.path());
            }
        }
    }
}
