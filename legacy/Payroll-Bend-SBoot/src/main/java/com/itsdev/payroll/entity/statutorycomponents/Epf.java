package com.itsdev.payroll.entity.statutorycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "epf")
public class Epf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean isAdminChargesIncludedCtc;
    private Boolean isEdliIncludedCtc;
    private Boolean considerEarnedSalaryForEpf;
    private String registrationNumber;
    private String epfAdminChargesEmployerContribution;
    private Boolean isEmployerContributionIncludedCtc;
    private Boolean isEmployeeRestrictedBasicEnabled;
    private Boolean isEmployerContributionIncludedSalaryStructure;
    private Boolean isEligibleForAbryScheme;
    private String edliEmployerContribution;
    private String epfEmployeeContribution;
    private Boolean canEnableEdliPfAdminChargesInSalaryStructure;
    private Boolean canProRateRestrictedBasic;
    private Boolean isAssociatedWithEmployee;
    private Integer epsSeniorCategoryAge;
    private Boolean isActive;
    private Boolean canOverrideRestrictedBasic;
    private String epsEmployeeContribution;
    private String epfAdminChargesEmployeeContribution;
    private Boolean isEdliIncludedSalaryStructure;
    private Boolean isAdminChargesIncludedSalaryStructure;
    private String epsEmployerContribution;
    private String deductionCycleFormatted;
    private Boolean isSubsidyApplicableForBothContributions;
    private LocalDate registrationDate;
    private String registrationDateFormatted;
    private String epsEmployerContributionForSeniorcategory;
    private String name;
    private Boolean isEmployerRestrictedBasicEnabled;
    private String epfEmployerContribution;
    private Boolean canEnableEdliPfAdminChargesInCtc;
    private String deductionCycle;
    private String epfEmployerContributionForSeniorcategory;
    private String edliEmployeeContribution;

    @OneToOne
    @JoinColumn(name = "organizationId", unique = true)
    private Organization organization;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Boolean getIsAdminChargesIncludedCtc() { return isAdminChargesIncludedCtc; }
    public void setIsAdminChargesIncludedCtc(Boolean isAdminChargesIncludedCtc) { this.isAdminChargesIncludedCtc = isAdminChargesIncludedCtc; }

    public Boolean getIsEdliIncludedCtc() { return isEdliIncludedCtc; }
    public void setIsEdliIncludedCtc(Boolean isEdliIncludedCtc) { this.isEdliIncludedCtc = isEdliIncludedCtc; }

    public Boolean getConsiderEarnedSalaryForEpf() { return considerEarnedSalaryForEpf; }
    public void setConsiderEarnedSalaryForEpf(Boolean considerEarnedSalaryForEpf) { this.considerEarnedSalaryForEpf = considerEarnedSalaryForEpf; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getEpfAdminChargesEmployerContribution() { return epfAdminChargesEmployerContribution; }
    public void setEpfAdminChargesEmployerContribution(String epfAdminChargesEmployerContribution) { this.epfAdminChargesEmployerContribution = epfAdminChargesEmployerContribution; }

    public Boolean getIsEmployerContributionIncludedCtc() { return isEmployerContributionIncludedCtc; }
    public void setIsEmployerContributionIncludedCtc(Boolean isEmployerContributionIncludedCtc) { this.isEmployerContributionIncludedCtc = isEmployerContributionIncludedCtc; }

    public Boolean getIsEmployeeRestrictedBasicEnabled() { return isEmployeeRestrictedBasicEnabled; }
    public void setIsEmployeeRestrictedBasicEnabled(Boolean isEmployeeRestrictedBasicEnabled) { this.isEmployeeRestrictedBasicEnabled = isEmployeeRestrictedBasicEnabled; }

    public Boolean getIsEmployerContributionIncludedSalaryStructure() { return isEmployerContributionIncludedSalaryStructure; }
    public void setIsEmployerContributionIncludedSalaryStructure(Boolean isEmployerContributionIncludedSalaryStructure) { this.isEmployerContributionIncludedSalaryStructure = isEmployerContributionIncludedSalaryStructure; }

    public Boolean getIsEligibleForAbryScheme() { return isEligibleForAbryScheme; }
    public void setIsEligibleForAbryScheme(Boolean isEligibleForAbryScheme) { this.isEligibleForAbryScheme = isEligibleForAbryScheme; }

    public String getEdliEmployerContribution() { return edliEmployerContribution; }
    public void setEdliEmployerContribution(String edliEmployerContribution) { this.edliEmployerContribution = edliEmployerContribution; }

    public String getEpfEmployeeContribution() { return epfEmployeeContribution; }
    public void setEpfEmployeeContribution(String epfEmployeeContribution) { this.epfEmployeeContribution = epfEmployeeContribution; }

    public Boolean getCanEnableEdliPfAdminChargesInSalaryStructure() { return canEnableEdliPfAdminChargesInSalaryStructure; }
    public void setCanEnableEdliPfAdminChargesInSalaryStructure(Boolean canEnableEdliPfAdminChargesInSalaryStructure) { this.canEnableEdliPfAdminChargesInSalaryStructure = canEnableEdliPfAdminChargesInSalaryStructure; }

    public Boolean getCanProRateRestrictedBasic() { return canProRateRestrictedBasic; }
    public void setCanProRateRestrictedBasic(Boolean canProRateRestrictedBasic) { this.canProRateRestrictedBasic = canProRateRestrictedBasic; }

    public Boolean getIsAssociatedWithEmployee() { return isAssociatedWithEmployee; }
    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) { this.isAssociatedWithEmployee = isAssociatedWithEmployee; }

    public Integer getEpsSeniorCategoryAge() { return epsSeniorCategoryAge; }
    public void setEpsSeniorCategoryAge(Integer epsSeniorCategoryAge) { this.epsSeniorCategoryAge = epsSeniorCategoryAge; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getCanOverrideRestrictedBasic() { return canOverrideRestrictedBasic; }
    public void setCanOverrideRestrictedBasic(Boolean canOverrideRestrictedBasic) { this.canOverrideRestrictedBasic = canOverrideRestrictedBasic; }

    public String getEpsEmployeeContribution() { return epsEmployeeContribution; }
    public void setEpsEmployeeContribution(String epsEmployeeContribution) { this.epsEmployeeContribution = epsEmployeeContribution; }

    public String getEpfAdminChargesEmployeeContribution() { return epfAdminChargesEmployeeContribution; }
    public void setEpfAdminChargesEmployeeContribution(String epfAdminChargesEmployeeContribution) { this.epfAdminChargesEmployeeContribution = epfAdminChargesEmployeeContribution; }

    public Boolean getIsEdliIncludedSalaryStructure() { return isEdliIncludedSalaryStructure; }
    public void setIsEdliIncludedSalaryStructure(Boolean isEdliIncludedSalaryStructure) { this.isEdliIncludedSalaryStructure = isEdliIncludedSalaryStructure; }

    public Boolean getIsAdminChargesIncludedSalaryStructure() { return isAdminChargesIncludedSalaryStructure; }
    public void setIsAdminChargesIncludedSalaryStructure(Boolean isAdminChargesIncludedSalaryStructure) { this.isAdminChargesIncludedSalaryStructure = isAdminChargesIncludedSalaryStructure; }

    public String getEpsEmployerContribution() { return epsEmployerContribution; }
    public void setEpsEmployerContribution(String epsEmployerContribution) { this.epsEmployerContribution = epsEmployerContribution; }

    public String getDeductionCycleFormatted() { return deductionCycleFormatted; }
    public void setDeductionCycleFormatted(String deductionCycleFormatted) { this.deductionCycleFormatted = deductionCycleFormatted; }

    public Boolean getIsSubsidyApplicableForBothContributions() { return isSubsidyApplicableForBothContributions; }
    public void setIsSubsidyApplicableForBothContributions(Boolean isSubsidyApplicableForBothContributions) { this.isSubsidyApplicableForBothContributions = isSubsidyApplicableForBothContributions; }

    public LocalDate getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }

    public String getRegistrationDateFormatted() { return registrationDateFormatted; }
    public void setRegistrationDateFormatted(String registrationDateFormatted) { this.registrationDateFormatted = registrationDateFormatted; }

    public String getEpsEmployerContributionForSeniorcategory() { return epsEmployerContributionForSeniorcategory; }
    public void setEpsEmployerContributionForSeniorcategory(String epsEmployerContributionForSeniorcategory) { this.epsEmployerContributionForSeniorcategory = epsEmployerContributionForSeniorcategory; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsEmployerRestrictedBasicEnabled() { return isEmployerRestrictedBasicEnabled; }
    public void setIsEmployerRestrictedBasicEnabled(Boolean isEmployerRestrictedBasicEnabled) { this.isEmployerRestrictedBasicEnabled = isEmployerRestrictedBasicEnabled; }

    public String getEpfEmployerContribution() { return epfEmployerContribution; }
    public void setEpfEmployerContribution(String epfEmployerContribution) { this.epfEmployerContribution = epfEmployerContribution; }

    public Boolean getCanEnableEdliPfAdminChargesInCtc() { return canEnableEdliPfAdminChargesInCtc; }
    public void setCanEnableEdliPfAdminChargesInCtc(Boolean canEnableEdliPfAdminChargesInCtc) { this.canEnableEdliPfAdminChargesInCtc = canEnableEdliPfAdminChargesInCtc; }

    public String getDeductionCycle() { return deductionCycle; }
    public void setDeductionCycle(String deductionCycle) { this.deductionCycle = deductionCycle; }

    public String getEpfEmployerContributionForSeniorcategory() { return epfEmployerContributionForSeniorcategory; }
    public void setEpfEmployerContributionForSeniorcategory(String epfEmployerContributionForSeniorcategory) { this.epfEmployerContributionForSeniorcategory = epfEmployerContributionForSeniorcategory; }

    public String getEdliEmployeeContribution() { return edliEmployeeContribution; }
    public void setEdliEmployeeContribution(String edliEmployeeContribution) { this.edliEmployeeContribution = edliEmployeeContribution; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}
