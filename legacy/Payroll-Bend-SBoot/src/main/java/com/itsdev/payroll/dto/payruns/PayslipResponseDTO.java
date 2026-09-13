package com.itsdev.payroll.dto.payruns;


import java.util.List;

import com.itsdev.payroll.dto.employee.EpfComponentDTO;
import com.itsdev.payroll.dto.employee.EsiComponentDTO;
import com.itsdev.payroll.dto.organization.WorkLocationDTO;

public class PayslipResponseDTO {

    private String payRunId;
    private String payRunType;
    private String payPeriod;
    private String formattedPayPeriod;
    private String payDate;
    private String formattedPayDate;
    private Double grossEarnings;
    private Double totalDeductions;
    private Double totalReimbursements;
    private Double netPay;
    private String paymentStatus;
    private Double professionalTax;

    private EmployeeSummaryDTO employeeSummary;
    private PayrollSummaryDTO payrollSummary;
    private List<EarningComponentDTO> earningComponents;
    
    private String organizationName;
    private String organizationFileUrl;
    private WorkLocationDTO workLocation;




    private List<EpfComponentDTO> epfComponents;
    private List<EsiComponentDTO> esiComponents;

    private Double lop;

    private Double totalNoOfLeaves; // maps from entity totalNoOfLeaves

    public Double getTotalNoOfLeaves() {
        return totalNoOfLeaves;
    }

    public void setTotalNoOfLeaves(Double totalNoOfLeaves) {
        this.totalNoOfLeaves = totalNoOfLeaves;
    }

    public Double getLop() {
        return lop;
    }

    public void setLop(Double lop) {
        this.lop = lop;
    }

    public Double getProfessionalTax() {
        return professionalTax;
    }

    public void setProfessionalTax(Double professionalTax) {
        this.professionalTax = professionalTax;
    }

    private Double paidDays;

    public Double getPaidDays() {
        return paidDays;
    }

    public void setPaidDays(Double paidDays) {
        this.paidDays = paidDays;
    }


    public List<EpfComponentDTO> getEpfComponents() {
        return epfComponents;
    }
    public void setEpfComponents(List<EpfComponentDTO> epfComponents) {
        this.epfComponents = epfComponents;
    }

    public List<EsiComponentDTO> getEsiComponents() {
        return esiComponents;
    }
    public void setEsiComponents(List<EsiComponentDTO> esiComponents) {
        this.esiComponents = esiComponents;
    }

    private Double monthlyTds;

    private Double claimDeduction;
    private Double claimReimbursement;




    // ... existing getters/setters

    public Double getMonthlyTds() {
        return monthlyTds;
    }

    public void setMonthlyTds(Double monthlyTds) {
        this.monthlyTds = monthlyTds;
    }

    public Double getClaimDeduction() {
        return claimDeduction;
    }

    public void setClaimDeduction(Double claimDeduction) {
        this.claimDeduction = claimDeduction;
    }

    public Double getClaimReimbursement() {
        return claimReimbursement;
    }

    public void setClaimReimbursement(Double claimReimbursement) {
        this.claimReimbursement = claimReimbursement;
    }
    

    public String getOrganizationName() {
		return organizationName;
	}
	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}
	public String getOrganizationFileUrl() {
		return organizationFileUrl;
	}
	public void setOrganizationFileUrl(String organizationFileUrl) {
		this.organizationFileUrl = organizationFileUrl;
	}
	public WorkLocationDTO getWorkLocation() {
		return workLocation;
	}
	public void setWorkLocation(WorkLocationDTO workLocation) {
		this.workLocation = workLocation;
	}
	// --- Getters and Setters ---
    public String getPayRunId() { return payRunId; }
    public void setPayRunId(String payRunId) { this.payRunId = payRunId; }

    public String getPayRunType() { return payRunType; }
    public void setPayRunType(String payRunType) { this.payRunType = payRunType; }

    public String getPayPeriod() { return payPeriod; }
    public void setPayPeriod(String payPeriod) { this.payPeriod = payPeriod; }

    public String getFormattedPayPeriod() { return formattedPayPeriod; }
    public void setFormattedPayPeriod(String formattedPayPeriod) { this.formattedPayPeriod = formattedPayPeriod; }

    public String getPayDate() { return payDate; }
    public void setPayDate(String payDate) { this.payDate = payDate; }

    public String getFormattedPayDate() { return formattedPayDate; }
    public void setFormattedPayDate(String formattedPayDate) { this.formattedPayDate = formattedPayDate; }

    public Double getGrossEarnings() { return grossEarnings; }
    public void setGrossEarnings(Double grossEarnings) { this.grossEarnings = grossEarnings; }

    public Double getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(Double totalDeductions) { this.totalDeductions = totalDeductions; }

    public Double getTotalReimbursements() { return totalReimbursements; }
    public void setTotalReimbursements(Double totalReimbursements) { this.totalReimbursements = totalReimbursements; }

    public Double getNetPay() { return netPay; }
    public void setNetPay(Double netPay) { this.netPay = netPay; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public EmployeeSummaryDTO getEmployeeSummary() { return employeeSummary; }
    public void setEmployeeSummary(EmployeeSummaryDTO employeeSummary) { this.employeeSummary = employeeSummary; }

    public PayrollSummaryDTO getPayrollSummary() { return payrollSummary; }
    public void setPayrollSummary(PayrollSummaryDTO payrollSummary) { this.payrollSummary = payrollSummary; }

    public List<EarningComponentDTO> getEarningComponents() { return earningComponents; }
    public void setEarningComponents(List<EarningComponentDTO> earningComponents) { this.earningComponents = earningComponents; }
}

