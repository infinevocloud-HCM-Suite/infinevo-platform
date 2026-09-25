package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 */
class NavigationServiceTest {

    private EntitlementService entitlementService;
    private PermissionService permissionService;
    private NavigationService navigationService;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);
        permissionService = mock(PermissionService.class);
        navigationService = new NavigationService(entitlementService, permissionService);
    }

    @Test
    @DisplayName("a Payroll-only tenant's feed has no HRMS item")
    void payrollOnlyTenantFeedHasNoHrmsItem() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(false);
        when(permissionService.currentActions())
                .thenReturn(Set.of("core.employee.read", "core.org.read", "hrms.leave.read", "payroll.run.read"));

        NavigationResponse response = navigationService.navigation();

        List<String> keys =
                response.items().stream().map(NavigationItemResponse::key).toList();
        assertThat(keys).contains("core.employee", "payroll.runs");
        assertThat(keys).noneMatch(k -> k.startsWith("hrms."));
    }

    @Test
    @DisplayName("an action the user lacks removes its item")
    void actionUserLacksRemovesItsItem() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        // User holds employee read, but lacks audit and roles read
        when(permissionService.currentActions()).thenReturn(Set.of("core.employee.read"));

        NavigationResponse response = navigationService.navigation();

        List<String> keys =
                response.items().stream().map(NavigationItemResponse::key).toList();
        assertThat(keys).contains("core.employee");
        assertThat(keys).doesNotContain("core.audit", "core.roles");
    }

    @Test
    @DisplayName("a parent with no visible children is itself hidden")
    void parentWithNoVisibleChildrenIsItselfHidden() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(true);
        // User holds employee read, but lacks core.org.read needed by all children of core.org
        when(permissionService.currentActions()).thenReturn(Set.of("core.employee.read"));

        NavigationResponse response = navigationService.navigation();

        List<String> keys =
                response.items().stream().map(NavigationItemResponse::key).toList();
        assertThat(keys).contains("core.employee");
        assertThat(keys).doesNotContain("core.org");
    }

    @Test
    @DisplayName("actions contains only codes the caller holds, and never a role name")
    void actionsContainsOnlyCodesCallerHoldsAndNeverRoleNames() {
        Set<String> heldActions = Set.of("core.employee.read", "core.employee.update_own", "payroll.run.read");
        when(permissionService.currentActions()).thenReturn(heldActions);

        NavigationResponse response = navigationService.navigation();

        assertThat(response.actions()).isEqualTo(heldActions);
        assertThat(response.actions())
                .allSatisfy(code -> assertThat(code).matches("^[a-z]+\\.[a-z_]+\\.[a-z_]+$"))
                .noneMatch(code -> Set.of("admin", "tenant-admin", "employee", "hr", "manager", "payroll-officer")
                        .contains(code));
    }
}
