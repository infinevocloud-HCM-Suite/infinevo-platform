package com.infinevo.payroll.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.navigation.NavigationCatalogue;
import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.payroll.component.EarningController;
import com.infinevo.payroll.dashboard.PayrollDashboardController;
import com.infinevo.payroll.deduction.EmployeeDeductionController;
import com.infinevo.payroll.payrun.PayRunController;
import com.infinevo.payroll.reimbursement.ReimbursementClaimController;
import com.infinevo.payroll.schedule.PayScheduleController;
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
    @DisplayName(
            "payroll.prior_payroll targets PriorPayrollController's list GET, behind the same action and the PAYROLL module")
    void priorPayrollItemMatchesTheController() {
        ItemDefinition item = PayrollNavigation.PRIOR_PAYROLL;
        assertThat(item.targetEndpoint()).isEqualTo("/api/v1/payroll/prior-payroll-imports");
        assertThat(item.requiredAction()).isEqualTo("payroll.run.read");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        assertThat(item.path()).isEqualTo("/payroll/prior-payroll");
    }

    @Test
    @DisplayName("the whole menu is the core items followed by payroll's")
    void contributedItemsFollowTheCoreOnes() {
        List<ItemDefinition> all = NavigationCatalogue.withContributed(List.of(new PayrollNavigation()));

        assertThat(all).startsWith(NavigationCatalogue.DEFAULT_ITEMS.toArray(ItemDefinition[]::new));
        assertThat(all).last().isEqualTo(PayrollNavigation.GROUP);
        // D-34: one group under the PAYROLL module, no action of its own, the dashboard first inside it.
        ItemDefinition group = PayrollNavigation.GROUP;
        assertThat(group.key()).isEqualTo("payroll");
        assertThat(group.labelKey()).isEqualTo("nav.payroll");
        assertThat(group.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        assertThat(group.requiredAction()).isNull();
        assertThat(group.children())
                .containsExactly(
                        PayrollNavigation.DASHBOARD,
                        PayrollNavigation.RUNS,
                        PayrollNavigation.PRIOR_PAYROLL,
                        PayrollNavigation.CLAIMS,
                        PayrollNavigation.DEDUCTIONS,
                        PayrollNavigation.SETTINGS,
                        PayrollNavigation.COMPONENTS);
        assertThat(group.path()).isEqualTo(PayrollNavigation.DASHBOARD.path());
    }

    @Test
    @DisplayName("payroll.dashboard targets PayrollDashboardController's GET, behind the same action (W-47.5 §4)")
    void dashboardItemMatchesTheController() {
        ItemDefinition item = PayrollNavigation.DASHBOARD;
        Method summary = Arrays.stream(PayrollDashboardController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class)
                        && m.getAnnotation(GetMapping.class).value().length == 0)
                .findFirst()
                .orElseThrow();

        assertThat(PayrollDashboardController.class
                        .getAnnotation(RequestMapping.class)
                        .value())
                .containsExactly(item.targetEndpoint());
        assertThat(summary.getAnnotation(RequiresAction.class).value()).isEqualTo(item.requiredAction());
        assertThat(item.key()).isEqualTo("payroll.dashboard");
        assertThat(item.labelKey()).isEqualTo("nav.payroll.dashboard");
        assertThat(item.path()).isEqualTo("/payroll/dashboard");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
    }

    @Test
    @DisplayName("payroll.claims targets ReimbursementClaimController's officer list GET, behind the same action")
    void claimsItemMatchesTheController() {
        ItemDefinition item = PayrollNavigation.CLAIMS;
        assertThat(item.key()).isEqualTo("payroll.claims");
        assertThat(item.labelKey()).isEqualTo("nav.payroll.claims");
        assertThat(item.path()).isEqualTo("/payroll/claims");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        assertResolvesToGet(ReimbursementClaimController.class, item);
    }

    @Test
    @DisplayName("payroll.deductions targets EmployeeDeductionController's list GET, behind the same action")
    void deductionsItemMatchesTheController() {
        ItemDefinition item = PayrollNavigation.DEDUCTIONS;
        assertThat(item.key()).isEqualTo("payroll.deductions");
        assertThat(item.labelKey()).isEqualTo("nav.payroll.deductions");
        assertThat(item.path()).isEqualTo("/payroll/deductions");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        assertResolvesToGet(EmployeeDeductionController.class, item);
    }

    @Test
    @DisplayName("D-64: payroll.settings opens /payroll/settings for whoever manages payroll settings")
    void settingsItemLeadsToTheSettingsScreens() {
        ItemDefinition item = PayrollNavigation.SETTINGS;
        assertThat(item.key()).isEqualTo("payroll.settings");
        assertThat(item.labelKey()).isEqualTo("nav.payroll.settings");
        assertThat(item.path()).isEqualTo("/payroll/settings");
        assertThat(item.requiredAction()).isEqualTo("payroll.settings.manage");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        // The settings landing screen is the pay schedule; its GET is what the boot-time check finds.
        assertThat(PayScheduleController.class
                        .getAnnotation(RequestMapping.class)
                        .value())
                .containsExactly(item.targetEndpoint());
        assertThat(Arrays.stream(PayScheduleController.class.getDeclaredMethods())
                        .anyMatch(m -> m.isAnnotationPresent(GetMapping.class)
                                && m.getAnnotation(GetMapping.class).value().length == 0))
                .isTrue();
    }

    @Test
    @DisplayName("D-64: payroll.components opens /payroll/components behind the salary structure read action")
    void componentsItemMatchesTheController() {
        ItemDefinition item = PayrollNavigation.COMPONENTS;
        assertThat(item.key()).isEqualTo("payroll.components");
        assertThat(item.labelKey()).isEqualTo("nav.payroll.components");
        assertThat(item.path()).isEqualTo("/payroll/components");
        assertThat(item.requiredAction()).isEqualTo("payroll.structure.read");
        assertThat(item.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        Method list = Arrays.stream(EarningController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class)
                        && m.getAnnotation(GetMapping.class).value().length == 0)
                .findFirst()
                .orElseThrow();
        assertThat(EarningController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly(item.targetEndpoint());
        assertThat(list.getAnnotation(RequiresAction.class).value()).isEqualTo(item.requiredAction());
    }

    /**
     * What the boot-time catalogue check asks (NavigationCatalogueValidator): some {@code GET} handler maps
     * exactly the item's {@code targetEndpoint}. Here also: that handler asks for the item's action.
     */
    private static void assertResolvesToGet(Class<?> controller, ItemDefinition item) {
        Method handler = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class)
                        && Arrays.asList(m.getAnnotation(GetMapping.class).value())
                                .contains(item.targetEndpoint()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no GET mapping for " + item.targetEndpoint()));
        assertThat(handler.getAnnotation(RequiresAction.class).value()).isEqualTo(item.requiredAction());
    }
}
