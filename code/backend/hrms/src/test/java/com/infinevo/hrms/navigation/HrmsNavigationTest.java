package com.infinevo.hrms.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.hrms.attendance.AttendancePreferenceController;
import com.infinevo.hrms.attendance.ClockController;
import com.infinevo.hrms.attendance.RegularizationController;
import com.infinevo.hrms.dashboard.HrmsDashboardController;
import com.infinevo.hrms.overtime.OvertimeRequestController;
import com.infinevo.hrms.project.ProjectController;
import com.infinevo.hrms.timesheet.TimesheetController;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * W-42.1 §5 and W-48.1 §4: HRMS's menu items are backed by real endpoints, with the module (and, where the endpoint
 * takes one action, the action) that endpoint enforces.
 *
 * <p>The boot-time {@code NavigationCatalogueValidator} refuses to start the application when a leaf's endpoint has no
 * {@code GET} mapping, but it only runs inside the whole application. This asks the same question of the controller
 * directly, so a renamed path or a changed guard fails here, in the module's own build, and not at the next boot.
 */
class HrmsNavigationTest {

    private final List<ItemDefinition> items = new HrmsNavigation().items();

    @Test
    @DisplayName("HRMS contributes exactly ten items, as the specs' tables say")
    void tenItems() {
        assertThat(items).hasSize(10);
        assertItem(
                items.get(0),
                "hrms.timesheets",
                "nav.hrms.timesheets",
                "/hrms/timesheets",
                "/api/v1/hrms/timesheets/mine",
                "hrms.timesheet.read_own");
        assertItem(
                items.get(1),
                "hrms.projects",
                "nav.hrms.projects",
                "/hrms/projects",
                "/api/v1/hrms/projects",
                "hrms.project.manage");
        assertItem(
                items.get(2),
                "hrms.my_work",
                "nav.hrms.my_work",
                "/hrms/my-work",
                "/api/v1/hrms/projects/mine",
                "hrms.project.read_own");
        assertItem(
                items.get(3),
                "hrms.timesheet_review",
                "nav.hrms.timesheet_review",
                "/hrms/timesheet-review",
                "/api/v1/hrms/timesheets/managed",
                "hrms.timesheet.approve");
        assertItem(
                items.get(4),
                "hrms.attendance",
                "nav.hrms.attendance",
                "/hrms/attendance",
                "/api/v1/hrms/attendance/today",
                "hrms.attendance.mark");
        assertItem(
                items.get(5),
                "hrms.attendance_log",
                "nav.hrms.attendance_log",
                "/hrms/attendance-log",
                "/api/v1/hrms/attendance/sessions",
                "core.attendance.read");
        assertItem(
                items.get(6),
                "hrms.attendance_settings",
                "nav.hrms.attendance_settings",
                "/hrms/attendance-settings",
                "/api/v1/hrms/attendance/preferences",
                "core.attendance.manage");
        assertItem(
                items.get(7),
                "hrms.dashboard",
                "nav.hrms.dashboard",
                "/hrms/dashboard",
                "/api/v1/hrms/dashboard",
                "hrms.project.read_own");
        assertItem(
                items.get(8),
                "hrms.regularizations",
                "nav.hrms.regularizations",
                "/hrms/regularizations",
                "/api/v1/hrms/attendance/regularizations/mine",
                "core.attendance.read_own");
        assertItem(
                items.get(9),
                "hrms.overtime_requests",
                "nav.hrms.overtime_requests",
                "/hrms/overtime-requests",
                "/api/v1/hrms/overtime-requests/mine",
                "hrms.overtime.request");
    }

    @Test
    @DisplayName(
            "W-48.5 / W-48.6: dashboard, regularizations and overtime requests are GETs guarded by the item's action")
    void requestAndDashboardEndpointsAreReal() {
        assertGuardedGet(HrmsDashboardController.class, items.get(7));
        assertGuardedGet(RegularizationController.class, items.get(8));
        assertGuardedGet(OvertimeRequestController.class, items.get(9));
    }

    private static void assertGuardedGet(Class<?> controller, ItemDefinition item) {
        Method target = getMapping(controller, item.targetEndpoint());
        RequiresAction action = target.getAnnotation(RequiresAction.class);
        if (action == null) {
            action = controller.getAnnotation(RequiresAction.class);
        }
        assertThat(action.value()).isEqualTo(item.requiredAction());
        assertThat(controller.getAnnotation(RequiresModule.class).value()).isEqualTo(item.requiredModule());
    }

    @Test
    @DisplayName("W-48.3 / W-48.4: every new item's targetEndpoint is a GET of its controller under HRMS")
    void newItemsEndpointsAreReal() {
        assertThat(getMapping(TimesheetController.class, items.get(3).targetEndpoint()))
                .isNotNull();
        assertThat(getMapping(ClockController.class, items.get(4).targetEndpoint()))
                .isNotNull();
        assertThat(getMapping(ClockController.class, items.get(5).targetEndpoint()))
                .isNotNull();
        assertThat(getMapping(AttendancePreferenceController.class, items.get(6).targetEndpoint()))
                .isNotNull();
        for (ItemDefinition item : items) {
            assertThat(item.requiredModule()).isEqualTo(PlatformModule.HRMS);
        }
    }

    @Test
    @DisplayName("Timesheets: a GET of TimesheetController, behind the same module and action")
    void timesheetsEndpointIsRealAndGuardedAsTheItemSays() {
        ItemDefinition item = items.get(0);
        Method target = getMapping(TimesheetController.class, item.targetEndpoint());
        assertThat(target.getAnnotation(RequiresAction.class).value())
                .as("the action the endpoint enforces is the one the menu hides the item by")
                .isEqualTo(item.requiredAction());
        assertThat(TimesheetController.class.getAnnotation(RequiresModule.class).value())
                .isEqualTo(item.requiredModule());
    }

    @Test
    @DisplayName("Projects: a GET of ProjectController under HRMS (menu action is manage, which hr and manager hold)")
    void projectsEndpointIsReal() {
        ItemDefinition item = items.get(1);
        assertThat(getMapping(ProjectController.class, item.targetEndpoint())).isNotNull();
        assertThat(ProjectController.class.getAnnotation(RequiresModule.class).value())
                .isEqualTo(item.requiredModule());
    }

    @Test
    @DisplayName("My work: a GET of ProjectController, behind the same module and action")
    void myWorkEndpointIsRealAndGuardedAsTheItemSays() {
        ItemDefinition item = items.get(2);
        Method target = getMapping(ProjectController.class, item.targetEndpoint());
        assertThat(target.getAnnotation(RequiresAction.class).value()).isEqualTo(item.requiredAction());
        assertThat(ProjectController.class.getAnnotation(RequiresModule.class).value())
                .isEqualTo(item.requiredModule());
    }

    private static void assertItem(
            ItemDefinition item, String key, String label, String path, String endpoint, String action) {
        assertThat(item.key()).isEqualTo(key);
        assertThat(item.labelKey()).isEqualTo(label);
        assertThat(item.path()).isEqualTo(path);
        assertThat(item.targetEndpoint()).isEqualTo(endpoint);
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.HRMS);
        assertThat(item.requiredAction()).isEqualTo(action);
    }

    private static Method getMapping(Class<?> controller, String endpoint) {
        String base = controller.getAnnotation(RequestMapping.class).value()[0];
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .filter(m -> {
                    // A bare @GetMapping maps the base path itself.
                    String[] paths = m.getAnnotation(GetMapping.class).value();
                    return Arrays.stream(paths.length == 0 ? new String[] {""} : paths)
                            .anyMatch(p -> (base + p).equals(endpoint));
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("no GET mapping serves " + endpoint));
    }
}
