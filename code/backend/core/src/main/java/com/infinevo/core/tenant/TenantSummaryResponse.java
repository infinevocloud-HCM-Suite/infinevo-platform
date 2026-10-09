package com.infinevo.core.tenant;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The platform dashboard's one reply (W-73.2): customer tenants counted by subscription status, the most
 * recently created ones, and the ones whose administrator has not yet taken up their invitation.
 *
 * <p>{@code byStatus} names every {@code SubscriptionStatus}, zero included, plus {@code NONE} for a tenant with
 * no subscription row when there is one. The platform tenant itself is not a customer and is in none of these.
 */
public record TenantSummaryResponse(
        long total,
        Map<String, Long> byStatus,
        long createdLast30Days,
        List<RecentTenant> recent,
        List<WaitingTenant> waitingForAdmin) {

    public TenantSummaryResponse {
        byStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));
        recent = List.copyOf(recent);
        waitingForAdmin = List.copyOf(waitingForAdmin);
    }

    /** A recently created tenant; {@code status} is its subscription status, null when it has none. */
    public record RecentTenant(UUID id, String name, Instant createdAt, String status) {}

    /** A tenant whose {@code tenant-admin} invitation is {@code PENDING} or {@code EXPIRED}. */
    public record WaitingTenant(UUID id, String name, String adminEmail, String invitationStatus, Instant expiresAt) {}
}
