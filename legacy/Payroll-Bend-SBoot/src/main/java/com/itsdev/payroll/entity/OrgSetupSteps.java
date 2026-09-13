package com.itsdev.payroll.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "orgSetupSteps")
public class OrgSetupSteps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String organizationId;

    private boolean isWorkLocationSetup = false;
    private boolean isEmployeeSetup = false;
    private boolean isPayScheduleSetup = false;
    private boolean isPriorPayrollSetup = false;
    private boolean isOrgTaxSetup = false;
    private boolean isSalaryComponentsSetup = false;
    private boolean isEPFSetup = false;
    private boolean isESISetup = false;
    private boolean isPTAXSetup = false;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public boolean isWorkLocationSetup() { return isWorkLocationSetup; }
    public void setWorkLocationSetup(boolean workLocationSetup) { isWorkLocationSetup = workLocationSetup; }

    public boolean isEmployeeSetup() { return isEmployeeSetup; }
    public void setEmployeeSetup(boolean employeeSetup) { isEmployeeSetup = employeeSetup; }

    public boolean isPayScheduleSetup() { return isPayScheduleSetup; }
    public void setPayScheduleSetup(boolean payScheduleSetup) { isPayScheduleSetup = payScheduleSetup; }

    public boolean isPriorPayrollSetup() { return isPriorPayrollSetup; }
    public void setPriorPayrollSetup(boolean priorPayrollSetup) { isPriorPayrollSetup = priorPayrollSetup; }

    public boolean isOrgTaxSetup() { return isOrgTaxSetup; }
    public void setOrgTaxSetup(boolean orgTaxSetup) { isOrgTaxSetup = orgTaxSetup; }

    public boolean isSalaryComponentsSetup() { return isSalaryComponentsSetup; }
    public void setSalaryComponentsSetup(boolean salaryComponentsSetup) { isSalaryComponentsSetup = salaryComponentsSetup; }

    public boolean isEPFSetup() { return isEPFSetup; }
    public void setEPFSetup(boolean epfSetup) { isEPFSetup = epfSetup; }

    public boolean isESISetup() { return isESISetup; }
    public void setESISetup(boolean esiSetup) { isESISetup = esiSetup; }

    public boolean isPTAXSetup() { return isPTAXSetup; }
    public void setPTAXSetup(boolean ptaxSetup) { isPTAXSetup = ptaxSetup; }
}
