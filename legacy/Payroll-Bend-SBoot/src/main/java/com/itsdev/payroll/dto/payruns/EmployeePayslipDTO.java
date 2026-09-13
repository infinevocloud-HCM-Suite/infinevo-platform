package com.itsdev.payroll.dto.payruns;

import com.itsdev.payroll.dto.employee.EpfComponentDTO;
import com.itsdev.payroll.dto.employee.EsiComponentDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class EmployeePayslipDTO {
	
    private String payrunId;
    private String payrollType;

    private String payPeriod;

    private LocalDate payDate;
   
    private Double netPay;

    private Double grossPay;

    private Double grossEarnings;

	private Double totalTaxes;



    private Double grossDeductions;

    private Double grossReimbursements;

    private Boolean isPreviousEmploymentPayslip;
    private String paymentStatus;

	private Double paidDays;

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

	public Double getTotalTaxes() {
		return totalTaxes;
	}

	public void setTotalTaxes(Double totalTaxes) {
		this.totalTaxes = totalTaxes;
	}

	public Double getPaidDays() {
		return paidDays;
	}

	public void setPaidDays(Double paidDays) {
		this.paidDays = paidDays;
	}

	// ✅ NEW FIELDS
	private List<EpfComponentDTO> epfComponents;
	private List<EsiComponentDTO> esiComponents;

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




	// ... existing getters/setters

	public Double getMonthlyTds() {
		return monthlyTds;
	}

	public void setMonthlyTds(Double monthlyTds) {
		this.monthlyTds = monthlyTds;
	}

	public String getPayrunId() {
		return payrunId;
	}

	public void setPayrunId(String payrunId) {
		this.payrunId = payrunId;
	}

	public String getPayrollType() {
		return payrollType;
	}
	public void setPayrollType(String payrollType) {
		this.payrollType = payrollType;
	}
	public String getPayPeriod() {
		return payPeriod;
	}
	public void setPayPeriod(String payPeriod) {
		this.payPeriod = payPeriod;
	}
	public LocalDate getPayDate() {
		return payDate;
	}
	public void setPayDate(LocalDate payDate) {
		this.payDate = payDate;
	}
	public Double getNetPay() {
		return netPay;
	}
	public void setNetPay(Double netPay) {
		this.netPay = netPay;
	}
	public Double getGrossPay() {
		return grossPay;
	}
	public void setGrossPay(Double grossPay) {
		this.grossPay = grossPay;
	}
	public Double getGrossEarnings() {
		return grossEarnings;
	}
	public void setGrossEarnings(Double grossEarnings) {
		this.grossEarnings = grossEarnings;
	}
	public Double getGrossDeductions() {
		return grossDeductions;
	}
	public void setGrossDeductions(Double grossDeductions) {
		this.grossDeductions = grossDeductions;
	}
	public Double getGrossReimbursements() {
		return grossReimbursements;
	}
	public void setGrossReimbursements(Double grossReimbursements) {
		this.grossReimbursements = grossReimbursements;
	}
	public Boolean getIsPreviousEmploymentPayslip() {
		return isPreviousEmploymentPayslip;
	}
	public void setIsPreviousEmploymentPayslip(Boolean isPreviousEmploymentPayslip) {
		this.isPreviousEmploymentPayslip = isPreviousEmploymentPayslip;
	}
	public String getPaymentStatus() {
		return paymentStatus;
	}
	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}
    

    
    
    
    
    
    
    
}