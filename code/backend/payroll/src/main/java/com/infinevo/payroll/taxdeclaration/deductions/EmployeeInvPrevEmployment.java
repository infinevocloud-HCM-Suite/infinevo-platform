package com.infinevo.payroll.taxdeclaration.deductions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Declared income and tax deduction lines from previous employment (W-32.3).
 */
@Entity
@Table(schema = "payroll", name = "employee_inv_prev_employment")
public class EmployeeInvPrevEmployment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private PrevEmploymentKind kind;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "employer_name", length = 150)
    private String employerName;

    @Column(name = "employer_tan", length = 10)
    private String employerTan;

    @Enumerated(EnumType.STRING)
    @Column(name = "entered_by", nullable = false, length = 8)
    private EnteredBy enteredBy = EnteredBy.EMPLOYEE;

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

    public EmployeeInvPrevEmployment() {}

    public EmployeeInvPrevEmployment(
            UUID tenantId,
            UUID declarationId,
            PrevEmploymentKind kind,
            BigDecimal amount,
            String employerName,
            String employerTan,
            EnteredBy enteredBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.employerName = employerName;
        this.employerTan = employerTan;
        this.enteredBy = enteredBy != null ? enteredBy : EnteredBy.EMPLOYEE;
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

    public PrevEmploymentKind getKind() {
        return kind;
    }

    public void setKind(PrevEmploymentKind kind) {
        this.kind = kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public String getEmployerTan() {
        return employerTan;
    }

    public void setEmployerTan(String employerTan) {
        this.employerTan = employerTan;
    }

    public EnteredBy getEnteredBy() {
        return enteredBy;
    }

    public void setEnteredBy(EnteredBy enteredBy) {
        this.enteredBy = enteredBy;
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
