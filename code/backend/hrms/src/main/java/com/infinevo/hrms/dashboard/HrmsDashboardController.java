package com.infinevo.hrms.dashboard;

import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-44 §4 — one read-only {@code GET} for the caller's HRMS dashboard. Replaces the three legacy calls, each with
 * its own hand-built {@code Map} ({@code ManagerDashboardController.java:25,37},
 * {@code EmployeeDashboardController.java:24,51}), with one round trip under {@code /api/v1} and the
 * {@code status} / {@code message} / {@code data} envelope (DEBT-007, DEBT-008). Any one of the five actions opens
 * it; the service decides each block. The tenant is the bound one.
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/dashboard")
public class HrmsDashboardController {

    private final HrmsDashboardService dashboardService;

    public HrmsDashboardController(HrmsDashboardService dashboardService) {
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService must not be null");
    }

    @GetMapping
    @RequiresAction(
            value = "hrms.project.read_own",
            anyOf = {
                "hrms.timesheet.read_own",
                "hrms.project.read_team",
                "hrms.timesheet.read_team",
                "hrms.timesheet.approve"
            })
    public ApiResponse<HrmsDashboardResponse> dashboard() {
        return ApiResponse.success("HRMS dashboard retrieved successfully", dashboardService.forCaller());
    }
}
