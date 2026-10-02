package com.infinevo.hrms.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
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
 * W-42.1 §5 and §8: the {@code hrms.timesheets} menu item is backed by a real endpoint, with the module and action that
 * endpoint enforces.
 *
 * <p>The boot-time {@code NavigationCatalogueValidator} refuses to start the application when a leaf's endpoint has no
 * {@code GET} mapping, but it only runs inside the whole application. This asks the same question of the controller
 * directly, so a renamed path or a changed guard fails here, in the module's own build, and not at the next boot.
 */
class HrmsNavigationTest {

    private final List<ItemDefinition> items = new HrmsNavigation().items();

    @Test
    @DisplayName("HRMS contributes exactly one item, hrms.timesheets, as the spec's table says")
    void oneItem() {
        assertThat(items).hasSize(1);
        ItemDefinition item = items.get(0);

        assertThat(item.key()).isEqualTo("hrms.timesheets");
        assertThat(item.labelKey()).isEqualTo("nav.hrms.timesheets");
        assertThat(item.path()).isEqualTo("/hrms/timesheets");
        assertThat(item.targetEndpoint()).isEqualTo("/api/v1/hrms/timesheets/mine");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.HRMS);
        assertThat(item.requiredAction()).isEqualTo("hrms.timesheet.read_own");
    }

    @Test
    @DisplayName("Its endpoint is a GET mapping of TimesheetController, behind the same module and action")
    void endpointIsRealAndGuardedAsTheItemSays() {
        ItemDefinition item = items.get(0);
        String base =
                TimesheetController.class.getAnnotation(RequestMapping.class).value()[0];

        Method target = Arrays.stream(TimesheetController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .filter(m -> (base + m.getAnnotation(GetMapping.class).value()[0]).equals(item.targetEndpoint()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no GET mapping serves " + item.targetEndpoint()));

        assertThat(target.getAnnotation(RequiresAction.class).value())
                .as("the action the endpoint enforces is the one the menu hides the item by")
                .isEqualTo(item.requiredAction());
        assertThat(TimesheetController.class.getAnnotation(RequiresModule.class).value())
                .isEqualTo(item.requiredModule());
    }
}
