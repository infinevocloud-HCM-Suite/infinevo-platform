package com.infinevo.hrms.navigation;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.core.navigation.NavigationContributor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * HRMS's menu items (W-42.1 §5). Each is here because its endpoint is in this module: the boot-time catalogue check
 * ({@code NavigationCatalogueValidator}) refuses an item whose {@code targetEndpoint} has no {@code GET} mapping.
 */
@Component
public class HrmsNavigation implements NavigationContributor {

    /** The caller's timesheets: {@code TimesheetController}'s {@code GET /api/v1/hrms/timesheets/mine}. */
    public static final ItemDefinition TIMESHEETS = new ItemDefinition(
            "hrms.timesheets",
            "nav.hrms.timesheets",
            "/hrms/timesheets",
            "/api/v1/hrms/timesheets/mine",
            PlatformModule.HRMS,
            "hrms.timesheet.read_own");

    /** Every project the caller manages or may read: {@code ProjectController}'s {@code GET /api/v1/hrms/projects}. */
    public static final ItemDefinition PROJECTS = new ItemDefinition(
            "hrms.projects",
            "nav.hrms.projects",
            "/hrms/projects",
            "/api/v1/hrms/projects",
            PlatformModule.HRMS,
            "hrms.project.manage");

    /** The caller's own projects and tasks: {@code ProjectController}'s {@code GET /api/v1/hrms/projects/mine}. */
    public static final ItemDefinition MY_WORK = new ItemDefinition(
            "hrms.my_work",
            "nav.hrms.my_work",
            "/hrms/my-work",
            "/api/v1/hrms/projects/mine",
            PlatformModule.HRMS,
            "hrms.project.read_own");

    /** Weeks on the caller's projects to approve (W-48.3): {@code TimesheetController}'s {@code GET /api/v1/hrms/timesheets/managed}. */
    public static final ItemDefinition TIMESHEET_REVIEW = new ItemDefinition(
            "hrms.timesheet_review",
            "nav.hrms.timesheet_review",
            "/hrms/timesheet-review",
            "/api/v1/hrms/timesheets/managed",
            PlatformModule.HRMS,
            "hrms.timesheet.approve");

    /** The caller's clock card (W-48.4): {@code ClockController}'s {@code GET /api/v1/hrms/attendance/today}. */
    public static final ItemDefinition ATTENDANCE = new ItemDefinition(
            "hrms.attendance",
            "nav.hrms.attendance",
            "/hrms/attendance",
            "/api/v1/hrms/attendance/today",
            PlatformModule.HRMS,
            "hrms.attendance.mark");

    /** Every clock session, for HR (W-48.4): {@code ClockController}'s {@code GET /api/v1/hrms/attendance/sessions}. */
    public static final ItemDefinition ATTENDANCE_LOG = new ItemDefinition(
            "hrms.attendance_log",
            "nav.hrms.attendance_log",
            "/hrms/attendance-log",
            "/api/v1/hrms/attendance/sessions",
            PlatformModule.HRMS,
            "core.attendance.read");

    /** The tenant's attendance settings (W-48.4): {@code AttendancePreferenceController}'s {@code GET /api/v1/hrms/attendance/preferences}. */
    public static final ItemDefinition ATTENDANCE_SETTINGS = new ItemDefinition(
            "hrms.attendance_settings",
            "nav.hrms.attendance_settings",
            "/hrms/attendance-settings",
            "/api/v1/hrms/attendance/preferences",
            PlatformModule.HRMS,
            "core.attendance.manage");

    /** The caller's HRMS dashboard (W-48.6): {@code HrmsDashboardController}'s {@code GET /api/v1/hrms/dashboard}. */
    public static final ItemDefinition DASHBOARD = new ItemDefinition(
            "hrms.dashboard",
            "nav.hrms.dashboard",
            "/hrms/dashboard",
            "/api/v1/hrms/dashboard",
            PlatformModule.HRMS,
            "hrms.project.read_own");

    /** The caller's regularizations (W-48.5): {@code RegularizationController}'s {@code GET .../regularizations/mine}. */
    public static final ItemDefinition REGULARIZATIONS = new ItemDefinition(
            "hrms.regularizations",
            "nav.hrms.regularizations",
            "/hrms/regularizations",
            "/api/v1/hrms/attendance/regularizations/mine",
            PlatformModule.HRMS,
            "core.attendance.read_own");

    /** The caller's overtime requests (W-48.5): {@code OvertimeRequestController}'s {@code GET .../overtime-requests/mine}. */
    public static final ItemDefinition OVERTIME_REQUESTS = new ItemDefinition(
            "hrms.overtime_requests",
            "nav.hrms.overtime_requests",
            "/hrms/overtime-requests",
            "/api/v1/hrms/overtime-requests/mine",
            PlatformModule.HRMS,
            "hrms.overtime.request");

    /** The module's screens, in menu order. */
    public static final List<ItemDefinition> LEAVES = List.of(
            TIMESHEETS,
            PROJECTS,
            MY_WORK,
            TIMESHEET_REVIEW,
            ATTENDANCE,
            ATTENDANCE_LOG,
            ATTENDANCE_SETTINGS,
            DASHBOARD,
            REGULARIZATIONS,
            OVERTIME_REQUESTS);

    /**
     * The one HRMS menu group (D-34). No action of its own: the feed hides it when every screen inside
     * is hidden, and gives it the first visible screen's path.
     */
    public static final ItemDefinition GROUP = new ItemDefinition(
            "hrms", "nav.hrms", TIMESHEETS.path(), TIMESHEETS.targetEndpoint(), PlatformModule.HRMS, null, LEAVES);

    @Override
    public List<ItemDefinition> items() {
        return List.of(GROUP);
    }
}
