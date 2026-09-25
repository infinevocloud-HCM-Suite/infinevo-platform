package com.infinevo.core.navigation;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test endpoints standing in for module controllers not yet built on main (W-12.3 §7 & §8).
 * Lives on the test classpath only so NavigationMatchesEnforcementIT can exercise every
 * catalogue item's target endpoint against @RequiresModule and @RequiresAction.
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

    @RequiresAction("core.employee.read")
    @GetMapping("/api/v1/employees")
    public String employees() {
        return "employees-ok";
    }

    @RequiresAction("core.audit.read")
    @GetMapping("/api/v1/audit")
    public String audit() {
        return "audit-ok";
    }
}
