package com.itsdev.payroll.entity.payruns;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employee_payruns")
public class EmployeePayRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employeeId")
    private String employeeId;

    @Column(name = "employeeNumber")
    private String employeeNumber;

    @Column(name = "employeeName")
    private String employeeName;

    @Column(name = "fullName")
    private String fullName;

    @Column(name = "paymentStatus")
    private String paymentStatus; // e.g., "yet_to_pay"

    @Column(name = "paymentMode")
    private String paymentMode; // e.g., "BANK_TRANSFER"

    @Column(name = "totalEarnings")
    private Double totalEarnings;

    @Column(name = "totalDeductions")
    private Double totalDeductions;

    @Column(name = "totalTaxes")
    private Double totalTaxes;

    @Column(name = "totalBenefits")
    private Double totalBenefits;

    @Column(name = "totalReimbursements")
    private Double totalReimbursements;

    @Column(name = "netPay")
    private Double netPay;

    @Column(name = "monthlySalary")
    private Double monthlySalary;

    @Column(name = "paidDays")
    private Double paidDays;

    @Column(name = "employeeStatus")
    private String employeeStatus;

    @Column(name = "bonusEarningExistForEmployee")
    private Boolean bonusEarningExistForEmployee;

    @Column(name = "bonus")
    private Double bonus;

    @Column(name = "taxOverridden")
    private Boolean taxOverridden;

    @Column(name = "employeeHavingHoldSalary")
    private Boolean employeeHavingHoldSalary;

	@Column(name = "totalNoOfLeaves")
	private Double totalNoOfLeaves;
	@Column(name = "LOP")
	private Double LOP;

    // ✅ Store the same payrunId for easy lookup / external reference
    @Column(name = "payrunId")
    private String payrunId;

    // ✅ Relationship to parent PayRun
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payrun_ref_id")
    private PayRun payRun;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_ref_id", referencedColumnName = "employee_id")
    private BasicDetails employee;
    
     @Column(name = "monthlyTds")
	 private Double monthlyTds;

	 @Column(name = "claim_deduction")
	 private Double claimDeduction;

	 @Column(name = "claim_reimbursement")
	 private Double claimReimbursement;

	 @Column(name = "claim_deduction_status")
	 private String claimDeductionStatus;

	 @Column(name = "claim_reimbursement_status")
	 private String claimReimbursementStatus;


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


	public BasicDetails getEmployee() {
		return employee;
	}

	public void setEmployee(BasicDetails employee) {
		this.employee = employee;
	}

	public String getPayrunId() {
		return payrunId;
	}

	public void setPayrunId(String payrunId) {
		this.payrunId = payrunId;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public Double getTotalEarnings() {
		return totalEarnings;
	}

	public void setTotalEarnings(Double totalEarnings) {
		this.totalEarnings = totalEarnings;
	}

	public Double getTotalDeductions() {
		return totalDeductions;
	}

	public void setTotalDeductions(Double totalDeductions) {
		this.totalDeductions = totalDeductions;
	}

	public Double getTotalTaxes() {
		return totalTaxes;
	}

	public void setTotalTaxes(Double totalTaxes) {
		this.totalTaxes = totalTaxes;
	}

	public Double getTotalBenefits() {
		return totalBenefits;
	}

	public void setTotalBenefits(Double totalBenefits) {
		this.totalBenefits = totalBenefits;
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

	public Double getMonthlySalary() {
		return monthlySalary;
	}

	public void setMonthlySalary(Double monthlySalary) {
		this.monthlySalary = monthlySalary;
	}

	public Double getPaidDays() {
		return paidDays;
	}

	public void setPaidDays(Double paidDays) {
		this.paidDays = paidDays;
	}

	public String getEmployeeStatus() {
		return employeeStatus;
	}

	public void setEmployeeStatus(String employeeStatus) {
		this.employeeStatus = employeeStatus;
	}

	public Boolean getBonusEarningExistForEmployee() {
		return bonusEarningExistForEmployee;
	}

	public void setBonusEarningExistForEmployee(Boolean bonusEarningExistForEmployee) {
		this.bonusEarningExistForEmployee = bonusEarningExistForEmployee;
	}

	public Boolean getTaxOverridden() {
		return taxOverridden;
	}

	public void setTaxOverridden(Boolean taxOverridden) {
		this.taxOverridden = taxOverridden;
	}

	public Boolean getEmployeeHavingHoldSalary() {
		return employeeHavingHoldSalary;
	}

	public void setEmployeeHavingHoldSalary(Boolean employeeHavingHoldSalary) {
		this.employeeHavingHoldSalary = employeeHavingHoldSalary;
	}

	public PayRun getPayRun() {
		return payRun;
	}

	public void setPayRun(PayRun payRun) {
		this.payRun = payRun;
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
}

