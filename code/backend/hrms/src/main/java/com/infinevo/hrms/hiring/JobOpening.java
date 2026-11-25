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
 * Job Opening requisition entity (W-112).
 */
@Entity
@Table(name = "job_opening", schema = "hrms")
@Audited
public class JobOpening {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "opening_code", nullable = false, length = 32)
    private String openingCode;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "designation_id")
    private UUID designationId;

    @Column(name = "work_location_id")
    private UUID workLocationId;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private JobOpeningEmploymentType employmentType = JobOpeningEmploymentType.FULL_TIME;

    @Column(name = "openings_count", nullable = false)
    private int openingsCount = 1;

    @Column(name = "filled_count", nullable = false)
    private int filledCount = 0;

    @Column(name = "min_salary", precision = 19, scale = 4)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 19, scale = 4)
    private BigDecimal maxSalary;

    @Column(name = "min_experience_years")
    private Integer minExperienceYears;

    @Column(name = "max_experience_years")
    private Integer maxExperienceYears;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobOpeningStatus status = JobOpeningStatus.DRAFT;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public JobOpening() {}

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

    public String getOpeningCode() {
        return openingCode;
    }

    public void setOpeningCode(String openingCode) {
        this.openingCode = openingCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getDesignationId() {
        return designationId;
    }

    public void setDesignationId(UUID designationId) {
        this.designationId = designationId;
    }

    public UUID getWorkLocationId() {
        return workLocationId;
    }

    public void setWorkLocationId(UUID workLocationId) {
        this.workLocationId = workLocationId;
    }

    public UUID getHiringManagerId() {
        return hiringManagerId;
    }

    public void setHiringManagerId(UUID hiringManagerId) {
        this.hiringManagerId = hiringManagerId;
    }

    public JobOpeningEmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(JobOpeningEmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public int getOpeningsCount() {
        return openingsCount;
    }

    public void setOpeningsCount(int openingsCount) {
        this.openingsCount = openingsCount;
    }

    public int getFilledCount() {
        return filledCount;
    }

    public void setFilledCount(int filledCount) {
        this.filledCount = filledCount;
    }

    public BigDecimal getMinSalary() {
        return minSalary;
    }

    public void setMinSalary(BigDecimal minSalary) {
        this.minSalary = minSalary;
    }

    public BigDecimal getMaxSalary() {
        return maxSalary;
    }

    public void setMaxSalary(BigDecimal maxSalary) {
        this.maxSalary = maxSalary;
    }

    public Integer getMinExperienceYears() {
        return minExperienceYears;
    }

    public void setMinExperienceYears(Integer minExperienceYears) {
        this.minExperienceYears = minExperienceYears;
    }

    public Integer getMaxExperienceYears() {
        return maxExperienceYears;
    }

    public void setMaxExperienceYears(Integer maxExperienceYears) {
        this.maxExperienceYears = maxExperienceYears;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JobOpeningStatus getStatus() {
        return status;
    }

    public void setStatus(JobOpeningStatus status) {
        this.status = status;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
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
