package com.infinevo.core.user;

import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.RoleService;
import com.infinevo.core.authz.UserRole;
import com.infinevo.core.authz.UserRoleRepository;
import com.infinevo.core.invitation.KeycloakProvisioningService;
import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The tenant's user accounts for the Users &amp; access screen (W-73.4 §4), and Disable / Enable.
 *
 * <p><strong>List.</strong> Three reads however many users: the accounts with their linked employee
 * ({@code core.user_account} left-joined to {@code core.employee} on {@code user_account_id}), every grant in the
 * tenant, and the tenant's roles. All three name the bound tenant and run under its row-level security, so
 * another tenant's users never appear.
 *
 * <p><strong>Disable.</strong> {@code core.user_account.status} becomes {@code DISABLED} in this tenant — from
 * then on the account holds no action ({@code RoleActionRepository.findActionCodesOfUser},
 * {@code core.resolve_impersonation}). Roles stay on record; Enable reverses it.
 *
 * <p><strong>Keycloak.</strong> One Keycloak user can hold an account in several tenants, and its
 * {@code enabled} flag is realm-wide. So Disable sets it {@code false} only when no other tenant holds that
 * Keycloak user ACTIVE, and Enable sets it {@code true} only when no other tenant holds it DISABLED
 * ({@code core.keycloak_user_account_states}, V164); otherwise the per-tenant status alone decides. The call
 * runs inside the transaction, before commit: if Keycloak refuses, nothing is written. If the transaction then
 * rolls back after Keycloak accepted, a synchronization sets the flag back, so Keycloak and the database do not
 * disagree; that revert failing is logged at {@code ERROR}. The permission version is bumped after commit so
 * every replica drops the cached action set at once.
 */
@Service
public class UserDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(UserDirectoryService.class);

    static final String STATUS_DISABLED = "DISABLED";

    /** The audit columns are {@code varchar(100)} (V009). */
    private static final int MAX_ACTOR = 100;

    @PersistenceContext
    private EntityManager entityManager;

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final KeycloakProvisioningService keycloak;
    private final PermissionCache permissionCache;

    public UserDirectoryService(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            RoleService roleService,
            KeycloakProvisioningService keycloak,
            PermissionCache permissionCache) {
        this.userAccountRepository =
                Objects.requireNonNull(userAccountRepository, "userAccountRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleService = Objects.requireNonNull(roleService, "roleService must not be null");
        this.keycloak = Objects.requireNonNull(keycloak, "keycloak must not be null");
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache must not be null");
    }

    /**
     * The bound tenant's accounts, ordered by email. {@code q}, when given, matches email or name, ignoring
     * case.
     */
    @Transactional(readOnly = true)
    public List<UserView> list(String q) {
        UUID tenantId = TenantContext.require();
        String filter = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";

        StringBuilder sql = new StringBuilder(
                """
                select ua.id, ua.email, ua.first_name, ua.last_name, ua.status,
                       e.id, e.employee_number, e.first_name, e.last_name
                  from core.user_account ua
                  left join core.employee e
                    on e.tenant_id = ua.tenant_id
                   and e.user_account_id = ua.id
                   and e.is_deleted = false
                 where ua.tenant_id = :tenantId
                """);
        if (filter != null) {
            sql.append(
                    """
                       and (lower(ua.email) like :q
                            or lower(concat_ws(' ', ua.first_name, ua.last_name)) like :q
                            or lower(concat_ws(' ', e.first_name, e.last_name)) like :q)
                    """);
        }
        sql.append(" order by lower(ua.email), e.employee_number");
        Query query = entityManager.createNativeQuery(sql.toString()).setParameter("tenantId", tenantId);
        if (filter != null) {
            query.setParameter("q", filter);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<UUID, Role> roles = roleRepository.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<UUID, List<UserView.RoleRef>> rolesByUser = new HashMap<>();
        for (UserRole grant : userRoleRepository.findByTenantId(tenantId)) {
            Role role = roles.get(grant.getRoleId());
            if (role != null) {
                rolesByUser
                        .computeIfAbsent(grant.getUserAccountId(), k -> new ArrayList<>())
                        .add(new UserView.RoleRef(role.getId(), role.getCode(), role.getName()));
            }
        }

        // An account linked to two employees (not expected) is listed once, with the first.
        Map<UUID, UserView> views = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            if (views.containsKey(id)) {
                continue;
            }
            String email = (String) row[1];
            String displayName = name((String) row[2], (String) row[3]);
            if (displayName == null) {
                displayName = name((String) row[7], (String) row[8]);
            }
            List<UserView.RoleRef> held = new ArrayList<>(rolesByUser.getOrDefault(id, List.of()));
            held.sort(Comparator.comparing(UserView.RoleRef::code));
            views.put(
                    id,
                    new UserView(
                            id,
                            email,
                            displayName != null ? displayName : email,
                            List.copyOf(held),
                            (UUID) row[5],
                            (String) row[6],
                            UserAccount.STATUS_ACTIVE.equals(row[4])));
        }
        return List.copyOf(views.values());
    }

    /**
     * Disables an account: refused for the caller's own and for the last active {@code tenant-admin}
     * ({@link RoleService#requireCanDisable}). Disabling a disabled account changes nothing.
     */
    @Transactional
    public void disable(UUID userAccountId) {
        UUID tenantId = TenantContext.require();
        UserAccount account = requireAccount(tenantId, userAccountId);
        if (STATUS_DISABLED.equals(account.getStatus())) {
            return;
        }
        roleService.requireCanDisable(userAccountId);
        UUID keycloakUserId = account.getKeycloakUserId();
        setStatus(tenantId, account, STATUS_DISABLED);
        if (statesElsewhere(keycloakUserId, userAccountId)[0] == 0) {
            setKeycloakEnabled(keycloakUserId, false);
        }
        bumpAfterCommit(tenantId);
    }

    /** Enables an account again; its roles were never removed. Enabling an active account changes nothing. */
    @Transactional
    public void enable(UUID userAccountId) {
        UUID tenantId = TenantContext.require();
        UserAccount account = requireAccount(tenantId, userAccountId);
        if (UserAccount.STATUS_ACTIVE.equals(account.getStatus())) {
            return;
        }
        UUID keycloakUserId = account.getKeycloakUserId();
        setStatus(tenantId, account, UserAccount.STATUS_ACTIVE);
        if (statesElsewhere(keycloakUserId, userAccountId)[1] == 0) {
            setKeycloakEnabled(keycloakUserId, true);
        }
        bumpAfterCommit(tenantId);
    }

    /**
     * {@code [active, disabled]}: the other accounts, in any tenant, of this Keycloak user. Read through the
     * SECURITY DEFINER function, since row-level security hides other tenants from this connection.
     */
    private int[] statesElsewhere(UUID keycloakUserId, UUID userAccountId) {
        Object[] row = (Object[]) entityManager
                .createNativeQuery(
                        "select active_elsewhere, disabled_elsewhere from core.keycloak_user_account_states(:kc, :id)")
                .setParameter("kc", keycloakUserId)
                .setParameter("id", userAccountId)
                .getSingleResult();
        return new int[] {((Number) row[0]).intValue(), ((Number) row[1]).intValue()};
    }

    /** Sets the flag now, and sets it back if this transaction then rolls back. */
    private void setKeycloakEnabled(UUID keycloakUserId, boolean enabled) {
        keycloak.setEnabled(keycloakUserId, enabled);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    keycloak.setEnabled(keycloakUserId, !enabled);
                } catch (RuntimeException e) {
                    log.error(
                            "Keycloak user {} left enabled={} after the account change rolled back; set it to {}"
                                    + " by hand",
                            keycloakUserId,
                            enabled,
                            !enabled,
                            e);
                }
            }
        });
    }

    private UserAccount requireAccount(UUID tenantId, UUID userAccountId) {
        Objects.requireNonNull(userAccountId, "userAccountId must not be null");
        return userAccountRepository
                .findById(userAccountId)
                .filter(found -> tenantId.equals(found.getTenantId()))
                .orElseThrow(() -> new RoleService.NotFoundException("No user " + userAccountId + " in this tenant"));
    }

    /**
     * {@code UserAccount} lives in {@code shared} and exposes no status setter — only the token sync writes it —
     * so the one column this screen owns is written here, named by tenant as well as id.
     */
    private void setStatus(UUID tenantId, UserAccount account, String status) {
        int updated = entityManager
                .createNativeQuery(
                        """
                        update core.user_account
                           set status = :status, updated_at = now(), updated_by = :actor
                         where tenant_id = :tenantId and id = :id
                        """)
                .setParameter("status", status)
                .setParameter("actor", currentActor())
                .setParameter("tenantId", tenantId)
                .setParameter("id", account.getId())
                .executeUpdate();
        if (updated != 1) {
            throw new RoleService.NotFoundException("No user " + account.getId() + " in this tenant");
        }
        entityManager.detach(account);
    }

    private void bumpAfterCommit(UUID tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bumpNow(tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bumpNow(tenantId);
            }
        });
    }

    /** Never throws once the change is committed; the permission TTL is the backstop, as in RoleServiceImpl. */
    private void bumpNow(UUID tenantId) {
        try {
            permissionCache.bumpVersion(tenantId);
        } catch (RuntimeException e) {
            log.error(
                    "Permission version bump failed for tenant {} after an account status change; cached"
                            + " permission sets may be stale for up to {}",
                    tenantId,
                    PermissionCache.PERMISSION_TTL,
                    e);
        }
    }

    private static String name(String first, String last) {
        String joined = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
        return joined.isEmpty() ? null : joined;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
