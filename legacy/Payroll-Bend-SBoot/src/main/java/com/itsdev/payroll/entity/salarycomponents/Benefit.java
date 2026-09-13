package com.itsdev.payroll.entity.salarycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "benefits")
public class Benefit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String benefitId;

    private String benefitName;
    private String benefitPlan;
    private String benefitPlanNameFormatted;
    private String benefitCategory;
    private boolean isPreTax;
    private int employeeCount;
    private String status;
    private boolean isOneTime;
    private boolean isUserConfigurable;
    private boolean isProRata;
    private boolean isSuperannuationBenefit;
    private boolean isIncludedInCtc;
    private boolean isIncludedInSalaryStructure;
    private String taxExemptionSubType;
    private String taxExemptionSubTypeFormatted;
    private String taxExemptSection;
    private boolean canAllowEmployerContribution;
    private boolean canAllowEmployeeContribution;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBenefitId() {
        return benefitId;
    }

    public void setBenefitId(String benefitId) {
        this.benefitId = benefitId;
    }

    public String getBenefitName() {
        return benefitName;
    }

    public void setBenefitName(String benefitName) {
        this.benefitName = benefitName;
    }

    public String getBenefitPlan() {
        return benefitPlan;
    }

    public void setBenefitPlan(String benefitPlan) {
        this.benefitPlan = benefitPlan;
    }

    public String getBenefitPlanNameFormatted() {
        return benefitPlanNameFormatted;
    }

    public void setBenefitPlanNameFormatted(String benefitPlanNameFormatted) {
        this.benefitPlanNameFormatted = benefitPlanNameFormatted;
    }

    public String getBenefitCategory() {
        return benefitCategory;
    }

    public void setBenefitCategory(String benefitCategory) {
        this.benefitCategory = benefitCategory;
    }

    public boolean isPreTax() {
        return isPreTax;
    }

    public void setPreTax(boolean preTax) {
        isPreTax = preTax;
    }

    public int getEmployeeCount() {
        return employeeCount;
    }

    public void setEmployeeCount(int employeeCount) {
        this.employeeCount = employeeCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOneTime() {
        return isOneTime;
    }

    public void setOneTime(boolean oneTime) {
        isOneTime = oneTime;
    }

    public boolean isUserConfigurable() {
        return isUserConfigurable;
    }

    public void setUserConfigurable(boolean userConfigurable) {
        isUserConfigurable = userConfigurable;
    }

    public boolean isProRata() {
        return isProRata;
    }

    public void setProRata(boolean proRata) {
        isProRata = proRata;
    }

    public boolean isSuperannuationBenefit() {
        return isSuperannuationBenefit;
    }

    public void setSuperannuationBenefit(boolean superannuationBenefit) {
        isSuperannuationBenefit = superannuationBenefit;
    }

    public boolean isIncludedInCtc() {
        return isIncludedInCtc;
    }

    public void setIncludedInCtc(boolean includedInCtc) {
        isIncludedInCtc = includedInCtc;
    }

    public boolean isIncludedInSalaryStructure() {
        return isIncludedInSalaryStructure;
    }

    public void setIncludedInSalaryStructure(boolean includedInSalaryStructure) {
        isIncludedInSalaryStructure = includedInSalaryStructure;
    }

    public String getTaxExemptionSubType() {
        return taxExemptionSubType;
    }

    public void setTaxExemptionSubType(String taxExemptionSubType) {
        this.taxExemptionSubType = taxExemptionSubType;
    }

    public String getTaxExemptionSubTypeFormatted() {
        return taxExemptionSubTypeFormatted;
    }

    public void setTaxExemptionSubTypeFormatted(String taxExemptionSubTypeFormatted) {
        this.taxExemptionSubTypeFormatted = taxExemptionSubTypeFormatted;
    }

    public String getTaxExemptSection() {
        return taxExemptSection;
    }

    public void setTaxExemptSection(String taxExemptSection) {
        this.taxExemptSection = taxExemptSection;
    }

    public boolean isCanAllowEmployerContribution() {
        return canAllowEmployerContribution;
    }

    public void setCanAllowEmployerContribution(boolean canAllowEmployerContribution) {
        this.canAllowEmployerContribution = canAllowEmployerContribution;
    }

    public boolean isCanAllowEmployeeContribution() {
        return canAllowEmployeeContribution;
    }

    public void setCanAllowEmployeeContribution(boolean canAllowEmployeeContribution) {
        this.canAllowEmployeeContribution = canAllowEmployeeContribution;
    }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }


}
