package com.infinevo.payroll.statutory.pt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * National reference table tracking which states levy professional tax (W-31.2, V064).
 */
@Entity
@Table(name = "pt_state", schema = "reference")
@Immutable
public class PtState {

    @Id
    @Column(name = "state_code", length = 10, nullable = false)
    private String stateCode;

    @Column(name = "levies_pt", nullable = false)
    private boolean leviesPt;

    @Column(name = "annual_ceiling", precision = 19, scale = 4)
    private BigDecimal annualCeiling;

    @Column(name = "notes", length = 200)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PtState() {}

    public PtState(String stateCode, boolean leviesPt, BigDecimal annualCeiling, String notes) {
        this.stateCode = stateCode;
        this.leviesPt = leviesPt;
        this.annualCeiling = annualCeiling;
        this.notes = notes;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getStateCode() {
        return stateCode;
    }

    public boolean isLeviesPt() {
        return leviesPt;
    }

    public BigDecimal getAnnualCeiling() {
        return annualCeiling;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
