package com.infinevo.hrms.hiring;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Formal job offer entity (W-112).
 */
@Entity
@Table(name = "job_offer", schema = "hrms")
@Audited
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "designation_id", nullable = false)
    private UUID designationId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "work_location_id", nullable = false)
    private UUID workLocationId;

    @Column(name = "offered_ctc", nullable = false, precision = 19, scale = 4)
    private BigDecimal offeredCtc;

    @Column(name = "target_join_date", nullable = false)
    private LocalDate targetJoinDate;

    @Column(name = "offer_valid_until", nullable = false)
    private LocalDate offerValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobOfferStatus status = JobOfferStatus.DRAFT;

    @Column(name = "converted_employee_id")
    private UUID convertedEmployeeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public JobOffer() {}

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void onPreUpdate() {
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

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getDesignationId() {
        return designationId;
    }

    public void setDesignationId(UUID designationId) {
        this.designationId = designationId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getWorkLocationId() {
        return workLocationId;
    }

    public void setWorkLocationId(UUID workLocationId) {
        this.workLocationId = workLocationId;
    }

    public BigDecimal getOfferedCtc() {
        return offeredCtc;
    }

    public void setOfferedCtc(BigDecimal offeredCtc) {
        this.offeredCtc = offeredCtc;
    }

    public LocalDate getTargetJoinDate() {
        return targetJoinDate;
    }

    public void setTargetJoinDate(LocalDate targetJoinDate) {
        this.targetJoinDate = targetJoinDate;
    }

    public LocalDate getOfferValidUntil() {
        return offerValidUntil;
    }

    public void setOfferValidUntil(LocalDate offerValidUntil) {
        this.offerValidUntil = offerValidUntil;
    }

    public JobOfferStatus getStatus() {
        return status;
    }

    public void setStatus(JobOfferStatus status) {
        this.status = status;
    }

    public UUID getConvertedEmployeeId() {
        return convertedEmployeeId;
    }

    public void setConvertedEmployeeId(UUID convertedEmployeeId) {
        this.convertedEmployeeId = convertedEmployeeId;
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
