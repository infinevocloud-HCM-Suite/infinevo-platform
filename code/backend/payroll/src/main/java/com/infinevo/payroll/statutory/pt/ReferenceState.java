package com.infinevo.payroll.statutory.pt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping of {@code reference.state} (W-09, V003).
 */
@Entity
@Table(name = "state", schema = "reference")
@Immutable
public class ReferenceState {

    @Id
    @Column(name = "code", length = 10, nullable = false)
    private String code;

    @Column(name = "gst_state_code", length = 2, nullable = false)
    private String gstStateCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(name = "is_union_territory", nullable = false)
    private boolean isUnionTerritory;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReferenceState() {}

    public ReferenceState(String code, String gstStateCode, String name, String countryCode, boolean isUnionTerritory) {
        this.code = code;
        this.gstStateCode = gstStateCode;
        this.name = name;
        this.countryCode = countryCode;
        this.isUnionTerritory = isUnionTerritory;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public String getGstStateCode() {
        return gstStateCode;
    }

    public String getName() {
        return name;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public boolean isUnionTerritory() {
        return isUnionTerritory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
