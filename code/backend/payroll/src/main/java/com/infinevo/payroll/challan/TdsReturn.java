package com.infinevo.payroll.challan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A quarter whose TDS return has been filed, with its 15-digit token number (W-89, V225). */
@Entity
@Table(name = "tds_return", schema = "payroll")
public class TdsReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "financial_year", nullable = false, length = 9, updatable = false)
    private String financialYear;

    @Column(name = "quarter", nullable = false, length = 2, updatable = false)
    private String quarter;

    @Column(name = "token_number", nullable = false, length = 15)
    private String tokenNumber;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = "system";

    protected TdsReturn() {}

    public TdsReturn(UUID tenantId, TdsQuarter quarter, String tokenNumber, LocalDate filedOn, String createdBy) {
        this.tenantId = tenantId;
        this.financialYear = quarter.financialYear().label();
        this.quarter = quarter.quarter();
        this.tokenNumber = tokenNumber;
        this.filedOn = filedOn;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public String getQuarter() {
        return quarter;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public LocalDate getFiledOn() {
        return filedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
