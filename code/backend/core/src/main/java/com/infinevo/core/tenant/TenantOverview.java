package com.infinevo.core.tenant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Overview of a tenant including subscription status, granted modules, and user count (W-65.1).
 *
 * <p>{@code admin_invitation} is the state of the tenant's administrator invitation (D-42).
 */
public record TenantOverview(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("name") String name,
        @JsonProperty("country_code") String countryCode,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("status") String status,
        @JsonProperty("modules") List<String> modules,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("current_period_end") LocalDate currentPeriodEnd,
        @JsonProperty("user_count") long userCount,
        @JsonProperty("admin_invitation") AdminInvitation adminInvitation) {

    /** Where the tenant's administrator invitation stands, as {@code core.list_tenants()} (V159) reports it. */
    public enum AdminInvitationStatus {
        /** A live invitation carrying {@code tenant-admin} is open. */
        PENDING,
        /** An invitation carrying {@code tenant-admin} was accepted. */
        ACCEPTED,
        /** Neither. */
        NONE;

        /** Unknown or missing values read as {@link #NONE}. */
        public static AdminInvitationStatus of(String value) {
            if (value == null) {
                return NONE;
            }
            try {
                return valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }

    /** The administrator invitation: its email ({@code null} for {@code NONE}) and status. */
    public record AdminInvitation(
            @JsonProperty("email") String email, @JsonProperty("status") AdminInvitationStatus status) {

        public static final AdminInvitation NONE = new AdminInvitation(null, AdminInvitationStatus.NONE);
    }
}
