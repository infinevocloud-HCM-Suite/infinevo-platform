package com.infinevo.core.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.cache.CacheOperationException;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * W-11.1 — the rules spec section 7 names for roles: a role cannot hold an unknown action, a granted
 * role cannot be deleted, and a system role cannot be edited or deleted. Plus the paths that should
 * succeed, so a service that refused everything would not pass.
 *
 * <p>No Spring context, as {@code docs/CONVENTIONS.md} section 3 requires of a {@code *Test}.
 * Row-level security is {@link RoleRlsIT}'s, against real Postgres.
 */
class RoleServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final Set<String> CATALOGUE = Set.of("core.role.read", "core.org.read", "core.leave.apply");

    private RoleRepository roleRepository;
    private RoleActionRepository roleActionRepository;
    private UserRoleRepository userRoleRepository;
    private ActionRepository actionRepository;
    private UserAccountRepository userAccountRepository;
    private PermissionCache permissionCache;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        roleActionRepository = mock(RoleActionRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        actionRepository = mock(ActionRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        permissionCache = mock(PermissionCache.class);
        service = new RoleServiceImpl(
                roleRepository,
                roleActionRepository,
                userRoleRepository,
                actionRepository,
                userAccountRepository,
                permissionCache);

        // The catalogue stand-in answers findAllById the way the database would: only codes it holds.
        when(actionRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            List<Action> found = new ArrayList<>();
            for (Object code : (Iterable<?>) inv.getArgument(0)) {
                if (CATALOGUE.contains(code)) {
                    found.add(new Action((String) code, "n", ((String) code).split("\\.")[0], null));
                }
            }
            return found;
        });
        when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(inv -> {
            Role role = inv.getArgument(0);
            ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
            return role;
        });

        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ── a role cannot hold an unknown action

    @Test
    @DisplayName("Creating a role with an action the catalogue does not hold is refused, naming every unknown code")
    void createWithUnknownActionRefused() {
        RoleCreateRequest request =
                new RoleCreateRequest(null, "Reviewer", List.of("core.role.read", "core.role.invent", "x.y.z"));

        RoleService.ValidationException e =
                catchThrowableOfType(() -> service.create(request), RoleService.ValidationException.class);

        assertThat(e.fieldErrors()).containsKey("actionCodes");
        assertThat(e.fieldErrors().get("actionCodes"))
                .contains("core.role.invent")
                .contains("x.y.z")
                .doesNotContain("core.role.read");
        verify(roleRepository, never()).saveAndFlush(any());
        verify(roleActionRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("Updating a role to hold an unknown action is refused and the role is not renamed")
    void updateWithUnknownActionRefused() {
        Role role = tenantRole("reviewer", "Reviewer");
        when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.update(role.getId(), new RoleUpdateRequest("Renamed", List.of("core.nope.x"))))
                .isInstanceOf(RoleService.ValidationException.class)
                .hasMessageContaining("core.nope.x");

        assertThat(role.getName()).isEqualTo("Reviewer");
        verify(roleActionRepository, never()).saveAll(anyIterable());
    }

    // ── system roles cannot be edited or deleted

    @Test
    @DisplayName("A system role cannot be edited, whatever the body says")
    void systemRoleCannotBeEdited() {
        Role hr = systemRole("hr", "HR");
        when(roleRepository.findByIdAndTenantId(hr.getId(), TENANT)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.update(hr.getId(), new RoleUpdateRequest("Human Resources", List.of())))
                .isInstanceOf(RoleService.SystemRoleException.class)
                .hasMessageContaining("hr");

        assertThat(hr.getName()).isEqualTo("HR");
        verify(roleActionRepository, never()).deleteAll(anyIterable());
        verify(roleActionRepository, never()).saveAll(anyIterable());
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("A system role cannot be deleted, even when nobody holds it")
    void systemRoleCannotBeDeleted() {
        Role employee = systemRole("employee", "Employee");
        when(roleRepository.findByIdAndTenantId(employee.getId(), TENANT)).thenReturn(Optional.of(employee));
        when(userRoleRepository.countByTenantIdAndRoleId(TENANT, employee.getId()))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.delete(employee.getId())).isInstanceOf(RoleService.SystemRoleException.class);

        verify(roleRepository, never()).delete(any());
    }

    // ── deleting a granted role is refused

    @Test
    @DisplayName("A role granted to a user cannot be deleted")
    void grantedRoleCannotBeDeleted() {
        Role role = tenantRole("reviewer", "Reviewer");
        when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));
        when(userRoleRepository.countByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(role.getId()))
                .isInstanceOf(RoleService.RoleInUseException.class)
                .hasMessageContaining("2 users hold");

        verify(roleRepository, never()).delete(any());
        verify(roleActionRepository, never()).deleteAll(anyIterable());
    }

    @Test
    @DisplayName("A tenant role nobody holds is deleted, its actions first")
    void ungrantedRoleIsDeleted() {
        Role role = tenantRole("reviewer", "Reviewer");
        List<RoleAction> held = List.of(new RoleAction(TENANT, role.getId(), "core.role.read", "t"));
        when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));
        when(userRoleRepository.countByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(0L);
        when(roleActionRepository.findByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(held);

        service.delete(role.getId());

        verify(roleActionRepository).deleteAll(held);
        verify(roleRepository).delete(role);
        verify(roleRepository).flush();
    }

    @Test
    @DisplayName("A role that is not in the bound tenant is not found")
    void deleteUnknownRoleIsNotFound() {
        UUID id = UUID.randomUUID();
        when(roleRepository.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(RoleService.NotFoundException.class);
    }

    // ── happy paths

    @Test
    @DisplayName("Creating a role derives a kebab code from the name, saves it in the bound tenant with its actions")
    @SuppressWarnings("unchecked")
    void createDerivesCodeAndSavesActions() {
        RoleCreateRequest request = new RoleCreateRequest(
                null, "  Payroll Reviewer (EU) ", List.of("core.role.read", "core.org.read", "core.role.read"));

        RoleResponse created = service.create(request);

        assertThat(created.code()).isEqualTo("payroll-reviewer-eu");
        assertThat(created.name()).isEqualTo("Payroll Reviewer (EU)");
        assertThat(created.tenantId()).isEqualTo(TENANT);
        assertThat(created.system()).isFalse();
        assertThat(created.actionCodes()).containsExactly("core.org.read", "core.role.read");

        ArgumentCaptor<Iterable<RoleAction>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(roleActionRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(RoleAction::getActionCode)
                .containsExactlyInAnyOrder("core.role.read", "core.org.read");
        assertThat(saved.getValue()).allSatisfy(ra -> {
            assertThat(ra.getTenantId()).isEqualTo(TENANT);
            assertThat(ra.getRoleId()).isEqualTo(created.id());
        });
    }

    @Test
    @DisplayName("A code given explicitly is used, and a malformed one is refused")
    void explicitCodeUsedAndChecked() {
        assertThat(service.create(new RoleCreateRequest("auditor", "Internal Auditor", List.of()))
                        .code())
                .isEqualTo("auditor");

        RoleService.ValidationException e = catchThrowableOfType(
                () -> service.create(new RoleCreateRequest("Not A Code", "X", List.of())),
                RoleService.ValidationException.class);
        assertThat(e.fieldErrors()).containsKey("code");
    }

    @Test
    @DisplayName("A code the tenant already uses is refused with a conflict")
    void duplicateCodeRefused() {
        when(roleRepository.existsByTenantIdAndCode(TENANT, "hr")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new RoleCreateRequest("hr", "HR again", List.of())))
                .isInstanceOf(RoleService.DuplicateCodeException.class);
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A missing action list is refused rather than read as empty")
    void missingActionListRefused() {
        RoleService.ValidationException e = catchThrowableOfType(
                () -> service.create(new RoleCreateRequest(null, "Reviewer", null)),
                RoleService.ValidationException.class);
        assertThat(e.fieldErrors()).containsKey("actionCodes");
    }

    @Test
    @DisplayName("Updating a tenant role renames it and writes only the actions that changed")
    @SuppressWarnings("unchecked")
    void updateReplacesByDifference() {
        Role role = tenantRole("reviewer", "Reviewer");
        RoleAction keep = new RoleAction(TENANT, role.getId(), "core.role.read", "t");
        RoleAction drop = new RoleAction(TENANT, role.getId(), "core.org.read", "t");
        when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));
        when(roleActionRepository.findByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(List.of(keep, drop));

        RoleResponse updated = service.update(
                role.getId(), new RoleUpdateRequest("Senior Reviewer", List.of("core.role.read", "core.leave.apply")));

        assertThat(updated.name()).isEqualTo("Senior Reviewer");
        assertThat(updated.code()).isEqualTo("reviewer");
        assertThat(updated.actionCodes()).containsExactly("core.leave.apply", "core.role.read");

        ArgumentCaptor<Iterable<RoleAction>> removed = ArgumentCaptor.forClass(Iterable.class);
        verify(roleActionRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(drop);

        ArgumentCaptor<Iterable<RoleAction>> added = ArgumentCaptor.forClass(Iterable.class);
        verify(roleActionRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(RoleAction::getActionCode).containsExactly("core.leave.apply");
    }

    // ── grants

    @Test
    @DisplayName("A grant to a user of another tenant is refused as not found, and nothing is written")
    void grantToForeignUserRefused() {
        UUID userId = UUID.randomUUID();
        UserAccount foreign = mock(UserAccount.class);
        when(foreign.getTenantId()).thenReturn(OTHER_TENANT);
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.replaceUserRoles(userId, new UserRolesRequest(List.of(UUID.randomUUID()))))
                .isInstanceOf(RoleService.NotFoundException.class)
                .hasMessageContaining("user");
        verify(userRoleRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("A grant naming a role the bound tenant does not have is refused, naming the role")
    void grantOfUnknownRoleRefused() {
        UUID userId = userInTenant();
        Role known = tenantRole("reviewer", "Reviewer");
        UUID unknown = UUID.randomUUID();
        when(roleRepository.findByTenantIdAndIdIn(eq(TENANT), anyCollection())).thenReturn(List.of(known));

        assertThatThrownBy(
                        () -> service.replaceUserRoles(userId, new UserRolesRequest(List.of(known.getId(), unknown))))
                .isInstanceOf(RoleService.NotFoundException.class)
                .hasMessageContaining(unknown.toString())
                .hasMessageNotContaining(known.getId().toString());
        verify(userRoleRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("platform-admin cannot be granted from inside a tenant; an existing holder keeps it")
    void platformAdminIsNeverGranted() {
        UUID userId = userInTenant();
        Role platformAdmin = systemRole("platform-admin", "Platform admin");
        when(roleRepository.findByTenantIdAndIdIn(eq(TENANT), anyCollection())).thenReturn(List.of(platformAdmin));
        when(userRoleRepository.findByTenantIdAndUserAccountId(TENANT, userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceUserRoles(userId, new UserRolesRequest(List.of(platformAdmin.getId()))))
                .isInstanceOf(RoleService.SystemRoleException.class)
                .hasMessageContaining("platform-admin");
        verify(userRoleRepository, never()).saveAll(any());

        UserRole held = new UserRole(TENANT, userId, platformAdmin.getId(), "t");
        when(userRoleRepository.findByTenantIdAndUserAccountId(TENANT, userId)).thenReturn(List.of(held));
        UserRolesResponse response =
                service.replaceUserRoles(userId, new UserRolesRequest(List.of(platformAdmin.getId())));
        assertThat(response.roles()).extracting(RoleResponse::code).containsExactly("platform-admin");
    }

    @Test
    @DisplayName("A grant replaces the user's roles by difference")
    @SuppressWarnings("unchecked")
    void grantReplacesByDifference() {
        UUID userId = userInTenant();
        Role keep = tenantRole("reviewer", "Reviewer");
        Role add = systemRole("employee", "Employee");
        UserRole kept = new UserRole(TENANT, userId, keep.getId(), "t");
        UserRole revoked = new UserRole(TENANT, userId, UUID.randomUUID(), "t");
        when(roleRepository.findByTenantIdAndIdIn(eq(TENANT), anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(1);
            return List.of(keep, add).stream()
                    .filter(r -> ids.contains(r.getId()))
                    .toList();
        });
        when(userRoleRepository.findByTenantIdAndUserAccountId(TENANT, userId)).thenReturn(List.of(kept, revoked));

        UserRolesResponse response =
                service.replaceUserRoles(userId, new UserRolesRequest(List.of(keep.getId(), add.getId())));

        assertThat(response.userAccountId()).isEqualTo(userId);
        assertThat(response.roles()).extracting(RoleResponse::code).containsExactly("employee", "reviewer");

        ArgumentCaptor<Iterable<UserRole>> removed = ArgumentCaptor.forClass(Iterable.class);
        verify(userRoleRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(revoked);

        ArgumentCaptor<Iterable<UserRole>> added = ArgumentCaptor.forClass(Iterable.class);
        verify(userRoleRepository).saveAll(added.capture());
        assertThat(added.getValue()).singleElement().satisfies(ur -> {
            assertThat(ur.getRoleId()).isEqualTo(add.getId());
            assertThat(ur.getUserAccountId()).isEqualTo(userId);
            assertThat(ur.getTenantId()).isEqualTo(TENANT);
        });
    }

    @Test
    @DisplayName("An empty grant revokes every role")
    @SuppressWarnings("unchecked")
    void emptyGrantRevokesAll() {
        UUID userId = userInTenant();
        UserRole held = new UserRole(TENANT, userId, UUID.randomUUID(), "t");
        when(userRoleRepository.findByTenantIdAndUserAccountId(TENANT, userId)).thenReturn(List.of(held));

        UserRolesResponse response = service.replaceUserRoles(userId, new UserRolesRequest(List.of()));

        assertThat(response.roles()).isEmpty();
        ArgumentCaptor<Iterable<UserRole>> removed = ArgumentCaptor.forClass(Iterable.class);
        verify(userRoleRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(held);
    }

    @Test
    @DisplayName("With no tenant bound, nothing runs")
    void noTenantNoCall() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.list()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Code derivation: kebab, trimmed, capped at 64")
    void deriveCode() {
        assertThat(RoleServiceImpl.deriveCode("Payroll Officer")).isEqualTo("payroll-officer");
        assertThat(RoleServiceImpl.deriveCode("--HR & Admin--")).isEqualTo("hr-admin");
        assertThat(RoleServiceImpl.deriveCode("!!!")).isEmpty();
        assertThat(RoleServiceImpl.deriveCode("a ".repeat(40)))
                .hasSizeLessThanOrEqualTo(64)
                .doesNotEndWith("-");
    }

    // ── W-11.2: every successful write bumps the tenant's permission version, after commit

    @Test
    @DisplayName("Create bumps the version after commit, not before")
    void createBumpsAfterCommit() {
        inTransaction();

        service.create(new RoleCreateRequest(null, "Reviewer", List.of("core.role.read")));

        verify(permissionCache, never()).bumpVersion(any());
        commit();
        verify(permissionCache, times(1)).bumpVersion(TENANT);
    }

    @Test
    @DisplayName("Update bumps the version after commit, not before")
    void updateBumpsAfterCommit() {
        Role role = tenantRole("reviewer", "Reviewer");
        when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));
        when(roleActionRepository.findByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(List.of());
        inTransaction();

        service.update(role.getId(), new RoleUpdateRequest("Reviewer", List.of("core.org.read")));

        verify(permissionCache, never()).bumpVersion(any());
        commit();
        verify(permissionCache, times(1)).bumpVersion(TENANT);
    }

    @Test
    @DisplayName("Delete bumps the version after commit, not before")
    void deleteBumpsAfterCommit() {
        Role role = tenantRole("reviewer", "Reviewer");
        when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));
        when(userRoleRepository.countByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(0L);
        when(roleActionRepository.findByTenantIdAndRoleId(TENANT, role.getId())).thenReturn(List.of());
        inTransaction();

        service.delete(role.getId());

        verify(permissionCache, never()).bumpVersion(any());
        commit();
        verify(permissionCache, times(1)).bumpVersion(TENANT);
    }

    @Test
    @DisplayName("A grant bumps the version after commit, not before")
    void grantBumpsAfterCommit() {
        UUID userId = userInTenant();
        Role reviewer = tenantRole("reviewer", "Reviewer");
        when(roleRepository.findByTenantIdAndIdIn(eq(TENANT), anyCollection())).thenReturn(List.of(reviewer));
        when(userRoleRepository.findByTenantIdAndUserAccountId(TENANT, userId)).thenReturn(List.of());
        inTransaction();

        service.replaceUserRoles(userId, new UserRolesRequest(List.of(reviewer.getId())));

        verify(permissionCache, never()).bumpVersion(any());
        commit();
        verify(permissionCache, times(1)).bumpVersion(TENANT);
    }

    @Test
    @DisplayName("A refused write registers no bump: unknown action, system role, role in use, platform-admin")
    void refusedWritesDoNotBump() {
        inTransaction();

        assertThatThrownBy(() -> service.create(new RoleCreateRequest(null, "X", List.of("core.nope.x"))))
                .isInstanceOf(RoleService.ValidationException.class);

        Role hr = systemRole("hr", "HR");
        when(roleRepository.findByIdAndTenantId(hr.getId(), TENANT)).thenReturn(Optional.of(hr));
        assertThatThrownBy(() -> service.update(hr.getId(), new RoleUpdateRequest("HR", List.of())))
                .isInstanceOf(RoleService.SystemRoleException.class);

        Role held = tenantRole("reviewer", "Reviewer");
        when(roleRepository.findByIdAndTenantId(held.getId(), TENANT)).thenReturn(Optional.of(held));
        when(userRoleRepository.countByTenantIdAndRoleId(TENANT, held.getId())).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(held.getId())).isInstanceOf(RoleService.RoleInUseException.class);

        UUID userId = userInTenant();
        Role platformAdmin = systemRole("platform-admin", "Platform admin");
        when(roleRepository.findByTenantIdAndIdIn(eq(TENANT), anyCollection())).thenReturn(List.of(platformAdmin));
        when(userRoleRepository.findByTenantIdAndUserAccountId(TENANT, userId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.replaceUserRoles(userId, new UserRolesRequest(List.of(platformAdmin.getId()))))
                .isInstanceOf(RoleService.SystemRoleException.class);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        commit();
        verify(permissionCache, never()).bumpVersion(any());
    }

    @Test
    @DisplayName("A write whose transaction rolls back after it returned never bumps")
    void rolledBackWriteDoesNotBump() {
        inTransaction();

        service.create(new RoleCreateRequest(null, "Reviewer", List.of()));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(permissionCache, never()).bumpVersion(any());
    }

    @Test
    @DisplayName(
            "A bump that fails after commit is logged, not thrown: the committed change is not reported as an error")
    void bumpFailureAfterCommitDoesNotThrow() {
        doThrow(new CacheOperationException("Failed to evict cache key"))
                .when(permissionCache)
                .bumpVersion(TENANT);
        inTransaction();

        service.create(new RoleCreateRequest(null, "Reviewer", List.of()));

        commit(); // would throw if the failure escaped afterCommit
        verify(permissionCache).bumpVersion(TENANT);
    }

    @Test
    @DisplayName("Outside a transaction the bump runs at once")
    void noTransactionBumpsAtOnce() {
        service.create(new RoleCreateRequest(null, "Reviewer", List.of()));

        verify(permissionCache).bumpVersion(TENANT);
    }

    // ── PermissionReadService

    @Test
    @DisplayName("actionsOf asks for the bound tenant's grants of that user, and nothing else")
    void actionsOfUsesTheBoundTenant() {
        UUID userId = UUID.randomUUID();
        when(roleActionRepository.findActionCodesOfUser(TENANT, userId)).thenReturn(Set.of("core.role.read"));

        assertThat(new PermissionReadServiceImpl(roleActionRepository).actionsOf(userId))
                .containsExactly("core.role.read");
    }

    // ── ActionSource, W-11.2

    @Test
    @DisplayName("The ActionSource form queries the explicit tenant when it is the bound one")
    void actionSourceUsesTheExplicitTenant() {
        UUID userId = UUID.randomUUID();
        when(roleActionRepository.findActionCodesOfUser(TENANT, userId)).thenReturn(Set.of("core.org.read"));

        assertThat(new PermissionReadServiceImpl(roleActionRepository).actionsOf(TENANT, userId))
                .containsExactly("core.org.read");
    }

    @Test
    @DisplayName("The ActionSource form refuses a tenant other than the bound one, or none bound, and never queries")
    void actionSourceRefusesAnotherTenant() {
        UUID userId = UUID.randomUUID();
        PermissionReadServiceImpl source = new PermissionReadServiceImpl(roleActionRepository);

        assertThatThrownBy(() -> source.actionsOf(OTHER_TENANT, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OTHER_TENANT.toString());

        TenantContext.clear();
        assertThatThrownBy(() -> source.actionsOf(TENANT, userId)).isInstanceOf(IllegalStateException.class);

        verify(roleActionRepository, never()).findActionCodesOfUser(any(), any());
    }

    /** Opens a transaction's synchronization scope, as {@code @Transactional} would. */
    private static void inTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    /** Runs what a successful commit runs, in order. */
    private static void commit() {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(TransactionSynchronization::afterCommit);
        syncs.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private UUID userInTenant() {
        UUID userId = UUID.randomUUID();
        UserAccount account = mock(UserAccount.class);
        when(account.getTenantId()).thenReturn(TENANT);
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(account));
        return userId;
    }

    private static Role tenantRole(String code, String name) {
        Role role = new Role(TENANT, code, name, "t");
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        return role;
    }

    private static Role systemRole(String code, String name) {
        Role role = tenantRole(code, name);
        ReflectionTestUtils.setField(role, "system", true);
        return role;
    }
}
