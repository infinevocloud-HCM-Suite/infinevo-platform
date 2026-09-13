package com.itsdev.payroll.dto;

public class OrgSetupStepsDTO {

    private boolean isWorkLocationSetup;
    private boolean isEmployeeSetup;
    private boolean isPayScheduleSetup;
    private boolean isPriorPayrollSetup;
    private boolean isOrgTaxSetup;
    private boolean isSalaryComponentsSetup;
    private boolean isEPFSetup = false;
    private boolean isESISetup = false;
    private boolean isPTAXSetup = false;


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