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
 * Declared self-occupied home loan principal and interest deductions under Section 24(b) / 80C (W-32.2).
 */
@Entity
@Table(schema = "payroll", name = "employee_inv_home_loan")
public class EmployeeInvHomeLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Column(name = "lender_name", nullable = false, length = 150)
    private String lenderName;

    @Column(name = "lender_pan", length = 10)
    private String lenderPan;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "is_first_time_buyer", nullable = false)
    private boolean isFirstTimeBuyer = false;

    @Column(name = "loan_sanctioned_on")
    private LocalDate loanSanctionedOn;

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

    public EmployeeInvHomeLoan() {}

    public EmployeeInvHomeLoan(
            UUID tenantId,
            UUID declarationId,
            String lenderName,
            String lenderPan,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            boolean isFirstTimeBuyer,
            LocalDate loanSanctionedOn) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId must not be null");
        this.lenderName = Objects.requireNonNull(lenderName, "lenderName must not be null");
        this.lenderPan = lenderPan;
        this.principalPaid = principalPaid != null ? principalPaid : BigDecimal.ZERO;
        this.interestPaid = interestPaid != null ? interestPaid : BigDecimal.ZERO;
        this.isFirstTimeBuyer = isFirstTimeBuyer;
        this.loanSanctionedOn = loanSanctionedOn;
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

    public String getLenderName() {
        return lenderName;
    }

    public void setLenderName(String lenderName) {
        this.lenderName = lenderName;
    }

    public String getLenderPan() {
        return lenderPan;
    }

    public void setLenderPan(String lenderPan) {
        this.lenderPan = lenderPan;
    }

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public void setPrincipalPaid(BigDecimal principalPaid) {
        this.principalPaid = principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public void setInterestPaid(BigDecimal interestPaid) {
        this.interestPaid = interestPaid;
    }

    public boolean isFirstTimeBuyer() {
        return isFirstTimeBuyer;
    }

    public void setFirstTimeBuyer(boolean firstTimeBuyer) {
        isFirstTimeBuyer = firstTimeBuyer;
    }

    public LocalDate getLoanSanctionedOn() {
        return loanSanctionedOn;
    }

    public void setLoanSanctionedOn(LocalDate loanSanctionedOn) {
        this.loanSanctionedOn = loanSanctionedOn;
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
