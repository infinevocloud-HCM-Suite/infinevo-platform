package com.infinevo.payroll.navigation;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.core.navigation.NavigationContributor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Payroll's menu items (W-47.2 §5). Each is here because its endpoint is in this module: the boot-time
 * catalogue check refuses an item whose {@code targetEndpoint} has no {@code GET} mapping.
 */
@Component
public class PayrollNavigation implements NavigationContributor {

    /** The pay run list — {@code PayRunController}'s {@code GET /api/v1/payroll/payruns}. */
    public static final ItemDefinition RUNS = new ItemDefinition(
            "payroll.runs",
            "nav.payroll.runs",
            "/payroll/runs",
            "/api/v1/payroll/payruns",
            PlatformModule.PAYROLL,
            "payroll.run.read");

    @Override
    public List<ItemDefinition> items() {
        return List.of(RUNS);
    }
}
