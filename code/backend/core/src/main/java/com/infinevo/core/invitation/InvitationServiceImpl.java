package com.infinevo.core.invitation;

import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.RoleService;
import com.infinevo.core.authz.UserRole;
import com.infinevo.core.authz.UserRoleRepository;
import com.infinevo.core.authz.UserRolesRequest;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link InvitationService} (W-24.2).
 */
@Service
@Transactional
public class InvitationServiceImpl implements InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationServiceImpl.class);

    private final UserInvitationRepository userInvitationRepository;
    private final UserInvitationRoleRepository userInvitationRoleRepository;
    private final EmployeeInvitationRepository employeeInvitationRepository;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final UserRoleRepository userRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserProfileSyncService userProfileSyncService;
    private final KeycloakProvisioningService keycloakProvisioningService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * The frontend page the invitation email links to, absolute, e.g.
     * {@code https://app.example.com/invitations/accept}. The token is appended as {@code ?token=}. Set by
     * {@code INVITATION_LINK_BASE_URL}; with none, no invitation email is composed.
     */
    private final String linkBaseUrl;

    @Autowired(required = false)
    private NotificationService notificationService;

    public InvitationServiceImpl(
            UserInvitationRepository userInvitationRepository,
            UserInvitationRoleRepository userInvitationRoleRepository,
            EmployeeInvitationRepository employeeInvitationRepository,
            RoleRepository roleRepository,
            RoleService roleService,
            UserRoleRepository userRoleRepository,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            UserProfileSyncService userProfileSyncService,
            KeycloakProvisioningService keycloakProvisioningService,
            JdbcTemplate jdbcTemplate,
            @Value("${invitation.link.base-url:}") String linkBaseUrl) {
        this.userInvitationRepository = userInvitationRepository;
        this.userInvitationRoleRepository = userInvitationRoleRepository;
        this.employeeInvitationRepository = employeeInvitationRepository;
        this.roleRepository = roleRepository;
        this.roleService = roleService;
        this.userRoleRepository = userRoleRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.userProfileSyncService = userProfileSyncService;
        this.keycloakProvisioningService = keycloakProvisioningService;
        this.jdbcTemplate = jdbcTemplate;
        this.linkBaseUrl = linkBaseUrl == null ? "" : linkBaseUrl.trim();
    }

    @Override
    public UserInvitationResponse createUserInvitation(UserInvitationRequest request, UUID actorUserId) {
        requireActor(actorUserId);
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(request, "request");
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        String email = request.email().trim().toLowerCase();

        Set<UUID> roleIds = request.roleIds() != null ? request.roleIds() : Set.of();
        // Validate roles exist in tenant
        for (UUID roleId : roleIds) {
            roleRepository
                    .findByIdAndTenantId(roleId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found in tenant: " + roleId));
        }

        // Active check: reject if there is already a live PENDING invitation for this email in this tenant
        userInvitationRepository
                .findActivePendingByEmail(tenantId, email, Instant.now())
                .ifPresent(inv -> {
                    throw new IllegalStateException("An active pending invitation already exists for " + email);
                });

        String rawToken = InvitationTokenUtils.generateToken();
        String tokenHash = InvitationTokenUtils.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        UserInvitation invitation =
                new UserInvitation(tenantId, email, tokenHash, expiresAt, actorUserId, actorUserId.toString());

        UserInvitation saved = userInvitationRepository.save(invitation);

        for (UUID roleId : roleIds) {
            UserInvitationRole roleJoin =
                    new UserInvitationRole(tenantId, saved.getId(), roleId, actorUserId.toString());
            userInvitationRoleRepository.save(roleJoin);
        }

        sendUserInvitationNotification(email, tenantId, rawToken);

        return UserInvitationResponse.from(saved, new ArrayList<>(roleIds));
    }

    @Override
    public List<UserInvitationResponse> listUserInvitations(InvitationStatus status) {
        UUID tenantId = requireCurrentTenant();
        List<UserInvitation> invitations = (status != null)
                ? userInvitationRepository.findByTenantIdAndStatus(tenantId, status)
                : userInvitationRepository.findByTenantId(tenantId);

        List<UserInvitationResponse> responses = new ArrayList<>();
        for (UserInvitation inv : invitations) {
            List<UUID> roles =
                    userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, inv.getId()).stream()
                            .map(UserInvitationRole::getRoleId)
                            .toList();
            responses.add(UserInvitationResponse.from(inv, roles));
        }
        return responses;
    }

    @Override
    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public UserInvitationResponse resendUserInvitation(UUID invitationId, UUID actorUserId) {
        requireActor(actorUserId);
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(invitationId, "invitationId");

        UserInvitation existing = userInvitationRepository
                .findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (existing.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be resent");
        }
        if (existing.isExpired()) {
            markExpired(existing);
            userInvitationRepository.saveAndFlush(existing);
            throw new InvitationExpiredException();
        }

        List<UUID> roles =
                userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, existing.getId()).stream()
                        .map(UserInvitationRole::getRoleId)
                        .toList();

        String newToken = InvitationTokenUtils.generateToken();
        String newTokenHash = InvitationTokenUtils.hashToken(newToken);
        Instant newExpiry = Instant.now().plus(7, ChronoUnit.DAYS);

        UserInvitation newInv = new UserInvitation(
                tenantId, existing.getEmail(), newTokenHash, newExpiry, actorUserId, actorUserId.toString());

        UserInvitation savedNew = userInvitationRepository.save(newInv);

        for (UUID roleId : roles) {
            userInvitationRoleRepository.save(
                    new UserInvitationRole(tenantId, savedNew.getId(), roleId, actorUserId.toString()));
        }

        // Revoke the old invitation and link superseded_by
        existing.setStatus(InvitationStatus.REVOKED);
        existing.setRevokedAt(Instant.now());
        existing.setSupersededById(savedNew.getId());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(actorUserId.toString());
        userInvitationRepository.save(existing);

        sendUserInvitationNotification(savedNew.getEmail(), tenantId, newToken);

        return UserInvitationResponse.from(savedNew, roles);
    }

    @Override
    public void revokeUserInvitation(UUID invitationId, UUID actorUserId) {
        requireActor(actorUserId);
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(invitationId, "invitationId");

        UserInvitation existing = userInvitationRepository
                .findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (existing.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be revoked");
        }

        existing.setStatus(InvitationStatus.REVOKED);
        existing.setRevokedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(actorUserId.toString());
        userInvitationRepository.save(existing);
    }

    @Override
    public EmployeeInvitationResponse createEmployeeInvitation(EmployeeInvitationRequest request, UUID actorUserId) {
        requireActor(actorUserId);
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(request, "request");
        if (request.employeeId() == null) {
            throw new IllegalArgumentException("employeeId is required");
        }

        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(request.employeeId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + request.employeeId()));

        String email = employee.getWorkEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Employee has no work email address configured");
        }
        email = email.trim().toLowerCase();

        // Active check: reject if there is already a live PENDING invitation for this employee
        employeeInvitationRepository
                .findActivePendingByEmployeeId(tenantId, employee.getId(), Instant.now())
                .ifPresent(inv -> {
                    throw new IllegalStateException("An active pending invitation already exists for this employee");
                });

        String rawToken = InvitationTokenUtils.generateToken();
        String tokenHash = InvitationTokenUtils.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        EmployeeInvitation invitation = new EmployeeInvitation(
                tenantId, employee.getId(), email, tokenHash, expiresAt, actorUserId, actorUserId.toString());

        EmployeeInvitation saved = employeeInvitationRepository.save(invitation);

        String employeeName =
                (employee.getFirstName() + (employee.getLastName() != null ? " " + employee.getLastName() : "")).trim();
        if (employeeName.isBlank()) {
            employeeName = email;
        }
        sendEmployeeInvitationNotification(email, tenantId, rawToken, employeeName);

        return EmployeeInvitationResponse.from(saved);
    }

    @Override
    public List<EmployeeInvitationResponse> listEmployeeInvitations(InvitationStatus status, UUID employeeId) {
        UUID tenantId = requireCurrentTenant();
        List<EmployeeInvitation> invitations;

        if (status != null && employeeId != null) {
            invitations =
                    employeeInvitationRepository.findByTenantIdAndStatusAndEmployeeId(tenantId, status, employeeId);
        } else if (status != null) {
            invitations = employeeInvitationRepository.findByTenantIdAndStatus(tenantId, status);
        } else if (employeeId != null) {
            invitations = employeeInvitationRepository.findByTenantIdAndEmployeeId(tenantId, employeeId);
        } else {
            invitations = employeeInvitationRepository.findByTenantId(tenantId);
        }

        return invitations.stream().map(EmployeeInvitationResponse::from).toList();
    }

    @Override
    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public EmployeeInvitationResponse resendEmployeeInvitation(UUID invitationId, UUID actorUserId) {
        requireActor(actorUserId);
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(invitationId, "invitationId");

        EmployeeInvitation existing = employeeInvitationRepository
                .findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (existing.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be resent");
        }
        if (existing.isExpired()) {
            markExpired(existing);
            employeeInvitationRepository.saveAndFlush(existing);
            throw new InvitationExpiredException();
        }

        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(existing.getEmployeeId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + existing.getEmployeeId()));

        String newToken = InvitationTokenUtils.generateToken();
        String newTokenHash = InvitationTokenUtils.hashToken(newToken);
        Instant newExpiry = Instant.now().plus(7, ChronoUnit.DAYS);

        EmployeeInvitation newInv = new EmployeeInvitation(
                tenantId,
                existing.getEmployeeId(),
                employee.getWorkEmail().trim().toLowerCase(),
                newTokenHash,
                newExpiry,
                actorUserId,
                actorUserId.toString());

        EmployeeInvitation savedNew = employeeInvitationRepository.save(newInv);

        // Revoke the old invitation and link superseded_by
        existing.setStatus(InvitationStatus.REVOKED);
        existing.setRevokedAt(Instant.now());
        existing.setSupersededById(savedNew.getId());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(actorUserId.toString());
        employeeInvitationRepository.save(existing);

        String employeeName =
                (employee.getFirstName() + (employee.getLastName() != null ? " " + employee.getLastName() : "")).trim();
        if (employeeName.isBlank()) {
            employeeName = savedNew.getEmail();
        }
        sendEmployeeInvitationNotification(savedNew.getEmail(), tenantId, newToken, employeeName);

        return EmployeeInvitationResponse.from(savedNew);
    }

    @Override
    public void revokeEmployeeInvitation(UUID invitationId, UUID actorUserId) {
        requireActor(actorUserId);
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(invitationId, "invitationId");

        EmployeeInvitation existing = employeeInvitationRepository
                .findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (existing.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be revoked");
        }

        existing.setStatus(InvitationStatus.REVOKED);
        existing.setRevokedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(actorUserId.toString());
        employeeInvitationRepository.save(existing);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code noRollbackFor}: an expired token is marked {@code EXPIRED} inside this transaction — the one
     * holding the row lock — and that change must commit even though the request fails.
     */
    @Override
    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public void acceptInvitation(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invitation token must not be blank");
        }

        String tokenHash = InvitationTokenUtils.hashToken(token);

        // Try user invitation first (security definer bypasses RLS)
        Optional<UserInvitation> userInvOpt = userInvitationRepository.findByTokenHashSecurityDefiner(tokenHash);
        if (userInvOpt.isPresent()) {
            acceptUserInvitation(userInvOpt.get());
            return;
        }

        // Try employee invitation (security definer bypasses RLS)
        Optional<EmployeeInvitation> empInvOpt = employeeInvitationRepository.findByTokenHashSecurityDefiner(tokenHash);
        if (empInvOpt.isPresent()) {
            acceptEmployeeInvitation(empInvOpt.get());
            return;
        }

        throw new IllegalArgumentException("Invalid invitation token");
    }

    private void acceptUserInvitation(UserInvitation rawInv) {
        UUID tenantId = rawInv.getTenantId();
        TenantContext.set(tenantId);
        try {
            UserInvitation inv = userInvitationRepository
                    .findByIdForUpdate(rawInv.getId(), tenantId)
                    .orElse(rawInv);

            refuseUnlessPending(inv.getStatus());
            if (inv.isExpired()) {
                markExpired(inv);
                userInvitationRepository.saveAndFlush(inv);
                throw new InvitationExpiredException();
            }

            // 1. Provision or reuse Keycloak user (W-24.2 §13 decision 2)
            KeycloakProvisioningService.ProvisioningResult provisioning =
                    keycloakProvisioningService.getOrCreateKeycloakUser(inv.getEmail(), null, null);
            UUID keycloakUserId = provisioning.keycloakUserId();

            try {
                // 2. Provision or sync core.user_account
                userProfileSyncService.sync(tenantId, keycloakUserId, inv.getEmail(), null, null);
                UserAccount account = userAccountRepository
                        .findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Failed to find or create user account for " + keycloakUserId));

                // 3. Provision core.user_tenant mapping
                jdbcTemplate.update(
                        "INSERT INTO core.user_tenant (tenant_id, user_id, created_by, updated_by) VALUES (?, ?, 'invitation-accept', 'invitation-accept') ON CONFLICT (user_id, tenant_id) DO NOTHING",
                        tenantId,
                        keycloakUserId);

                // 4. Add the invitation's roles to whatever the account already holds
                List<UUID> roles =
                        userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, inv.getId()).stream()
                                .map(UserInvitationRole::getRoleId)
                                .toList();
                grantRoles(tenantId, account.getId(), roles);

                // 5. Mark invitation accepted
                inv.setStatus(InvitationStatus.ACCEPTED);
                inv.setAcceptedAt(Instant.now());
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("invitation-accept");
                userInvitationRepository.save(inv);
            } catch (RuntimeException e) {
                compensate(provisioning);
                throw e;
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void acceptEmployeeInvitation(EmployeeInvitation rawInv) {
        UUID tenantId = rawInv.getTenantId();
        TenantContext.set(tenantId);
        try {
            EmployeeInvitation inv = employeeInvitationRepository
                    .findByIdForUpdate(rawInv.getId(), tenantId)
                    .orElse(rawInv);

            refuseUnlessPending(inv.getStatus());
            if (inv.isExpired()) {
                markExpired(inv);
                employeeInvitationRepository.saveAndFlush(inv);
                throw new InvitationExpiredException();
            }

            Employee employee = employeeRepository
                    .findByIdAndTenantIdAndDeletedFalse(inv.getEmployeeId(), tenantId)
                    .orElseThrow(() ->
                            new IllegalStateException("Employee not found for invitation: " + inv.getEmployeeId()));

            // 1. Provision Keycloak user
            KeycloakProvisioningService.ProvisioningResult provisioning =
                    keycloakProvisioningService.getOrCreateKeycloakUser(
                            inv.getEmail(), employee.getFirstName(), employee.getLastName());
            UUID keycloakUserId = provisioning.keycloakUserId();

            try {
                // 2. Provision core.user_account
                userProfileSyncService.sync(
                        tenantId, keycloakUserId, inv.getEmail(), employee.getFirstName(), employee.getLastName());
                UserAccount account = userAccountRepository
                        .findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Failed to find or create user account for " + keycloakUserId));

                // 3. Provision core.user_tenant mapping
                jdbcTemplate.update(
                        "INSERT INTO core.user_tenant (tenant_id, user_id, created_by, updated_by) VALUES (?, ?, 'invitation-accept', 'invitation-accept') ON CONFLICT (user_id, tenant_id) DO NOTHING",
                        tenantId,
                        keycloakUserId);

                // 4. Add the seeded 'employee' role (W-24.2 §6) to whatever the account already holds
                Role employeeRole = roleRepository
                        .findByTenantIdAndCode(tenantId, "employee")
                        .orElseThrow(() ->
                                new IllegalStateException("Seeded 'employee' role not found in tenant " + tenantId));
                grantRoles(tenantId, account.getId(), List.of(employeeRole.getId()));

                // 5. Link the employee to the account
                employee.setUserAccountId(account.getId());
                employeeRepository.save(employee);

                // 6. Mark invitation accepted
                inv.setStatus(InvitationStatus.ACCEPTED);
                inv.setAcceptedAt(Instant.now());
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("invitation-accept");
                employeeInvitationRepository.save(inv);
            } catch (RuntimeException e) {
                compensate(provisioning);
                throw e;
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Adds {@code roleIds} to the account's current roles — never replaces them. An account reused from
     * another invitation (§13 decision 2) keeps what it already holds in this tenant.
     */
    private void grantRoles(UUID tenantId, UUID userAccountId, Collection<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        Set<UUID> union = new LinkedHashSet<>();
        for (UserRole held : userRoleRepository.findByTenantIdAndUserAccountId(tenantId, userAccountId)) {
            union.add(held.getRoleId());
        }
        if (union.containsAll(roleIds)) {
            return;
        }
        union.addAll(roleIds);
        roleService.replaceUserRoles(userAccountId, new UserRolesRequest(new ArrayList<>(union)));
    }

    /** Deletes a Keycloak user this acceptance created, so a failed acceptance leaves no orphan. */
    private void compensate(KeycloakProvisioningService.ProvisioningResult provisioning) {
        if (!provisioning.newlyCreated()) {
            return;
        }
        try {
            keycloakProvisioningService.deleteKeycloakUser(provisioning.keycloakUserId());
        } catch (RuntimeException cleanupEx) {
            log.warn(
                    "Failed compensating deletion of Keycloak user {}: {}",
                    provisioning.keycloakUserId(),
                    cleanupEx.getMessage());
        }
    }

    /** Accepted, revoked, declined and expired are all final; the caller learns only that it is refused. */
    private static void refuseUnlessPending(InvitationStatus status) {
        switch (status) {
            case PENDING -> {}
            case ACCEPTED -> throw new IllegalStateException("Invitation has already been accepted");
            case REVOKED -> throw new IllegalStateException("Invitation has been revoked");
            case DECLINED -> throw new IllegalStateException("Invitation has been declined");
            case EXPIRED -> throw new InvitationExpiredException();
        }
    }

    private static void markExpired(UserInvitation inv) {
        inv.setStatus(InvitationStatus.EXPIRED);
        inv.setUpdatedAt(Instant.now());
        inv.setUpdatedBy("system");
    }

    private static void markExpired(EmployeeInvitation inv) {
        inv.setStatus(InvitationStatus.EXPIRED);
        inv.setUpdatedAt(Instant.now());
        inv.setUpdatedBy("system");
    }

    /** {@code noRollbackFor} for the reason {@link #acceptInvitation(String)} gives. */
    @Override
    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public void declineInvitation(String token, String reason) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invitation token must not be blank");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Decline reason must not be blank");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("Decline reason cannot exceed 500 characters");
        }

        String tokenHash = InvitationTokenUtils.hashToken(token);

        Optional<UserInvitation> userInvOpt = userInvitationRepository.findByTokenHashSecurityDefiner(tokenHash);
        if (userInvOpt.isPresent()) {
            UserInvitation rawInv = userInvOpt.get();
            TenantContext.set(rawInv.getTenantId());
            try {
                UserInvitation inv = userInvitationRepository
                        .findByIdForUpdate(rawInv.getId(), rawInv.getTenantId())
                        .orElse(rawInv);
                refuseUnlessPending(inv.getStatus());
                if (inv.isExpired()) {
                    markExpired(inv);
                    userInvitationRepository.saveAndFlush(inv);
                    throw new InvitationExpiredException();
                }
                inv.setStatus(InvitationStatus.DECLINED);
                inv.setDeclinedAt(Instant.now());
                inv.setDeclineReason(reason.trim());
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("invitation-decline");
                userInvitationRepository.save(inv);
                return;
            } finally {
                TenantContext.clear();
            }
        }

        Optional<EmployeeInvitation> empInvOpt = employeeInvitationRepository.findByTokenHashSecurityDefiner(tokenHash);
        if (empInvOpt.isPresent()) {
            EmployeeInvitation rawInv = empInvOpt.get();
            TenantContext.set(rawInv.getTenantId());
            try {
                EmployeeInvitation inv = employeeInvitationRepository
                        .findByIdForUpdate(rawInv.getId(), rawInv.getTenantId())
                        .orElse(rawInv);
                refuseUnlessPending(inv.getStatus());
                if (inv.isExpired()) {
                    markExpired(inv);
                    employeeInvitationRepository.saveAndFlush(inv);
                    throw new InvitationExpiredException();
                }
                inv.setStatus(InvitationStatus.DECLINED);
                inv.setDeclinedAt(Instant.now());
                inv.setDeclineReason(reason.trim());
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("invitation-decline");
                employeeInvitationRepository.save(inv);
                return;
            } finally {
                TenantContext.clear();
            }
        }

        throw new IllegalArgumentException("Invalid invitation token");
    }

    private UUID requireCurrentTenant() {
        return TenantContext.require();
    }

    /**
     * The audit column {@code invited_by_user_id} is {@code NOT NULL} and must name a real person. With no
     * authenticated actor the request is refused rather than stamped with an invented id.
     */
    private static void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new IllegalStateException("No authenticated user could be resolved to record as the inviter");
        }
    }

    private String getTenantName(UUID tenantId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT name FROM core.tenant WHERE tenant_id = ?", (rs, rowNum) -> rs.getString("name"), tenantId);
            return names.isEmpty() ? "Infinevo" : names.get(0);
        } catch (Exception e) {
            return "Infinevo";
        }
    }

    /**
     * The absolute link the invitee opens: the frontend accept page, never the POST API path. Null when
     * {@code invitation.link.base-url} is not configured.
     */
    String invitationLink(String token) {
        if (linkBaseUrl.isEmpty()) {
            return null;
        }
        return linkBaseUrl + (linkBaseUrl.contains("?") ? "&" : "?") + "token=" + token;
    }

    @Override
    public boolean canSendInvitationEmail() {
        return notificationService != null && !linkBaseUrl.isEmpty();
    }

    private void sendUserInvitationNotification(String email, UUID tenantId, String token) {
        if (notificationService == null) {
            return;
        }
        String link = invitationLink(token);
        if (link == null) {
            log.error(
                    "User invitation email not composed: invitation.link.base-url (INVITATION_LINK_BASE_URL) is not set");
            return;
        }
        try {
            Map<String, Object> data = Map.of(
                    NotificationService.RECIPIENT_EMAIL, email, "tenant_name", getTenantName(tenantId), "link", link);
            notificationService.compose(NotificationEvent.USER_INVITATION, null, data);
        } catch (Exception e) {
            // The exception class only: a message could quote the rendered link, and with it the token.
            log.warn(
                    "Could not compose user invitation notification: {}",
                    e.getClass().getSimpleName());
        }
    }

    private void sendEmployeeInvitationNotification(String email, UUID tenantId, String token, String employeeName) {
        if (notificationService == null) {
            return;
        }
        String link = invitationLink(token);
        if (link == null) {
            log.error(
                    "Employee invitation email not composed: invitation.link.base-url (INVITATION_LINK_BASE_URL) is not set");
            return;
        }
        try {
            Map<String, Object> data = Map.of(
                    NotificationService.RECIPIENT_EMAIL,
                    email,
                    "employee_name",
                    employeeName != null && !employeeName.isBlank() ? employeeName : email,
                    "tenant_name",
                    getTenantName(tenantId),
                    "link",
                    link);
            notificationService.compose(NotificationEvent.EMPLOYEE_INVITATION, null, data);
        } catch (Exception e) {
            log.warn(
                    "Could not compose employee invitation notification: {}",
                    e.getClass().getSimpleName());
        }
    }
}
