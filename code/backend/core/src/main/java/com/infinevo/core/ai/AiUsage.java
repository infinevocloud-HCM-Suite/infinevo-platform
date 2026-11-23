package com.infinevo.core.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A tenant's AI helper request count for one calendar month (W-114.1) — {@code core.ai_usage} (V192).
 *
 * <p>{@code usageMonth} is the first day of the month in UTC. The row is only ever written by
 * {@link AiUsageRepository#countOne}, one atomic statement, so this entity is read-only here: no setters.
 */
@Entity
@Table(
        name = "ai_usage",
        schema = "core",
        indexes = {@Index(name = "uk_ai_usage_tenant_month", columnList = "tenant_id, usage_month", unique = true)})
public class AiUsage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usage_month", nullable = false, updatable = false)
    private LocalDate usageMonth;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiUsage() {}

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public LocalDate getUsageMonth() {
        return usageMonth;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
