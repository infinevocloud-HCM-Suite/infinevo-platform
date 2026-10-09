package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
    @DisplayName("modules is what the tenant holds, whatever the caller may see")
    void modulesIsWhatTheTenantHoldsWhateverTheCallerMaySee() {
        when(entitlementService.holds(PlatformModule.PAYROLL)).thenReturn(true);
        when(entitlementService.holds(PlatformModule.HRMS)).thenReturn(false);
        // No payroll action at all: the payroll item is hidden, the module is still reported.
        when(permissionService.currentActions()).thenReturn(Set.of("core.employee.read"));

        NavigationResponse response = navigationService.navigation();

        assertThat(keysOf(response)).containsExactly("core.employee");
        assertThat(response.modules()).containsExactly(PlatformModule.PAYROLL);
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
                .thenReturn(Set.of(
                        "core.employee.read",
                        "core.org.read",
                        "core.role.read",
                        "core.audit.read",
                        "core.holiday.read",
                        "core.tenant.read",
                        "core.approval.decide",
                        "core.user.manage",
                        "core.employee.create",
                        "core.tenant.provision",
                        "core.leave.read",
                        "core.leave_type.manage"));
        NavigationService shipped = new NavigationService(entitlementService, permissionService);

        List<String> keys = keysOf(shipped.navigation());

        assertThat(keys)
                .containsExactlyElementsOf(NavigationCatalogue.DEFAULT_ITEMS.stream()
                        .map(ItemDefinition::key)
                        .toList());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("the reply names the bound tenant")
    void replyNamesTheBoundTenant() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(tenantId))).thenReturn(List.of("Acme Ltd"));
        when(permissionService.currentActions()).thenReturn(Set.of());

        NavigationService withName = new NavigationService(entitlementService, permissionService, CATALOGUE, jdbc);

        assertThat(withName.navigation().tenantName()).isEqualTo("Acme Ltd");
    }

    @Test
    @DisplayName("W-73.1: with a profile service the reply carries the tenant's tagline and logo link too")
    void replyCarriesBranding() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        TenantProfileService profiles = mock(TenantProfileService.class);
        when(profiles.branding())
                .thenReturn(Optional.of(new TenantBranding("Acme Ltd", "People first", UUID.randomUUID(), "/d?t=x")));
        when(permissionService.currentActions()).thenReturn(Set.of());

        NavigationService branded = new NavigationService(
                entitlementService, permissionService, CATALOGUE, null, null, new PlatformTenant(), profiles);
        NavigationResponse reply = branded.navigation();

        assertThat(reply.tenantName()).isEqualTo("Acme Ltd");
        assertThat(reply.tagline()).isEqualTo("People first");
        assertThat(reply.tenantLogoUrl()).isEqualTo("/d?t=x");
    }

    @Test
    @DisplayName("no bound tenant, no name")
    void noBoundTenantNoName() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(permissionService.currentActions()).thenReturn(Set.of());

        NavigationService withName = new NavigationService(entitlementService, permissionService, CATALOGUE, jdbc);

        assertThat(withName.navigation().tenantName()).isNull();
    }

    // ── D-34: groups

    @Test
    @DisplayName("D-34: the shipped catalogue groups People, Organisation and Settings; leaves keep their keys")
    void shippedCatalogueIsGrouped() {
        assertThat(NavigationCatalogue.DEFAULT_ITEMS)
                .extracting(ItemDefinition::key)
                .containsExactly(
                        "core.admin.home",
                        "core.people",
                        "core.org",
                        "core.approvals",
                        "core.leave",
                        "core.settings",
                        "core.tenants");
        assertThat(childKeys("core.people")).containsExactly("core.employee", "core.users");
        assertThat(childKeys("core.org"))
                .containsExactly(
                        "core.org.departments",
                        "core.org.designations",
                        "core.org.locations",
                        "core.holiday",
                        "core.roles");
        assertThat(childKeys("core.settings")).containsExactly("core.setup", "core.settings.company", "core.audit");
        for (String group : List.of("core.people", "core.org", "core.settings")) {
            ItemDefinition def = group(group);
            assertThat(def.requiredAction())
                    .as("%s shows when a child does", group)
                    .isNull();
            assertThat(def.path())
                    .as("%s opens its first child", group)
                    .isEqualTo(def.children().get(0).path());
            assertThat(def.targetEndpoint()).isEqualTo(def.children().get(0).targetEndpoint());
        }
    }

    @Test
    @DisplayName("D-34: a group shows only the children the caller may see, and opens the first of them")
    void groupShowsVisibleChildrenAndOpensTheFirst() {
        when(permissionService.currentActions()).thenReturn(Set.of("core.holiday.read", "core.audit.read"));
        NavigationService shipped = new NavigationService(entitlementService, permissionService);

        NavigationResponse response = shipped.navigation();

        assertThat(keysOf(response)).containsExactly("core.org", "core.settings");
        NavigationItemResponse org = response.items().get(0);
        assertThat(org.children()).extracting(NavigationItemResponse::key).containsExactly("core.holiday");
        assertThat(org.path())
                .as("not /org/departments, which this caller cannot open")
                .isEqualTo("/holidays");
        assertThat(response.items().get(1).path()).isEqualTo("/audit");
    }

    @Test
    @DisplayName("D-34: a group with no visible child is hidden")
    void groupWithNoVisibleChildIsHidden() {
        when(permissionService.currentActions()).thenReturn(Set.of("core.approval.decide"));
        NavigationService shipped = new NavigationService(entitlementService, permissionService);

        assertThat(keysOf(shipped.navigation())).containsExactly("core.approvals");
    }

    // ── D-35: home path

    private static final ItemDefinition HOME_PEOPLE = new ItemDefinition(
            "core.people", "nav.people", "/employees", "/api/v1/employees", null, null, List.of(EMPLOYEES));
    private static final ItemDefinition HOME_SETTINGS = new ItemDefinition(
            "core.settings",
            "nav.settings",
            "/setup",
            "/api/v1/setup-checklist",
            null,
            null,
            List.of(
                    new ItemDefinition(
                            "core.setup", "nav.setup", "/setup", "/api/v1/setup-checklist", null, "core.tenant.read"),
                    new ItemDefinition("core.audit", "nav.audit", "/audit", "/api/v1/audit", null, "core.audit.read")));
    private static final ItemDefinition HOME_TENANTS = new ItemDefinition(
            "core.tenants", "nav.tenants", "/admin/tenants", "/api/v1/tenants", null, "core.tenant.provision");
    private static final ItemDefinition HOME_PAYROLL_DASHBOARD = new ItemDefinition(
            "payroll.dashboard",
            "nav.payroll.dashboard",
            "/payroll/dashboard",
            "/api/v1/payroll/dashboard",
            PlatformModule.PAYROLL,
            "payroll.run.read");
    private static final ItemDefinition HOME_HRMS_DASHBOARD = new ItemDefinition(
            "hrms.dashboard",
            "nav.hrms.dashboard",
            "/hrms/dashboard",
            "/api/v1/hrms/dashboard",
            PlatformModule.HRMS,
            "hrms.project.read_own");
    private static final List<ItemDefinition> HOME_CATALOGUE =
            List.of(HOME_PEOPLE, HOME_SETTINGS, HOME_TENANTS, HOME_PAYROLL_DASHBOARD, HOME_HRMS_DASHBOARD);

    private static final Set<String> TENANT_ADMIN = Set.of(
            "core.tenant.read",
            "core.tenant.manage",
            "core.audit.read",
            "core.employee.read",
            "payroll.run.read",
            "hrms.project.read_own");
    private static final Set<String> HR = Set.of("core.tenant.read", "core.employee.read", "hrms.project.read_own");
    private static final Set<String> MANAGER = Set.of("core.employee.read_team", "hrms.project.read_own");
    private static final Set<String> EMPLOYEE = Set.of("core.employee.read_own", "hrms.project.read_own");
    private static final Set<String> PAYROLL_OFFICER = Set.of("core.employee.read", "payroll.run.read");

    private String homeFor(Set<String> actions, Set<PlatformModule> modules, UUID tenant, Boolean setupComplete) {
        TenantContext.set(tenant);
        for (PlatformModule module : PlatformModule.values()) {
            when(entitlementService.holds(module)).thenReturn(modules.contains(module));
        }
        when(permissionService.currentActions()).thenReturn(actions);
        SetupChecklistService setup = null;
        if (setupComplete != null) {
            setup = mock(SetupChecklistService.class);
            when(setup.isComplete(tenant)).thenReturn(setupComplete);
        }
        return new NavigationService(
                        entitlementService, permissionService, HOME_CATALOGUE, null, setup, new PlatformTenant())
                .navigation()
                .homePath();
    }

    private static final Set<PlatformModule> BOTH = Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL);

    @Test
    @DisplayName("W-73.2: platform staff land on the platform dashboard when the feed carries it")
    void platformStaffLandOnDashboard() {
        Set<String> staff = Set.of("core.tenant.read", "core.tenant.provision", "core.audit.read");
        ItemDefinition dashboard = new ItemDefinition(
                "core.admin.home",
                "nav.admin.home",
                "/admin",
                "/api/v1/tenants/summary",
                null,
                "core.tenant.provision");
        List<ItemDefinition> catalogue = List.of(dashboard, HOME_PEOPLE, HOME_SETTINGS, HOME_TENANTS);
        for (PlatformModule module : PlatformModule.values()) {
            when(entitlementService.holds(module)).thenReturn(false);
        }
        when(permissionService.currentActions()).thenReturn(staff);
        TenantContext.set(PlatformTenant.DEFAULT_PLATFORM_TENANT_ID);

        NavigationResponse feed = new NavigationService(
                        entitlementService, permissionService, catalogue, null, null, new PlatformTenant())
                .navigation();

        assertThat(feed.homePath()).isEqualTo("/admin");
        assertThat(keysOf(feed).get(0)).isEqualTo("core.admin.home");
    }

    @Test
    @DisplayName("D-35, W-73.2 rollback: with no dashboard item, platform staff land on the tenants screen")
    void platformStaffLandOnTenants() {
        Set<String> staff = Set.of("core.tenant.read", "core.tenant.provision", "core.audit.read");
        assertThat(homeFor(staff, Set.of(), PlatformTenant.DEFAULT_PLATFORM_TENANT_ID, false))
                .as("never the setup checklist, even though it is unfinished")
                .isEqualTo("/admin/tenants");
    }

    @Test
    @DisplayName("D-33, W-73.1, W-73.4: the platform tenant's menu hides Setup, Users & access and Company profile even"
            + " though staff hold their actions")
    void platformTenantHidesCustomerOnlyScreens() {
        Set<String> staff = Set.of("core.tenant.read", "core.tenant.provision", "core.audit.read", "core.user.manage");
        ItemDefinition people = new ItemDefinition(
                "core.people",
                "nav.people",
                "/users",
                "/api/v1/users",
                null,
                null,
                List.of(new ItemDefinition(
                        "core.users", "nav.users", "/users", "/api/v1/users", null, "core.user.manage")));
        ItemDefinition settings = new ItemDefinition(
                "core.settings",
                "nav.settings",
                "/setup",
                "/api/v1/setup-checklist",
                null,
                null,
                List.of(
                        new ItemDefinition(
                                "core.setup",
                                "nav.setup",
                                "/setup",
                                "/api/v1/setup-checklist",
                                null,
                                "core.tenant.read"),
                        new ItemDefinition(
                                "core.settings.company",
                                "nav.settings.company",
                                "/settings/company",
                                "/api/v1/tenants/current/profile",
                                null,
                                "core.tenant.read"),
                        new ItemDefinition(
                                "core.audit", "nav.audit", "/audit", "/api/v1/audit", null, "core.audit.read")));
        List<ItemDefinition> catalogue = List.of(people, settings, HOME_TENANTS);
        for (PlatformModule module : PlatformModule.values()) {
            when(entitlementService.holds(module)).thenReturn(false);
        }
        when(permissionService.currentActions()).thenReturn(staff);

        TenantContext.set(PlatformTenant.DEFAULT_PLATFORM_TENANT_ID);
        NavigationResponse platform = new NavigationService(
                        entitlementService, permissionService, catalogue, null, null, new PlatformTenant())
                .navigation();
        assertThat(keysOf(platform)).containsExactly("core.settings", "core.tenants");
        assertThat(platform.items().get(0).children()).extracting("key").containsExactly("core.audit");

        TenantContext.set(UUID.randomUUID());
        NavigationResponse customer = new NavigationService(
                        entitlementService, permissionService, catalogue, null, null, new PlatformTenant())
                .navigation();
        assertThat(keysOf(customer)).as("a customer tenant keeps them").contains("core.people", "core.settings");
        assertThat(customer.items().get(1).children())
                .extracting("key")
                .containsExactly("core.setup", "core.settings.company", "core.audit");
    }

    @Test
    @DisplayName("D-35: platform staff without the tenants screen land on the first screen they can see")
    void platformStaffWithoutTenantsLandOnTheFirstVisibleScreen() {
        assertThat(homeFor(Set.of("core.audit.read"), Set.of(), PlatformTenant.DEFAULT_PLATFORM_TENANT_ID, null))
                .isEqualTo("/audit");
        assertThat(homeFor(Set.of(), Set.of(), PlatformTenant.DEFAULT_PLATFORM_TENANT_ID, null))
                .isEqualTo("/me");
    }

    @Test
    @DisplayName("D-35: a tenant admin lands on setup until the checklist is complete")
    void tenantAdminLandsOnSetupUntilComplete() {
        assertThat(homeFor(TENANT_ADMIN, BOTH, UUID.randomUUID(), false)).isEqualTo("/setup");
    }

    @Test
    @DisplayName("D-35: a tenant admin with setup complete lands on the module dashboard, payroll first")
    void tenantAdminWithSetupCompleteLandsOnTheDashboard() {
        assertThat(homeFor(TENANT_ADMIN, BOTH, UUID.randomUUID(), true)).isEqualTo("/payroll/dashboard");
        assertThat(homeFor(TENANT_ADMIN, Set.of(PlatformModule.HRMS), UUID.randomUUID(), true))
                .isEqualTo("/hrms/dashboard");
        assertThat(homeFor(TENANT_ADMIN, Set.of(), UUID.randomUUID(), true)).isEqualTo("/employees");
    }

    @Test
    @DisplayName("D-35: with no checklist service a tenant admin is not sent to setup")
    void noChecklistServiceNeverSendsToSetup() {
        assertThat(homeFor(TENANT_ADMIN, BOTH, UUID.randomUUID(), null)).isEqualTo("/payroll/dashboard");
    }

    @Test
    @DisplayName("D-35: a payroll officer lands on the payroll dashboard, or employees without the module")
    void payrollOfficerLandsOnThePayrollDashboard() {
        assertThat(homeFor(PAYROLL_OFFICER, Set.of(PlatformModule.PAYROLL), UUID.randomUUID(), false))
                .isEqualTo("/payroll/dashboard");
        assertThat(homeFor(PAYROLL_OFFICER, Set.of(PlatformModule.HRMS), UUID.randomUUID(), false))
                .as("the dashboard is not in the feed, so it is not home")
                .isEqualTo("/employees");
    }

    @Test
    @DisplayName("D-35: hr lands on the HRMS dashboard, or employees in a payroll-only tenant")
    void hrLandsOnTheHrmsDashboardOrEmployees() {
        assertThat(homeFor(HR, BOTH, UUID.randomUUID(), false)).isEqualTo("/hrms/dashboard");
        assertThat(homeFor(HR, Set.of(PlatformModule.PAYROLL), UUID.randomUUID(), false))
                .isEqualTo("/employees");
    }

    @Test
    @DisplayName("D-35: a manager lands on the HRMS dashboard, or the portal without it")
    void managerLandsOnTheHrmsDashboard() {
        assertThat(homeFor(MANAGER, BOTH, UUID.randomUUID(), false)).isEqualTo("/hrms/dashboard");
        assertThat(homeFor(MANAGER, Set.of(PlatformModule.PAYROLL), UUID.randomUUID(), false))
                .as("nothing else is visible to this fixture, so the portal")
                .isEqualTo("/me");
    }

    @Test
    @DisplayName("D-35: the seeded manager (no hrms.project.read_own) lands on the first screen it can see")
    void seededManagerWithoutTheDashboardLandsOnItsFirstScreen() {
        // V158's manager: reads the team, approves, holds no hrms.project.read_own, so no HRMS dashboard.
        Set<String> seededManager = Set.of(
                "core.employee.read_team",
                "core.org.read",
                "core.leave.read_team",
                "core.leave.approve",
                "core.approval.decide",
                "hrms.project.manage");
        ItemDefinition approvals = new ItemDefinition(
                "core.approvals",
                "nav.approvals",
                "/approvals",
                "/api/v1/approvals/pending",
                null,
                "core.approval.decide");
        List<ItemDefinition> catalogue = List.of(HOME_PEOPLE, approvals, HOME_HRMS_DASHBOARD);
        for (PlatformModule module : PlatformModule.values()) {
            when(entitlementService.holds(module)).thenReturn(true);
        }
        when(permissionService.currentActions()).thenReturn(seededManager);
        TenantContext.set(UUID.randomUUID());

        String home = new NavigationService(
                        entitlementService, permissionService, catalogue, null, null, new PlatformTenant())
                .navigation()
                .homePath();

        assertThat(home).isEqualTo("/approvals");
    }

    @Test
    @DisplayName("D-35: a checklist that cannot be read does not take the feed down; the admin skips setup")
    void brokenChecklistDoesNotBreakTheFeed() {
        TenantContext.set(UUID.randomUUID());
        for (PlatformModule module : PlatformModule.values()) {
            when(entitlementService.holds(module)).thenReturn(true);
        }
        when(permissionService.currentActions()).thenReturn(TENANT_ADMIN);
        SetupChecklistService broken = mock(SetupChecklistService.class);
        when(broken.isComplete(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("db"));

        NavigationResponse response = new NavigationService(
                        entitlementService, permissionService, HOME_CATALOGUE, null, broken, new PlatformTenant())
                .navigation();

        assertThat(response.homePath()).isEqualTo("/payroll/dashboard");
    }

    @Test
    @DisplayName("D-35: an employee lands on the portal, even when the HRMS dashboard is in the feed")
    void employeeLandsOnThePortal() {
        assertThat(homeFor(EMPLOYEE, BOTH, UUID.randomUUID(), false)).isEqualTo("/me");
    }

    private static ItemDefinition group(String key) {
        return NavigationCatalogue.DEFAULT_ITEMS.stream()
                .filter(d -> d.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> childKeys(String key) {
        return group(key).children().stream().map(ItemDefinition::key).toList();
    }

    private static List<String> keysOf(NavigationResponse response) {
        return response.items().stream().map(NavigationItemResponse::key).toList();
    }
}
