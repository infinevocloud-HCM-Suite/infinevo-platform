package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NavigationService} (W-12.3, spec section 7).
 *
 * <p>They run over a catalogue of their own rather than {@link NavigationCatalogue#DEFAULT_ITEMS},
 * because the filtering rules have to be proven for module items too, and the default catalogue
 * holds none until the HRMS and Payroll endpoints ship. The default catalogue is covered end to end
 * by {@code NavigationMatchesEnforcementIT}.
 */
class NavigationServiceTest {

    private static final ItemDefinition EMPLOYEES = new ItemDefinition(
            "core.employee", "nav.employees", "/employees", "/api/v1/employees", null, "core.employee.read");
    private static final ItemDefinition ORG = new ItemDefinition(
            "core.org",
            "nav.organisation",
            "/org/departments",
            "/api/v1/departments",
            null,
            "core.org.read",
            List.of(
                    new ItemDefinition(
                            "core.org.departments",
                            "nav.departments",
                            "/org/departments",
                            "/api/v1/departments",
                            null,
                            "core.org.read"),
                    new ItemDefinition(
                            "core.org.designations",
                            "nav.designations",
                            "/org/designations",
                            "/api/v1/designations",
                            null,
                            "core.org.read")));
    private static final ItemDefinition ROLES =
            new ItemDefinition("core.roles", "nav.roles", "/roles", "/api/v1/roles", null, "core.role.read");
    private static final ItemDefinition TIMESHEETS = new ItemDefinition(
            "hrms.timesheets",
            "nav.timesheets",
            "/timesheets",
            "/api/v1/timesheets",
            PlatformModule.HRMS,
            "hrms.timesheet.read");
    private static final ItemDefinition PAY_RUNS = new ItemDefinition(
            "payroll.runs",
            "nav.payroll",
            "/payroll/runs",
            "/api/v1/payroll/runs",
            PlatformModule.PAYROLL,
            "payroll.run.read");

    private static final List<ItemDefinition> CATALOGUE = List.of(EMPLOYEES, ORG, ROLES, TIMESHEETS, PAY_RUNS);

    private EntitlementService entitlementService;
    private PermissionService permissionService;
    private NavigationService navigationService;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);
        permissionService = mock(PermissionService.class);
        navigationService = new NavigationService(entitlementService, permissionService, CATALOGUE);
    }

    @Test
    @DisplayName("a Payroll-only tenant's feed has no HRMS item, even when the user holds the action")
    void payrollOnlyTenantFeedHasNoHrmsItem() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(false);
        when(permissionService.currentActions())
                .thenReturn(Set.of("core.employee.read", "core.org.read", "hrms.timesheet.read", "payroll.run.read"));

        List<String> keys = keysOf(navigationService.navigation());

        assertThat(keys).containsExactly("core.employee", "core.org", "payroll.runs");
        assertThat(keys).noneMatch(k -> k.startsWith("hrms."));
    }

    @Test
    @DisplayName("an action the user lacks removes its item")
    void actionUserLacksRemovesItsItem() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        when(permissionService.currentActions()).thenReturn(Set.of("core.employee.read", "hrms.timesheet.read"));

        List<String> keys = keysOf(navigationService.navigation());

        assertThat(keys).containsExactly("core.employee", "hrms.timesheets");
        assertThat(keys).doesNotContain("core.roles", "payroll.runs");
    }

    @Test
    @DisplayName("a parent with no visible children is itself hidden")
    void parentWithNoVisibleChildrenIsItselfHidden() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        when(permissionService.currentActions()).thenReturn(Set.of("core.employee.read"));

        List<String> keys = keysOf(navigationService.navigation());

        assertThat(keys).containsExactly("core.employee");
        assertThat(keys).doesNotContain("core.org");
    }

    @Test
    @DisplayName("a parent with a visible child is returned with only that child")
    void parentWithVisibleChildKeepsOnlyVisibleChildren() {
        when(permissionService.currentActions()).thenReturn(Set.of("core.org.read"));

        NavigationResponse response = navigationService.navigation();

        assertThat(keysOf(response)).containsExactly("core.org");
        assertThat(response.items().get(0).children())
                .extracting(NavigationItemResponse::key)
                .containsExactly("core.org.departments", "core.org.designations");
    }

    @Test
    @DisplayName("no actions at all is an empty menu, not a default one")
    void noActionsIsAnEmptyMenu() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        when(permissionService.currentActions()).thenReturn(Set.of());

        NavigationResponse response = navigationService.navigation();

        assertThat(response.items()).isEmpty();
        assertThat(response.actions()).isEmpty();
    }

    @Test
    @DisplayName("actions is exactly the caller's set from PermissionService, and never a role name")
    void actionsIsExactlyTheCallersSetAndNeverRoleNames() {
        Set<String> heldActions = Set.of("core.employee.read", "core.employee.update_own", "payroll.run.read");
        when(permissionService.currentActions()).thenReturn(heldActions);

        NavigationResponse response = navigationService.navigation();

        assertThat(response.actions()).isEqualTo(heldActions);
        assertThat(response.actions())
                .allSatisfy(code -> assertThat(code).matches("^[a-z]+\\.[a-z_]+\\.[a-z_]+$"))
                .noneMatch(code -> Set.of("admin", "tenant-admin", "employee", "hr", "manager", "payroll-officer")
                        .contains(code));
    }

    @Test
    @DisplayName("the default constructor serves the shipped catalogue")
    void defaultConstructorServesTheShippedCatalogue() {
        when(permissionService.currentActions())
                .thenReturn(Set.of("core.org.read", "core.role.read", "core.audit.read"));
        NavigationService shipped = new NavigationService(entitlementService, permissionService);

        List<String> keys = keysOf(shipped.navigation());

        assertThat(keys)
                .containsExactlyElementsOf(NavigationCatalogue.DEFAULT_ITEMS.stream()
                        .map(ItemDefinition::key)
                        .toList());
    }

    private static List<String> keysOf(NavigationResponse response) {
        return response.items().stream().map(NavigationItemResponse::key).toList();
    }
}
