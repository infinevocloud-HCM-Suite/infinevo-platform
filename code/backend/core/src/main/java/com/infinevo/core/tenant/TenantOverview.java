package com.infinevo.core.tenant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Overview of a tenant including subscription status, granted modules, and user count (W-65.1).
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
        @JsonProperty("user_count") long userCount) {}
