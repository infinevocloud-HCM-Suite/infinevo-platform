package com.infinevo.payroll.taxdeclaration.housing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Declared rented accommodation period and monthly rent details under Section 10(13A) (W-32.2).
 */
@Entity
@Table(schema = "payroll", name = "employee_inv_house_rent")
public class EmployeeInvHouseRent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Column(name = "from_month", nullable = false)
    private LocalDate fromMonth;

    @Column(name = "to_month", nullable = false)
    private LocalDate toMonth;

    @Column(name = "address", nullable = false, length = 1000)
    private String address;

    @Column(name = "landlord_name", nullable = false, length = 150)
    private String landlordName;

    @Column(name = "landlord_pan", length = 10)
    private String landlordPan;

    @Column(name = "is_metro", nullable = false)
    private boolean isMetro = false;

    @Column(name = "amount_per_month", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPerMonth = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public EmployeeInvHouseRent() {}

    public EmployeeInvHouseRent(
            UUID tenantId,
            UUID declarationId,
            LocalDate fromMonth,
            LocalDate toMonth,
            String address,
            String landlordName,
            String landlordPan,
            boolean isMetro,
            BigDecimal amountPerMonth) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId must not be null");
        this.fromMonth = Objects.requireNonNull(fromMonth, "fromMonth must not be null");
        this.toMonth = Objects.requireNonNull(toMonth, "toMonth must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.landlordName = Objects.requireNonNull(landlordName, "landlordName must not be null");
        this.landlordPan = landlordPan;
        this.isMetro = isMetro;
        this.amountPerMonth = amountPerMonth != null ? amountPerMonth : BigDecimal.ZERO;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(UUID declarationId) {
        this.declarationId = declarationId;
    }

    public LocalDate getFromMonth() {
        return fromMonth;
    }

    public void setFromMonth(LocalDate fromMonth) {
        this.fromMonth = fromMonth;
    }

    public LocalDate getToMonth() {
        return toMonth;
    }

    public void setToMonth(LocalDate toMonth) {
        this.toMonth = toMonth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLandlordName() {
        return landlordName;
    }

    public void setLandlordName(String landlordName) {
        this.landlordName = landlordName;
    }

    public String getLandlordPan() {
        return landlordPan;
    }

    public void setLandlordPan(String landlordPan) {
        this.landlordPan = landlordPan;
    }

    public boolean isMetro() {
        return isMetro;
    }

    public void setMetro(boolean metro) {
        isMetro = metro;
    }

    public BigDecimal getAmountPerMonth() {
        return amountPerMonth;
    }

    public void setAmountPerMonth(BigDecimal amountPerMonth) {
        this.amountPerMonth = amountPerMonth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
