package com.infinevo.core.option;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "tenant_option")
@Audited
public class TenantOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "option_key", nullable = false, length = 100)
    private String optionKey;

    @Column(name = "option_value", nullable = false, columnDefinition = "TEXT")
    private String optionValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private String updatedBy = "system";

    protected TenantOption() {}

    public TenantOption(UUID tenantId, String optionKey, String optionValue) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.optionKey = Objects.requireNonNull(optionKey, "optionKey must not be null");
        this.optionValue = Objects.requireNonNull(optionValue, "optionValue must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = Objects.requireNonNull(optionValue, "optionValue must not be null");
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
