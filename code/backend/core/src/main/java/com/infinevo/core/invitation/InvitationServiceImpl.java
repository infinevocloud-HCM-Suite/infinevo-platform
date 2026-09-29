package com.infinevo.core.invitation;

import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.RoleService;
import com.infinevo.core.authz.UserRolesRequest;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserProfileSyncService userProfileSyncService;
    private final KeycloakProvisioningService keycloakProvisioningService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private NotificationService notificationService;

    private final InvitationExpirationService invitationExpirationService;

    public InvitationServiceImpl(
            UserInvitationRepository userInvitationRepository,
            UserInvitationRoleRepository userInvitationRoleRepository,
            EmployeeInvitationRepository employeeInvitationRepository,
            RoleRepository roleRepository,
            RoleService roleService,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            UserProfileSyncService userProfileSyncService,
            KeycloakProvisioningService keycloakProvisioningService,
            JdbcTemplate jdbcTemplate) {
        this(
                userInvitationRepository,
                userInvitationRoleRepository,
                employeeInvitationRepository,
                roleRepository,
                roleService,
                employeeRepository,
                userAccountRepository,
                userProfileSyncService,
                keycloakProvisioningService,
                jdbcTemplate,
                null);
    }

    // The constructor Spring uses. With more than one constructor Spring needs one marked,
    // or it falls back to a no-arg constructor that does not exist.
    @Autowired
    public InvitationServiceImpl(
            UserInvitationRepository userInvitationRepository,
            UserInvitationRoleRepository userInvitationRoleRepository,
            EmployeeInvitationRepository employeeInvitationRepository,
            RoleRepository roleRepository,
            RoleService roleService,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            UserProfileSyncService userProfileSyncService,
            KeycloakProvisioningService keycloakProvisioningService,
            JdbcTemplate jdbcTemplate,
            InvitationExpirationService invitationExpirationService) {
        this.userInvitationRepository = userInvitationRepository;
        this.userInvitationRoleRepository = userInvitationRoleRepository;
        this.employeeInvitationRepository = employeeInvitationRepository;
        this.roleRepository = roleRepository;
        this.roleService = roleService;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.userProfileSyncService = userProfileSyncService;
        this.keycloakProvisioningService = keycloakProvisioningService;
        this.jdbcTemplate = jdbcTemplate;
        this.invitationExpirationService = invitationExpirationService;
    }

    @Override
    public UserInvitationResponse createUserInvitation(UserInvitationRequest request, UUID actorUserId) {
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

        UserInvitation invitation = new UserInvitation(
                tenantId,
                email,
                tokenHash,
                expiresAt,
                actorUserId != null ? actorUserId : UUID.randomUUID(),
                actorUserId != null ? actorUserId.toString() : "system");

        UserInvitation saved = userInvitationRepository.save(invitation);

        for (UUID roleId : roleIds) {
            UserInvitationRole roleJoin = new UserInvitationRole(
                    tenantId, saved.getId(), roleId, actorUserId != null ? actorUserId.toString() : "system");
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
    public UserInvitationResponse resendUserInvitation(UUID invitationId, UUID actorUserId) {
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(invitationId, "invitationId");

        UserInvitation existing = userInvitationRepository
                .findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (existing.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be resent");
        }
        if (existing.isExpired()) {
            existing.setStatus(InvitationStatus.EXPIRED);
            userInvitationRepository.save(existing);
            throw new IllegalStateException("Invitation has expired");
        }

        List<UUID> roles =
                userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, existing.getId()).stream()
                        .map(UserInvitationRole::getRoleId)
                        .toList();

        String newToken = InvitationTokenUtils.generateToken();
        String newTokenHash = InvitationTokenUtils.hashToken(newToken);
        Instant newExpiry = Instant.now().plus(7, ChronoUnit.DAYS);

        UserInvitation newInv = new UserInvitation(
                tenantId,
                existing.getEmail(),
                newTokenHash,
                newExpiry,
                actorUserId != null ? actorUserId : UUID.randomUUID(),
                actorUserId != null ? actorUserId.toString() : "system");

        UserInvitation savedNew = userInvitationRepository.save(newInv);

        for (UUID roleId : roles) {
            userInvitationRoleRepository.save(new UserInvitationRole(
                    tenantId, savedNew.getId(), roleId, actorUserId != null ? actorUserId.toString() : "system"));
        }

        // Revoke the old invitation and link superseded_by
        existing.setStatus(InvitationStatus.REVOKED);
        existing.setRevokedAt(Instant.now());
        existing.setSupersededById(savedNew.getId());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(actorUserId != null ? actorUserId.toString() : "system");
        userInvitationRepository.save(existing);

        sendUserInvitationNotification(savedNew.getEmail(), tenantId, newToken);

        return UserInvitationResponse.from(savedNew, roles);
    }

    @Override
    public void revokeUserInvitation(UUID invitationId, UUID actorUserId) {
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
        existing.setUpdatedBy(actorUserId != null ? actorUserId.toString() : "system");
        userInvitationRepository.save(existing);
    }

    @Override
    public EmployeeInvitationResponse createEmployeeInvitation(EmployeeInvitationRequest request, UUID actorUserId) {
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
                tenantId,
                employee.getId(),
                email,
                tokenHash,
                expiresAt,
                actorUserId != null ? actorUserId : UUID.randomUUID(),
                actorUserId != null ? actorUserId.toString() : "system");

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
    public EmployeeInvitationResponse resendEmployeeInvitation(UUID invitationId, UUID actorUserId) {
        UUID tenantId = requireCurrentTenant();
        Objects.requireNonNull(invitationId, "invitationId");

        EmployeeInvitation existing = employeeInvitationRepository
                .findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (existing.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be resent");
        }
        if (existing.isExpired()) {
            existing.setStatus(InvitationStatus.EXPIRED);
            employeeInvitationRepository.save(existing);
            throw new IllegalStateException("Invitation has expired");
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
                actorUserId != null ? actorUserId : UUID.randomUUID(),
                actorUserId != null ? actorUserId.toString() : "system");

        EmployeeInvitation savedNew = employeeInvitationRepository.save(newInv);

        // Revoke the old invitation and link superseded_by
        existing.setStatus(InvitationStatus.REVOKED);
        existing.setRevokedAt(Instant.now());
        existing.setSupersededById(savedNew.getId());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(actorUserId != null ? actorUserId.toString() : "system");
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
        existing.setUpdatedBy(actorUserId != null ? actorUserId.toString() : "system");
        employeeInvitationRepository.save(existing);
    }

    @Override
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

            if (inv.getStatus() == InvitationStatus.ACCEPTED) {
                throw new IllegalStateException("Invitation has already been accepted");
            }
            if (inv.getStatus() == InvitationStatus.REVOKED) {
                throw new IllegalStateException("Invitation has been revoked");
            }
            if (inv.getStatus() == InvitationStatus.DECLINED) {
                throw new IllegalStateException("Invitation has been declined");
            }
            if (inv.getStatus() == InvitationStatus.EXPIRED || inv.isExpired()) {
                inv.setStatus(InvitationStatus.EXPIRED);
                if (invitationExpirationService != null) {
                    invitationExpirationService.markUserInvitationExpired(tenantId, inv.getId());
                } else {
                    userInvitationRepository.save(inv);
                }
                throw new IllegalStateException("Invitation has expired");
            }

            // 1. Provision or reuse Keycloak user (W-24.2 §13 decision 2)
            KeycloakProvisioningService.ProvisioningResult provisioning =
                    keycloakProvisioningService.getOrCreateKeycloakUserWithStatus(inv.getEmail(), null, null);
            UUID keycloakUserId = provisioning != null
                    ? provisioning.keycloakUserId()
                    : keycloakProvisioningService.getOrCreateKeycloakUser(inv.getEmail(), null, null);
            boolean newlyCreated = provisioning != null && provisioning.newlyCreated();

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

                // 4. Copy invitation roles into core.user_role
                List<UUID> roles =
                        userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, inv.getId()).stream()
                                .map(UserInvitationRole::getRoleId)
                                .toList();
                if (!roles.isEmpty()) {
                    roleService.replaceUserRoles(account.getId(), new UserRolesRequest(roles));
                }

                // 5. Mark invitation accepted
                inv.setStatus(InvitationStatus.ACCEPTED);
                inv.setAcceptedAt(Instant.now());
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("invitation-accept");
                userInvitationRepository.save(inv);
            } catch (Exception e) {
                if (newlyCreated) {
                    try {
                        keycloakProvisioningService.deleteKeycloakUser(keycloakUserId);
                    } catch (Exception cleanupEx) {
                        log.warn(
                                "Failed compensating deletion of Keycloak user {}: {}",
                                keycloakUserId,
                                cleanupEx.getMessage());
                    }
                }
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

            if (inv.getStatus() == InvitationStatus.ACCEPTED) {
                throw new IllegalStateException("Invitation has already been accepted");
            }
            if (inv.getStatus() == InvitationStatus.REVOKED) {
                throw new IllegalStateException("Invitation has been revoked");
            }
            if (inv.getStatus() == InvitationStatus.DECLINED) {
                throw new IllegalStateException("Invitation has been declined");
            }
            if (inv.getStatus() == InvitationStatus.EXPIRED || inv.isExpired()) {
                inv.setStatus(InvitationStatus.EXPIRED);
                if (invitationExpirationService != null) {
                    invitationExpirationService.markEmployeeInvitationExpired(tenantId, inv.getId());
                } else {
                    employeeInvitationRepository.save(inv);
                }
                throw new IllegalStateException("Invitation has expired");
            }

            Employee employee = employeeRepository
                    .findByIdAndTenantIdAndDeletedFalse(inv.getEmployeeId(), tenantId)
                    .orElseThrow(() ->
                            new IllegalStateException("Employee not found for invitation: " + inv.getEmployeeId()));

            // 1. Provision Keycloak user
            KeycloakProvisioningService.ProvisioningResult provisioning =
                    keycloakProvisioningService.getOrCreateKeycloakUserWithStatus(
                            inv.getEmail(), employee.getFirstName(), employee.getLastName());
            UUID keycloakUserId = provisioning != null
                    ? provisioning.keycloakUserId()
                    : keycloakProvisioningService.getOrCreateKeycloakUser(
                            inv.getEmail(), employee.getFirstName(), employee.getLastName());
            boolean newlyCreated = provisioning != null && provisioning.newlyCreated();

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

                // 4. Grant seeded 'employee' role (W-24.2 §6: employee role is the seeded employee role)
                Role employeeRole = roleRepository
                        .findByTenantIdAndCode(tenantId, "employee")
                        .orElseThrow(() ->
                                new IllegalStateException("Seeded 'employee' role not found in tenant " + tenantId));
                roleService.replaceUserRoles(account.getId(), new UserRolesRequest(List.of(employeeRole.getId())));

                // 5. Link employee to user_account_id if supported
                linkEmployeeUserAccount(employee, account.getId());

                // 6. Mark invitation accepted
                inv.setStatus(InvitationStatus.ACCEPTED);
                inv.setAcceptedAt(Instant.now());
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("invitation-accept");
                employeeInvitationRepository.save(inv);
            } catch (Exception e) {
                if (newlyCreated) {
                    try {
                        keycloakProvisioningService.deleteKeycloakUser(keycloakUserId);
                    } catch (Exception cleanupEx) {
                        log.warn(
                                "Failed compensating deletion of Keycloak user {}: {}",
                                keycloakUserId,
                                cleanupEx.getMessage());
                    }
                }
                throw e;
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void linkEmployeeUserAccount(Employee employee, UUID userAccountId) {
        try {
            Method m = Employee.class.getMethod("setUserAccountId", UUID.class);
            m.invoke(employee, userAccountId);
            employeeRepository.save(employee);
        } catch (NoSuchMethodException ignored) {
            // If user_account_id is not yet on Employee entity in this branch, link via SQL
            try {
                jdbcTemplate.update(
                        "UPDATE core.employee SET user_account_id = ? WHERE id = ? AND tenant_id = ?",
                        userAccountId,
                        employee.getId(),
                        employee.getTenantId());
            } catch (Exception e) {
                log.debug("user_account_id column not present on employee table: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn(
                    "Could not link employee {} to user account {}: {}",
                    employee.getId(),
                    userAccountId,
                    e.getMessage());
        }
    }

    @Override
    public void declineInvitation(String token, String reason) {
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
                if (inv.getStatus() == InvitationStatus.EXPIRED || inv.isExpired()) {
                    inv.setStatus(InvitationStatus.EXPIRED);
                    if (invitationExpirationService != null) {
                        invitationExpirationService.markUserInvitationExpired(rawInv.getTenantId(), inv.getId());
                    } else {
                        userInvitationRepository.save(inv);
                    }
                    throw new IllegalStateException("Invitation has expired");
                }
                if (inv.getStatus() != InvitationStatus.PENDING) {
                    throw new IllegalStateException("Invitation is not in pending status");
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
                if (inv.getStatus() == InvitationStatus.EXPIRED || inv.isExpired()) {
                    inv.setStatus(InvitationStatus.EXPIRED);
                    if (invitationExpirationService != null) {
                        invitationExpirationService.markEmployeeInvitationExpired(rawInv.getTenantId(), inv.getId());
                    } else {
                        employeeInvitationRepository.save(inv);
                    }
                    throw new IllegalStateException("Invitation has expired");
                }
                if (inv.getStatus() != InvitationStatus.PENDING) {
                    throw new IllegalStateException("Invitation is not in pending status");
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

    private String getTenantName(UUID tenantId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT name FROM core.tenant WHERE tenant_id = ?", (rs, rowNum) -> rs.getString("name"), tenantId);
            return names.isEmpty() ? "Infinevo" : names.get(0);
        } catch (Exception e) {
            return "Infinevo";
        }
    }

    private void sendUserInvitationNotification(String email, UUID tenantId, String token) {
        if (notificationService == null) {
            return;
        }
        try {
            Map<String, Object> data = Map.of(
                    NotificationService.RECIPIENT_EMAIL,
                    email,
                    "tenant_name",
                    getTenantName(tenantId),
                    "link",
                    "/api/v1/invitations/accept?token=" + token);
            notificationService.compose(NotificationEvent.USER_INVITATION, null, data);
        } catch (Exception e) {
            log.warn("Could not compose user invitation notification: {}", e.getMessage());
        }
    }

    private void sendEmployeeInvitationNotification(String email, UUID tenantId, String token, String employeeName) {
        if (notificationService == null) {
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
                    "/api/v1/invitations/accept?token=" + token);
            notificationService.compose(NotificationEvent.EMPLOYEE_INVITATION, null, data);
        } catch (Exception e) {
            log.warn("Could not compose employee invitation notification: {}", e.getMessage());
        }
    }
}
