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

    @Override
    public List<ItemDefinition> items() {
        return List.of(TIMESHEETS);
    }
}
