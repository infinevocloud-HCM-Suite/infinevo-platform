package com.infinevo.payroll.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.navigation.NavigationCatalogue;
import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.payroll.payrun.PayRunController;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** W-47.2 §5 — the pay run menu item points at the list endpoint and asks for the action that guards it. */
class PayrollNavigationTest {

    @Test
    @DisplayName("payroll.runs targets PayRunController's list GET, behind the same action and the PAYROLL module")
    void runsItemMatchesTheController() {
        ItemDefinition runs = PayrollNavigation.RUNS;
        Method list = Arrays.stream(PayRunController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class)
                        && m.getAnnotation(GetMapping.class).value().length == 0)
                .findFirst()
                .orElseThrow();

        assertThat(PayRunController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly(runs.targetEndpoint());
        assertThat(list.getAnnotation(RequiresAction.class).value()).isEqualTo(runs.requiredAction());
        assertThat(runs.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        assertThat(runs.path()).isEqualTo("/payroll/runs");
    }

    @Test
    @DisplayName("the whole menu is the core items followed by payroll's")
    void contributedItemsFollowTheCoreOnes() {
        List<ItemDefinition> all = NavigationCatalogue.withContributed(List.of(new PayrollNavigation()));

        assertThat(all).startsWith(NavigationCatalogue.DEFAULT_ITEMS.toArray(ItemDefinition[]::new));
        assertThat(all).last().isEqualTo(PayrollNavigation.RUNS);
    }
}
