package com.itsdev.payroll.dto.payruns;


import com.itsdev.payroll.dto.employee.EpfComponentDTO;
import com.itsdev.payroll.dto.statutorycomponents.SlabDetailDTO;

import java.util.List;

public class EmployeePayRunDTO {
    private String employeeId;
    private String employeeNumber;
    private String employeeName;
    private String fullName;

    private String paymentStatus;

    private String paymentMode;

    private Double totalDeductions;
 
    private Double totalBenefits;
 
    private Double totalDonations;

    private Double totalTaxes;

    private Double totalEarnings;



	private Double totalReimbursements;
  
    private Double netPay;
  
    private Double grossDeductions;
  
    private Double paidDays;

    private boolean isTaxOverridden;
    private boolean isEmployeeHavingHoldSalary;
    private String employeeStatus;
 
    private boolean canSkipWithJoineeArrear;
    private boolean isBonusEarningExistForEmployee;
    private Double bonus;
    
    private Double monthlySalary;

	private Double totalNoOfLeaves;

	private Double LOP;

	// new field for PT eligibility
	private Boolean eligibleForPt;
	
	
	 private Double monthlyTds;

	 // Claim deduction amount (from employee deduction module)
	 private Double claimDeduction;

	 // Claim reimbursement amount (from employee reimbursement module)
	 private Double claimReimbursement;

	 // Status of claim deduction (ACTIVE, INPAYRUN, PROCESSED)
	 private String claimDeductionStatus;

	 // Payment status of claim reimbursement (UNPAID, INPAYRUN, PAID)
	 private String claimReimbursementStatus;


	// ... existing getters/setters

	public Double getMonthlyTds() {
		return monthlyTds;
	}

	public void setMonthlyTds(Double monthlyTds) {
		this.monthlyTds = monthlyTds;
	}

	public Boolean getEligibleForPt() {
		return eligibleForPt;
	}

	public void setEligibleForPt(Boolean eligibleForPt) {
		this.eligibleForPt = eligibleForPt;
	}

	// inside class EmployeePayRunDTO
	private List<EpfComponentDTO> epfComponents;

	// inside class EmployeePayRunDTO
	private List<SlabDetailDTO> professionalTaxSlabs;

	public List<SlabDetailDTO> getProfessionalTaxSlabs() {
		return professionalTaxSlabs;
	}

	public void setProfessionalTaxSlabs(List<SlabDetailDTO> professionalTaxSlabs) {
		this.professionalTaxSlabs = professionalTaxSlabs;
	}

	public List<EpfComponentDTO> getEpfComponents() {
		return epfComponents;
	}

	public void setEpfComponents(List<EpfComponentDTO> epfComponents) {
		this.epfComponents = epfComponents;
	}

    public Double getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(Double monthlySalary) {
        this.monthlySalary = monthlySalary;
    }
    
    
	public String getEmployeeId() {
		return employeeId;
	}
	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}
	public String getEmployeeNumber() {
		return employeeNumber;
	}
	public void setEmployeeNumber(String employeeNumber) {
		this.employeeNumber = employeeNumber;
	}
	public String getEmployeeName() {
		return employeeName;
	}
	public void setEmployeeName(String employeeName) {
		this.employeeName = employeeName;
	}
	public String getFullName() {
		return fullName;
	}
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}
	public String getPaymentStatus() {
		return paymentStatus;
	}
	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}
	public String getPaymentMode() {
		return paymentMode;
	}
	public void setPaymentMode(String paymentMode) {
		this.paymentMode = paymentMode;
	}
	public Double getTotalDeductions() {
		return totalDeductions;
	}
	public void setTotalDeductions(Double totalDeductions) {
		this.totalDeductions = totalDeductions;
	}
	public Double getTotalBenefits() {
		return totalBenefits;
	}
	public void setTotalBenefits(Double totalBenefits) {
		this.totalBenefits = totalBenefits;
	}
	public Double getTotalDonations() {
		return totalDonations;
	}
	public void setTotalDonations(Double totalDonations) {
		this.totalDonations = totalDonations;
	}
	public Double getTotalTaxes() {
		return totalTaxes;
	}
	public void setTotalTaxes(Double totalTaxes) {
		this.totalTaxes = totalTaxes;
	}
	public Double getTotalEarnings() {
		return totalEarnings;
	}
	public void setTotalEarnings(Double totalEarnings) {
		this.totalEarnings = totalEarnings;
	}
	public Double getTotalReimbursements() {
		return totalReimbursements;
	}
	public void setTotalReimbursements(Double totalReimbursements) {
		this.totalReimbursements = totalReimbursements;
	}
	public Double getNetPay() {
		return netPay;
	}
	public void setNetPay(Double netPay) {
		this.netPay = netPay;
	}
	public Double getGrossDeductions() {
		return grossDeductions;
	}
	public void setGrossDeductions(Double grossDeductions) {
		this.grossDeductions = grossDeductions;
	}
	public Double getPaidDays() {
		return paidDays;
	}
	public void setPaidDays(Double paidDays) {
		this.paidDays = paidDays;
	}
	public boolean isTaxOverridden() {
		return isTaxOverridden;
	}
	public void setTaxOverridden(boolean isTaxOverridden) {
		this.isTaxOverridden = isTaxOverridden;
	}
	public boolean isEmployeeHavingHoldSalary() {
		return isEmployeeHavingHoldSalary;
	}
	public void setEmployeeHavingHoldSalary(boolean isEmployeeHavingHoldSalary) {
		this.isEmployeeHavingHoldSalary = isEmployeeHavingHoldSalary;
	}
	public String getEmployeeStatus() {
		return employeeStatus;
	}
	public void setEmployeeStatus(String employeeStatus) {
		this.employeeStatus = employeeStatus;
	}
	public boolean isCanSkipWithJoineeArrear() {
		return canSkipWithJoineeArrear;
	}
	public void setCanSkipWithJoineeArrear(boolean canSkipWithJoineeArrear) {
		this.canSkipWithJoineeArrear = canSkipWithJoineeArrear;
	}
	public boolean isBonusEarningExistForEmployee() {
		return isBonusEarningExistForEmployee;
	}
	public void setBonusEarningExistForEmployee(boolean isBonusEarningExistForEmployee) {
		this.isBonusEarningExistForEmployee = isBonusEarningExistForEmployee;
	}

	public Double getTotalNoOfLeaves() {
		return totalNoOfLeaves;
	}

	public void setTotalNoOfLeaves(Double totalNoOfLeaves) {
		this.totalNoOfLeaves = totalNoOfLeaves;
	}

	public Double getLOP() {
		return LOP;
	}

	public void setLOP(Double LOP) {
		this.LOP = LOP;
	}

	public Double getBonus() {
		return bonus;
	}

	public void setBonus(Double bonus) {
		this.bonus = bonus;
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

	public String getClaimDeductionStatus() {
		return claimDeductionStatus;
	}

	public void setClaimDeductionStatus(String claimDeductionStatus) {
		this.claimDeductionStatus = claimDeductionStatus;
	}

	public String getClaimReimbursementStatus() {
		return claimReimbursementStatus;
	}

	public void setClaimReimbursementStatus(String claimReimbursementStatus) {
		this.claimReimbursementStatus = claimReimbursementStatus;
	}

}

