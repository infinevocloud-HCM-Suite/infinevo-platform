package com.itsdev.payroll.dto.statutorycomponents;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public class EpfDTO {

    private Long id;
    private boolean isAdminChargesIncludedCtc;
    private boolean isEdliIncludedCtc;
    private boolean considerEarnedSalaryForEpf;
    private String registrationNumber;
    private String epfAdminChargesEmployerContribution;
    private boolean isEmployerContributionIncludedCtc;
    private boolean isEmployeeRestrictedBasicEnabled;
    private boolean isEmployerContributionIncludedSalaryStructure;
    private boolean isEligibleForAbryScheme;
    private String edliEmployerContribution;
    private String epfEmployeeContribution;
    private boolean canEnableEdliPfAdminChargesInSalaryStructure;
    private boolean canProRateRestrictedBasic;
    private boolean isAssociatedWithEmployee;
    private int epsSeniorCategoryAge;
    private boolean isActive;
    private boolean canOverrideRestrictedBasic;
    private String epsEmployeeContribution;
    private String epfAdminChargesEmployeeContribution;
    private boolean isEdliIncludedSalaryStructure;
    private boolean isAdminChargesIncludedSalaryStructure;
    private String epsEmployerContribution;
    private String deductionCycleFormatted;
    private boolean isSubsidyApplicableForBothContributions;
    private LocalDate registrationDate;
    private String registrationDateFormatted;
    private String epsEmployerContributionForSeniorcategory;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    private boolean isEmployerRestrictedBasicEnabled;
    private String epfEmployerContribution;
    private boolean canEnableEdliPfAdminChargesInCtc;
    private String deductionCycle;
    private String epfEmployerContributionForSeniorcategory;
    private String edliEmployeeContribution;

    public EpfDTO() {}


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
}

