package com.itsdev.payroll.entity.salarycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deductions")
public class Deduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String deductionId;

    @Column(nullable = false)
    private String deductionName;

    @Column(nullable = false)
    private String deductionType;

    private String deductionTypeFormatted;

    @Column(nullable = false)
    private String status = "active";

    private String statusFormatted;

    private Boolean isRecurring;

    private LocalDateTime createdTime;

    private Boolean isUserConfigurable;

    private Boolean isAssociatedWithEmployee;

    private String perquisiteInterestRate;

    private String emiInterestRate;

    private String emiType;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId")
    private Organization organization;

    @Column(nullable = false)
    private Boolean isDeleted = false;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeductionId() {
        return deductionId;
    }

    public void setDeductionId(String deductionId) {
        this.deductionId = deductionId;
    }

    public String getDeductionName() {
        return deductionName;
    }

    public void setDeductionName(String deductionName) {
        this.deductionName = deductionName;
    }

    public String getDeductionType() {
        return deductionType;
    }

    public void setDeductionType(String deductionType) {
        this.deductionType = deductionType;
    }

    public String getDeductionTypeFormatted() {
        return deductionTypeFormatted;
    }

    public void setDeductionTypeFormatted(String deductionTypeFormatted) {
        this.deductionTypeFormatted = deductionTypeFormatted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusFormatted() {
        return statusFormatted;
    }

    public void setStatusFormatted(String statusFormatted) {
        this.statusFormatted = statusFormatted;
    }

    public Boolean getIsRecurring() {
        return isRecurring;
    }

    public void setIsRecurring(Boolean isRecurring) {
        this.isRecurring = isRecurring;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public Boolean getIsUserConfigurable() {
        return isUserConfigurable;
    }

    public void setIsUserConfigurable(Boolean isUserConfigurable) {
        this.isUserConfigurable = isUserConfigurable;
    }

    public Boolean getIsAssociatedWithEmployee() {
        return isAssociatedWithEmployee;
    }

    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) {
        this.isAssociatedWithEmployee = isAssociatedWithEmployee;
    }

    public String getPerquisiteInterestRate() {
        return perquisiteInterestRate;
    }

    public void setPerquisiteInterestRate(String perquisiteInterestRate) {
        this.perquisiteInterestRate = perquisiteInterestRate;
    }

    public String getEmiInterestRate() {
        return emiInterestRate;
    }

    public void setEmiInterestRate(String emiInterestRate) {
        this.emiInterestRate = emiInterestRate;
    }

    public String getEmiType() {
        return emiType;
    }

    public void setEmiType(String emiType) {
        this.emiType = emiType;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }



}