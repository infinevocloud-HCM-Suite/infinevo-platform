package com.itsdev.payroll.entity.salarycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "reimbursements")
public class Reimbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reimbursementId;

    private String reimbursementName;
    private String reimbursementType;
    private String reimbursementTypeFormatted;
    private String displayName;

    private BigDecimal maxLimit;

    private Boolean isIncludedInCtc;
    private Boolean isIncludedInSalaryStructure;
    private String status;
    private String statusFormatted;
    private Boolean isFbpComponent;
    private Boolean isOptIn;

    private String carryForwardOption;
    private Boolean isAssociatedWithEmployee;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;


    // ------------------- Getters & Setters -------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReimbursementId() { return reimbursementId; }
    public void setReimbursementId(String reimbursementId) { this.reimbursementId = reimbursementId; }

    public String getReimbursementName() { return reimbursementName; }
    public void setReimbursementName(String reimbursementName) { this.reimbursementName = reimbursementName; }

    public String getReimbursementType() { return reimbursementType; }
    public void setReimbursementType(String reimbursementType) { this.reimbursementType = reimbursementType; }

    public String getReimbursementTypeFormatted() { return reimbursementTypeFormatted; }
    public void setReimbursementTypeFormatted(String reimbursementTypeFormatted) { this.reimbursementTypeFormatted = reimbursementTypeFormatted; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public BigDecimal getMaxLimit() { return maxLimit; }
    public void setMaxLimit(BigDecimal maxLimit) { this.maxLimit = maxLimit; }

    public Boolean getIsIncludedInCtc() { return isIncludedInCtc; }
    public void setIsIncludedInCtc(Boolean isIncludedInCtc) { this.isIncludedInCtc = isIncludedInCtc; }

    public Boolean getIsIncludedInSalaryStructure() { return isIncludedInSalaryStructure; }
    public void setIsIncludedInSalaryStructure(Boolean isIncludedInSalaryStructure) { this.isIncludedInSalaryStructure = isIncludedInSalaryStructure; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusFormatted() { return statusFormatted; }
    public void setStatusFormatted(String statusFormatted) { this.statusFormatted = statusFormatted; }

    public Boolean getIsFbpComponent() { return isFbpComponent; }
    public void setIsFbpComponent(Boolean isFbpComponent) { this.isFbpComponent = isFbpComponent; }

    public Boolean getIsOptIn() { return isOptIn; }
    public void setIsOptIn(Boolean isOptIn) { this.isOptIn = isOptIn; }

    public String getCarryForwardOption() { return carryForwardOption; }
    public void setCarryForwardOption(String carryForwardOption) { this.carryForwardOption = carryForwardOption; }

    public Boolean getIsAssociatedWithEmployee() { return isAssociatedWithEmployee; }
    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) { this.isAssociatedWithEmployee = isAssociatedWithEmployee; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}
