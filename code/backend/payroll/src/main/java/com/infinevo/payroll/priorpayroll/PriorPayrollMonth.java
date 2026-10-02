package com.infinevo.payroll.priorpayroll;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity holding an employee's salary and deduction figures for a month paid before joining
 * this platform (W-38.1 §4 &amp; §6).
 */
@Entity
@Table(name = "prior_payroll_month", schema = "payroll")
@Audited
public class PriorPayrollMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "gross_earnings", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossEarnings;

    @Column(name = "epf_employee", nullable = false, precision = 19, scale = 4)
    private BigDecimal epfEmployee;

    @Column(name = "esi_employee", nullable = false, precision = 19, scale = 4)
    private BigDecimal esiEmployee;

    @Column(name = "professional_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal professionalTax;

    @Column(name = "tds", nullable = false, precision = 19, scale = 4)
    private BigDecimal tds;

    @Column(name = "net_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal netPay;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public PriorPayrollMonth() {}

    public PriorPayrollMonth(
            UUID tenantId,
            UUID employeeId,
            String period,
            BigDecimal grossEarnings,
            BigDecimal epfEmployee,
            BigDecimal esiEmployee,
            BigDecimal professionalTax,
            BigDecimal tds,
            BigDecimal netPay,
            UUID importId,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null");
        this.grossEarnings = Objects.requireNonNull(grossEarnings, "grossEarnings must not be null");
        this.epfEmployee = epfEmployee != null ? epfEmployee : BigDecimal.ZERO.setScale(4);
        this.esiEmployee = esiEmployee != null ? esiEmployee : BigDecimal.ZERO.setScale(4);
        this.professionalTax = professionalTax != null ? professionalTax : BigDecimal.ZERO.setScale(4);
        this.tds = tds != null ? tds : BigDecimal.ZERO.setScale(4);
        this.netPay = Objects.requireNonNull(netPay, "netPay must not be null");
        this.importId = Objects.requireNonNull(importId, "importId must not be null");
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = actor != null ? actor : "system";
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
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

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public BigDecimal getGrossEarnings() {
        return grossEarnings;
    }

    public void setGrossEarnings(BigDecimal grossEarnings) {
        this.grossEarnings = grossEarnings;
    }

    public BigDecimal getEpfEmployee() {
        return epfEmployee;
    }

    public void setEpfEmployee(BigDecimal epfEmployee) {
        this.epfEmployee = epfEmployee;
    }

    public BigDecimal getEsiEmployee() {
        return esiEmployee;
    }

    public void setEsiEmployee(BigDecimal esiEmployee) {
        this.esiEmployee = esiEmployee;
    }

    public BigDecimal getProfessionalTax() {
        return professionalTax;
    }

    public void setProfessionalTax(BigDecimal professionalTax) {
        this.professionalTax = professionalTax;
    }

    public BigDecimal getTds() {
        return tds;
    }

    public void setTds(BigDecimal tds) {
        this.tds = tds;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }

    public UUID getImportId() {
        return importId;
    }

    public void setImportId(UUID importId) {
        this.importId = importId;
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
