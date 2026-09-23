package com.infinevo.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A test-only entity that opts in to the audit trail.
 *
 * <p>Spec section 2 asks for {@code core.tenant} and {@code core.user_tenant} to be opted in as
 * the proof. Neither has a JPA entity anywhere in the platform yet — both are reached through
 * {@code JdbcTemplate} in {@code TenantMembershipService} — and creating one would mean writing
 * into {@code core}, a different module from this ticket. So the end-to-end proof runs against
 * this probe instead: same listener, same writer, same table, in {@code core} with {@code
 * tenant_id} and row-level security like any other business table.
 *
 * <p>{@code apiToken} exists so {@link AuditCaptureIT} can show the deny-list redacting a real
 * captured row, not only a unit-tested map.
 */
@Entity
@Table(name = "audited_probe", schema = "core")
@Audited
public class AuditedProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "api_token", length = 100)
    private String apiToken;

    public AuditedProbe() {}

    public AuditedProbe(UUID tenantId, String name, BigDecimal amount, String apiToken) {
        this.tenantId = tenantId;
        this.name = name;
        this.amount = amount;
        this.apiToken = apiToken;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
