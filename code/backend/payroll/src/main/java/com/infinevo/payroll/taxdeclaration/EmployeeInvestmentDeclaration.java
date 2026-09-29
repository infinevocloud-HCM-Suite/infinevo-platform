package com.infinevo.payroll.taxdeclaration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Employee annual investment declaration header (W-32.1).
 *
 * <p>All section tables (housing, Section 6A, other income) hang off this header.
 */
@Entity
@Table(schema = "payroll", name = "employee_investment_declaration")
public class EmployeeInvestmentDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "tax_regime", nullable = false, length = 3)
    private String taxRegime = "NEW";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeclarationStatus status = DeclarationStatus.DRAFT;

    @Column(name = "is_staying_in_rented_house", nullable = false)
    private boolean isStayingInRentedHouse = false;

    @Column(name = "is_repaying_self_occupied_loan", nullable = false)
    private boolean isRepayingSelfOccupiedLoan = false;

    @Column(name = "has_let_out_property", nullable = false)
    private boolean hasLetOutProperty = false;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

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

    public EmployeeInvestmentDeclaration() {}

    public EmployeeInvestmentDeclaration(UUID tenantId, UUID employeeId, String financialYear, String taxRegime) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.taxRegime = taxRegime != null ? taxRegime : "NEW";
        this.status = DeclarationStatus.DRAFT;
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

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public DeclarationStatus getStatus() {
        return status;
    }

    public void setStatus(DeclarationStatus status) {
        this.status = status;
    }

    public boolean isStayingInRentedHouse() {
        return isStayingInRentedHouse;
    }

    public void setStayingInRentedHouse(boolean stayingInRentedHouse) {
        isStayingInRentedHouse = stayingInRentedHouse;
    }

    public boolean isRepayingSelfOccupiedLoan() {
        return isRepayingSelfOccupiedLoan;
    }

    public void setRepayingSelfOccupiedLoan(boolean repayingSelfOccupiedLoan) {
        isRepayingSelfOccupiedLoan = repayingSelfOccupiedLoan;
    }

    public boolean isHasLetOutProperty() {
        return hasLetOutProperty;
    }

    public boolean hasLetOutProperty() {
        return hasLetOutProperty;
    }

    public void setHasLetOutProperty(boolean hasLetOutProperty) {
        this.hasLetOutProperty = hasLetOutProperty;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
