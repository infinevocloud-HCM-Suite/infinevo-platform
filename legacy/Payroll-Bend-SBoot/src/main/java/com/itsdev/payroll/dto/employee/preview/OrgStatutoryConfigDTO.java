package com.itsdev.payroll.dto.employee.preview;

public class OrgStatutoryConfigDTO {

    // ------- EPF -------
    private Boolean epfEnabled;
    private String epfEmployeeContribution;
    private String epfEmployerContribution;
    private Boolean isEdliIncludedSalaryStructure;
    private Boolean isAdminChargesIncludedSalaryStructure;

    // ------- ESI -------
    private Boolean esiEnabled;
    private String esiEmployeeContribution;
    private String esiEmployerContribution;

    public OrgStatutoryConfigDTO() {}

    public OrgStatutoryConfigDTO(
            Boolean epfEnabled,
            String epfEmployeeContribution,
            String epfEmployerContribution,
            Boolean isEdliIncludedSalaryStructure,
            Boolean isAdminChargesIncludedSalaryStructure,
            Boolean esiEnabled,
            String esiEmployeeContribution,
            String esiEmployerContribution) {

        this.epfEnabled = epfEnabled;
        this.epfEmployeeContribution = epfEmployeeContribution;
        this.epfEmployerContribution = epfEmployerContribution;
        this.isEdliIncludedSalaryStructure = isEdliIncludedSalaryStructure;
        this.isAdminChargesIncludedSalaryStructure = isAdminChargesIncludedSalaryStructure;

        this.esiEnabled = esiEnabled;
        this.esiEmployeeContribution = esiEmployeeContribution;
        this.esiEmployerContribution = esiEmployerContribution;
    }

    // ------------------ GETTERS & SETTERS ------------------

    public Boolean getEpfEnabled() { return epfEnabled; }
    public void setEpfEnabled(Boolean epfEnabled) { this.epfEnabled = epfEnabled; }

    public String getEpfEmployeeContribution() { return epfEmployeeContribution; }
    public void setEpfEmployeeContribution(String epfEmployeeContribution) { this.epfEmployeeContribution = epfEmployeeContribution; }

    public String getEpfEmployerContribution() { return epfEmployerContribution; }
    public void setEpfEmployerContribution(String epfEmployerContribution) { this.epfEmployerContribution = epfEmployerContribution; }

    public Boolean getIsEdliIncludedSalaryStructure() { return isEdliIncludedSalaryStructure; }
    public void setIsEdliIncludedSalaryStructure(Boolean isEdliIncludedSalaryStructure) { this.isEdliIncludedSalaryStructure = isEdliIncludedSalaryStructure; }

    public Boolean getIsAdminChargesIncludedSalaryStructure() { return isAdminChargesIncludedSalaryStructure; }
    public void setIsAdminChargesIncludedSalaryStructure(Boolean isAdminChargesIncludedSalaryStructure) { this.isAdminChargesIncludedSalaryStructure = isAdminChargesIncludedSalaryStructure; }

    public Boolean getEsiEnabled() { return esiEnabled; }
    public void setEsiEnabled(Boolean esiEnabled) { this.esiEnabled = esiEnabled; }

    public String getEsiEmployeeContribution() { return esiEmployeeContribution; }
    public void setEsiEmployeeContribution(String esiEmployeeContribution) { this.esiEmployeeContribution = esiEmployeeContribution; }

    public String getEsiEmployerContribution() { return esiEmployerContribution; }
    public void setEsiEmployerContribution(String esiEmployerContribution) { this.esiEmployerContribution = esiEmployerContribution; }
}
