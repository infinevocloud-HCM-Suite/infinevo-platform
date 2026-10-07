package com.infinevo.core.tenant;

import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.invitation.UserInvitationRequest;
import com.infinevo.core.invitation.UserInvitationResponse;
import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TenantService} (W-12.1).
 *
 * <p>Provisions a new tenant through the PostgreSQL {@code SECURITY DEFINER} function
 * {@code core.provision_tenant}, which atomically writes the tenant, subscription, and module records.
 *
 * <p>With an {@code admin_email} on the request, the same transaction invites that address as the new
 * tenant's {@code tenant-admin} through {@link InvitationService} (D-42), so platform staff no longer
 * act as the tenant to invite its first administrator.
 */
@Service
public class TenantServiceImpl implements TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);

    private static final String DEFAULT_COUNTRY_CODE = "IN";
    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    private static final short DEFAULT_LEAVE_YEAR_START_MONTH = 4;

    /** The system role {@code core.seed_system_roles} gives every tenant its administrators through. */
    static final String TENANT_ADMIN_ROLE = "tenant-admin";

    private final JdbcTemplate jdbcTemplate;
    private final SetupChecklistService setupChecklistService;
    private final Supplier<InvitationService> invitationService;

    public TenantServiceImpl(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, (SetupChecklistService) null);
    }

    // The constructor Spring uses. With more than one constructor Spring needs one marked,
    // or it falls back to a no-arg constructor that does not exist. ObjectProvider keeps
    // contexts that do not scan SetupChecklistService or InvitationService (the guard test
    // slices) starting; the invitation service is looked up only when an admin is invited.
    @Autowired
    public TenantServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<SetupChecklistService> setupChecklistServiceProvider,
            ObjectProvider<InvitationService> invitationServiceProvider) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.setupChecklistService =
                setupChecklistServiceProvider != null ? setupChecklistServiceProvider.getIfAvailable() : null;
        this.invitationService =
                invitationServiceProvider != null ? invitationServiceProvider::getIfAvailable : () -> null;
    }

    public TenantServiceImpl(JdbcTemplate jdbcTemplate, SetupChecklistService setupChecklistService) {
        this(jdbcTemplate, setupChecklistService, null);
    }

    public TenantServiceImpl(
            JdbcTemplate jdbcTemplate,
            SetupChecklistService setupChecklistService,
            InvitationService invitationService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.setupChecklistService = setupChecklistService;
        this.invitationService = () -> invitationService;
    }

    // Declared here, not as an interface default, so a call through the Spring proxy opens the
    // transaction: a default method would reach the two-argument one by self-invocation, untransacted.
    @Override
    @Transactional
    public TenantResponse provisionTenant(TenantRequest request) {
        return provisionTenant(request, null);
    }

    @Override
    @Transactional
    public TenantResponse provisionTenant(TenantRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");

        // 1. Validate name
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required and must not be blank");
        }
        String name = request.name().trim();

        // 2. Validate country_code
        String rawCountry = request.countryCode();
        String countryCode = DEFAULT_COUNTRY_CODE;
        if (rawCountry != null && !rawCountry.isBlank()) {
            String trimmed = rawCountry.trim();
            if (trimmed.length() != 2) {
                throw new IllegalArgumentException("country_code must be a 2-character ISO 3166-1 alpha-2 code");
            }
            countryCode = trimmed.toUpperCase();
        }

        // 3. Validate timezone
        String rawTimezone = request.timeZone();
        String timezone = DEFAULT_TIMEZONE;
        if (rawTimezone != null && !rawTimezone.isBlank()) {
            String trimmed = rawTimezone.trim();
            try {
                ZoneId.of(trimmed);
                timezone = trimmed;
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Invalid timezone: " + trimmed, e);
            }
        }

        // 4. Validate leave_year_start_month
        Short rawMonth = request.leaveYearStartMonth();
        short leaveYearStartMonth = DEFAULT_LEAVE_YEAR_START_MONTH;
        if (rawMonth != null) {
            if (rawMonth < 1 || rawMonth > 12) {
                throw new IllegalArgumentException("leave_year_start_month must be between 1 and 12");
            }
            leaveYearStartMonth = rawMonth;
        }

        // 5. Modules
        Set<PlatformModule> modules = request.modules() != null ? request.modules() : Set.of();
        String[] moduleNames = modules.stream().map(Enum::name).toArray(String[]::new);

        // 6. Administrator email (D-42). Checked before anything is written, so a bad address or a
        //    missing inviter provisions nothing.
        final String adminEmail = request.validatedAdminEmail();
        final InvitationService invitations;
        if (adminEmail != null) {
            if (actorUserId == null) {
                throw new IllegalStateException(
                        "No authenticated user could be resolved to record as the inviter of the tenant administrator");
            }
            invitations = invitationService.get();
            if (invitations == null) {
                throw new IllegalStateException(
                        "User invitations are not available to invite the tenant administrator");
            }
        } else {
            invitations = null;
        }

        final String finalCountryCode = countryCode;
        final String finalTimezone = timezone;
        final short finalMonth = leaveYearStartMonth;

        UUID tenantId = jdbcTemplate.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT core.provision_tenant(?, ?, ?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, finalCountryCode);
                ps.setString(3, finalTimezone);
                ps.setShort(4, finalMonth);
                Array array = conn.createArrayOf("text", moduleNames);
                ps.setArray(5, array);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject(1, UUID.class);
                    }
                    throw new IllegalStateException("provision_tenant function did not return a tenant id");
                }
            }
        });

        log.info("Provisioned tenant {} ({}) with modules {}", tenantId, name, modules);

        UUID adminInvitationId = null;
        if (setupChecklistService != null || adminEmail != null) {
            adminInvitationId = runAsTenant(tenantId, () -> {
                if (setupChecklistService != null) {
                    setupChecklistService.assemble(tenantId);
                }
                return adminEmail != null ? inviteAdministrator(invitations, tenantId, adminEmail, actorUserId) : null;
            });
        }

        return new TenantResponse(
                tenantId, tenantId, name, finalCountryCode, finalTimezone, finalMonth, modules, adminInvitationId);
    }

    // The checklist rows and the administrator invitation belong to the new tenant, but the open
    // transaction's connection is bound to the provisioner's tenant by the first statement above,
    // and row-level security on core.tenant_setup_step, core.user_invitation,
    // core.user_invitation_role and core.notification refuses a row for any other tenant. Rebind
    // the thread and the transaction to the new tenant. The thread binding is restored on the way
    // out; the connection binding is not, and must not be: JPA writes its inserts at flush, which
    // may be the commit, and they must reach the database under the new tenant. Nothing else runs
    // in this transaction afterwards.
    private <T> T runAsTenant(UUID tenantId, Supplier<T> work) {
        UUID previousTenant = TenantContext.current().orElse(null);
        try {
            TenantContext.set(tenantId);
            jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                TenantContext.setForConnection(conn);
                return null;
            });
            return work.get();
        } finally {
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Invites {@code email} as the administrator of {@code tenantId}, with the tenant's {@code tenant-admin}
     * role, through the same service and email as {@code POST /api/v1/user-invitations} (W-24.2). Runs with
     * the new tenant bound.
     *
     * @return the invitation's id
     */
    private UUID inviteAdministrator(InvitationService invitations, UUID tenantId, String email, UUID actorUserId) {
        // The role is seeded by the core.tenant insert trigger (V022 tenant_seed_system_roles).
        List<UUID> roleIds = jdbcTemplate.query(
                "SELECT id FROM core.role WHERE tenant_id = ? AND code = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tenantId,
                TENANT_ADMIN_ROLE);
        if (roleIds.isEmpty()) {
            throw new IllegalStateException("Tenant " + tenantId + " has no " + TENANT_ADMIN_ROLE + " role");
        }
        UserInvitationResponse invitation =
                invitations.createUserInvitation(new UserInvitationRequest(email, Set.of(roleIds.get(0))), actorUserId);
        // The id only: the address is personal data.
        log.info("Invited the administrator of tenant {} (user invitation {})", tenantId, invitation.id());
        return invitation.id();
    }
}
