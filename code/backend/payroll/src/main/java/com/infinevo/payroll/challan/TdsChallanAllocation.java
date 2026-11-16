package com.infinevo.payroll.challan;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Links a TDS challan deposit to a pay run employee deduction (W-89.1).
 */
@Entity
@Audited
@Table(
        name = "tds_challan_allocation",
        schema = "payroll",
        indexes = {
            @Index(
                    name = "uk_tds_challan_alloc_emp_run",
                    columnList = "tenant_id, challan_id, pay_run_id, employee_id",
                    unique = true),
            @Index(name = "idx_tds_challan_alloc_challan", columnList = "tenant_id, challan_id"),
            @Index(name = "idx_tds_challan_alloc_pay_run", columnList = "tenant_id, pay_run_id")
        })
public class TdsChallanAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "challan_id", nullable = false, updatable = false)
    private UUID challanId;

    @Column(name = "pay_run_id", nullable = false, updatable = false)
    private UUID payRunId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "allocated_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedTax = BigDecimal.ZERO;

    @Column(name = "allocated_cess", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedCess = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = "system";

    public TdsChallanAllocation() {}

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

    public UUID getChallanId() {
        return challanId;
    }

    public void setChallanId(UUID challanId) {
        this.challanId = challanId;
    }

    public UUID getPayRunId() {
        return payRunId;
    }

    public void setPayRunId(UUID payRunId) {
        this.payRunId = payRunId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public BigDecimal getAllocatedTax() {
        return allocatedTax;
    }

    public void setAllocatedTax(BigDecimal allocatedTax) {
        this.allocatedTax = allocatedTax;
    }

    public BigDecimal getAllocatedCess() {
        return allocatedCess;
    }

    public void setAllocatedCess(BigDecimal allocatedCess) {
        this.allocatedCess = allocatedCess;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TdsChallanAllocation that = (TdsChallanAllocation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
