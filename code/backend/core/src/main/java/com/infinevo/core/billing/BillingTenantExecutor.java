package com.infinevo.core.billing;

import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Shared helper for billing tenant-context switching under PostgreSQL RLS and actor resolution (W-94.1).
 */
@Component
class BillingTenantExecutor {

    private static final int MAX_ACTOR_LENGTH = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTenant platformTenant;

    BillingTenantExecutor(JdbcTemplate jdbcTemplate, PlatformTenant platformTenant) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.platformTenant = platformTenant != null ? platformTenant : new PlatformTenant();
    }

    PlatformTenant platformTenant() {
        return platformTenant;
    }

    /**
     * Resolves the owning tenant for {@code invoiceId} across RLS when called by the platform tenant, or returns
     * the caller's bound tenant when called by a customer tenant.
     */
    UUID resolveReadableTenantForInvoice(UUID invoiceId) {
        UUID boundTenant = TenantContext.require();
        if (!platformTenant.isPlatformTenant(boundTenant)) {
            return boundTenant;
        }
        return lookupInvoiceTenant(invoiceId);
    }

    /** Resolves the owning tenant for {@code invoiceId} after asserting the caller is the platform tenant. */
    UUID resolvePlatformTargetTenantForInvoice(UUID invoiceId) {
        platformTenant.requirePlatformTenant();
        return lookupInvoiceTenant(invoiceId);
    }

    private UUID lookupInvoiceTenant(UUID invoiceId) {
        UUID ownerTenant = jdbcTemplate.queryForObject("SELECT core.billing_invoice_tenant(?)", UUID.class, invoiceId);
        if (ownerTenant == null) {
            throw new BillingInvoiceNotFoundException(invoiceId);
        }
        return ownerTenant;
    }

    /**
     * Runs {@code work} with both {@link TenantContext} and the current transaction's {@code app.current_tenant_id}
     * bound to {@code targetTenantId}, restoring the prior binding afterwards.
     */
    <T> T asTenant(UUID targetTenantId, Supplier<T> work) {
        Objects.requireNonNull(targetTenantId, "targetTenantId must not be null");
        UUID previousTenant = TenantContext.current().orElse(null);
        boolean rebind = !targetTenantId.equals(previousTenant);
        if (rebind) {
            TenantContext.set(targetTenantId);
            jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                TenantContext.setForConnection(conn);
                return null;
            });
        }
        try {
            return work.get();
        } finally {
            if (rebind) {
                if (previousTenant != null) {
                    TenantContext.set(previousTenant);
                    jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                        TenantContext.setForConnection(conn);
                        return null;
                    });
                } else {
                    TenantContext.clear();
                }
            }
        }
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return BillingInvoice.ACTOR_SYSTEM;
        }
        String candidate = null;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            candidate = jwt.getClaimAsString("preferred_username");
            if (candidate == null || candidate.isBlank()) {
                candidate = jwt.getSubject();
            }
        }
        if (candidate == null || candidate.isBlank()) {
            candidate = auth.getName();
        }
        if (candidate == null || candidate.isBlank()) {
            return BillingInvoice.ACTOR_SYSTEM;
        }
        String trimmed = candidate.trim();
        return trimmed.length() > MAX_ACTOR_LENGTH ? trimmed.substring(0, MAX_ACTOR_LENGTH) : trimmed;
    }
}
