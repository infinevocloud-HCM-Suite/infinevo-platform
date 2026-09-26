package com.infinevo.core.navigation;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only stubs for catalogue endpoints not yet built, or not in the test app's scan scope
 * (W-12.3 §7 &amp; §8). NavigationMatchesEnforcementIT walks every catalogue item's target
 * endpoint and asserts: visible item → non-403; absent item → 403.
 *
 * <p>Stubs retained here:
 * <ul>
 *   <li>{@code /api/v1/timesheets} — HRMS module, not yet built (W-40)</li>
 *   <li>{@code /api/v1/payroll/runs} — Payroll module, not yet built (W-29)</li>
 *   <li>{@code /api/v1/audit} — real AuditController is in {@code shared.audit}, which is
 *       outside the PermissionGuardTestApp component-scan; a stub is required to make the
 *       mapping visible in this test context</li>
 * </ul>
 *
 * <p>Stubs intentionally absent (real controllers already handle these):
 * <ul>
 *   <li>{@code GET /api/v1/employees} — EmployeeController has GET (added W-12.3)</li>
 *   <li>{@code GET /api/v1/departments}, /designations, /work-locations — DepartmentController
 *       etc. are in {@code core.org} which is scanned</li>
 *   <li>{@code GET /api/v1/roles} — RoleController is in {@code core.authz} which is scanned</li>
 * </ul>
 */
@RestController
class NavigationTestEndpointsController {

    @RequiresModule(PlatformModule.HRMS)
    @RequiresAction("hrms.timesheet.read")
    @GetMapping("/api/v1/timesheets")
    public String timesheets() {
        return "timesheets-ok";
    }

    @RequiresModule(PlatformModule.PAYROLL)
    @RequiresAction("payroll.run.read")
    @GetMapping("/api/v1/payroll/runs")
    public String payrollRuns() {
        return "payroll-runs-ok";
    }

    @RequiresAction("core.audit.read")
    @GetMapping("/api/v1/audit")
    public String audit() {
        return "audit-ok";
    }
}
