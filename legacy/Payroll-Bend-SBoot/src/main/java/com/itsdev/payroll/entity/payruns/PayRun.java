package com.itsdev.payroll.entity.payruns;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payruns")
public class PayRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payrunId", unique = true)
    private String payrunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private PayRunType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PayRunStatus status;

    @Column(name = "isPaymentDue")
    private Boolean isPaymentDue;

    @Column(name = "paymentStatus")
    private String paymentStatus;

    @Column(name = "payPeriodStartDate")
    private LocalDate payPeriodStartDate;

    @Column(name = "payPeriodEndDate")
    private LocalDate payPeriodEndDate;

    @Column(name = "payDate")
    private LocalDate payDate;

    @Column(name = "processingPeriod")
    private String processingPeriod;

    @Column(name = "payrollTotal")
    private BigDecimal payrollTotal;

    @Column(name = "statusInfo", length = 500)
    private String statusInfo;

    @Column(name = "noOfEmployees")
    private Integer noOfEmployees;

    @Column(name = "approvalType")
    private String approvalType;

    @Column(name = "approvalDetails", length = 1000)
    private String approvalDetails;

    @Column(name = "approvedDate")
    private LocalDate approvedDate;

    @Column(name = "compensationName")
    private String compensationName;

    @Column(name = "totalNetPay")
    private BigDecimal totalNetPay;

    @Column(name = "totalTaxes")
    private BigDecimal totalTaxes;

    @Column(name = "totalBenefits")
    private BigDecimal totalBenefits;

    @Column(name = "totalDonations")
    private BigDecimal totalDonations;

    @Column(name = "totalDeductions")
    private BigDecimal totalDeductions;

    @Column(name = "totalPayrollCost")
    private BigDecimal totalPayrollCost;

    @Column(name = "totalBonus")
    private BigDecimal totalBonus;

    @Column(name = "totalClaimDeduction")
    private BigDecimal totalClaimDeduction;

    @Column(name = "totalClaimReimbursement")
    private BigDecimal totalClaimReimbursement;

    @Column(name = "baseDays")
    private Double baseDays;

    @Column(name = "noOfSkippedEmployees")
    private Integer noOfSkippedEmployees;

    @Column(name = "canEditPaydate")
    private Boolean canEditPaydate;

    @Column(name = "canPostPayrunTransactions")
    private Boolean canPostPayrunTransactions;

    @Column(name = "hasDirectDepositPayments")
    private Boolean hasDirectDepositPayments;

    @Column(name = "hasNonDirectDepositPayments")
    private Boolean hasNonDirectDepositPayments;

    @Column(name = "totalUnpaidDdAmount")
    private BigDecimal totalUnpaidDdAmount;

    @Column(name = "totalYetToInitiateDdAmount")
    private BigDecimal totalYetToInitiateDdAmount;

    @Column(name = "timeLeftForPayrollApprovalInMillis")
    private Long timeLeftForPayrollApprovalInMillis;

    @Column(name = "isBonusEarningExist")
    private Boolean isBonusEarningExist;

    @Column(name = "isPayrollFailureExist")
    private Boolean isPayrollFailureExist;

    @Column(name = "isProofMode")
    private Boolean isProofMode;

    @Column(name = "isProfessionalTaxOverrideAllowed")
    private Boolean isProfessionalTaxOverrideAllowed;

    @Column(name = "isSalaryHoldAllowed")
    private Boolean isSalaryHoldAllowed;

    @Column(name = "isClaimsAndDeclarationsLockedForAllEmployees")
    private Boolean isClaimsAndDeclarationsLockedForAllEmployees;

    @Column(name = "isPreviousPayrollPresent")
    private Boolean isPreviousPayrollPresent;

    @Column(name = "isEmployeePayrollDataRevised")
    private Boolean isEmployeePayrollDataRevised;

    @Column(name = "isPayslipSent")
    private Boolean isPayslipSent;

    // For simplicity we store some JSON as string (variable_earning list etc.)
    @Column(name = "earning", columnDefinition = "TEXT")
    private String earningJson;

    @Column(name = "variablePayEarningsListJson", columnDefinition = "TEXT")
    private String variablePayEarningsListJson;

    @Column(name = "deductions", columnDefinition = "TEXT")
    private String deductionsJson;

    @Column(name = "expenseBatchesDetailsJson", columnDefinition = "TEXT")
    private String expenseBatchesDetailsJson;

    @Column(name = "processingExtraJson", columnDefinition = "TEXT")
    private String processingExtraJson;

    @Column(name = "rejectedReason")
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;
    
    @OneToMany(mappedBy = "payRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EmployeePayRun> employeePayRuns = new ArrayList<>();
    
    @Column(name = "totalEpfContribution")
    private BigDecimal totalEpfContribution;

    @Column(name = "totalEsiContribution")
    private BigDecimal totalEsiContribution;

    @Column(name = "totalEdliContribution")
    private BigDecimal totalEdliContribution;

    @Column(name = "totalEpfAdminCharges")
    private BigDecimal totalEpfAdminCharges;
    

    public BigDecimal getTotalEpfContribution() {
		return totalEpfContribution;
	}

	public void setTotalEpfContribution(BigDecimal totalEpfContribution) {
		this.totalEpfContribution = totalEpfContribution;
	}



	public BigDecimal getTotalEsiContribution() {
		return totalEsiContribution;
	}

	public void setTotalEsiContribution(BigDecimal totalEsiContribution) {
		this.totalEsiContribution = totalEsiContribution;
	}

	public BigDecimal getTotalEdliContribution() {
		return totalEdliContribution;
	}

	public void setTotalEdliContribution(BigDecimal totalEdliContribution) {
		this.totalEdliContribution = totalEdliContribution;
	}

	public BigDecimal getTotalEpfAdminCharges() {
		return totalEpfAdminCharges;
	}

	public void setTotalEpfAdminCharges(BigDecimal totalEpfAdminCharges) {
		this.totalEpfAdminCharges = totalEpfAdminCharges;
	}

	public List<EmployeePayRun> getEmployeePayRuns() {
		return employeePayRuns;
	}

	public void setEmployeePayRuns(List<EmployeePayRun> employeePayRuns) {
		this.employeePayRuns = employeePayRuns;
	}

	public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPayrunId() {
        return payrunId;
    }

    public void setPayrunId(String payrunId) {
        this.payrunId = payrunId;
    }

    public PayRunType getType() {
        return type;
    }

    public void setType(PayRunType type) {
        this.type = type;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayRunStatus status) {
        this.status = status;
    }

    public Boolean getPaymentDue() {
        return isPaymentDue;
    }

    public void setPaymentDue(Boolean paymentDue) {
        isPaymentDue = paymentDue;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDate getPayPeriodStartDate() {
        return payPeriodStartDate;
    }

    public void setPayPeriodStartDate(LocalDate payPeriodStartDate) {
        this.payPeriodStartDate = payPeriodStartDate;
    }

    public LocalDate getPayPeriodEndDate() {
        return payPeriodEndDate;
    }

    public void setPayPeriodEndDate(LocalDate payPeriodEndDate) {
        this.payPeriodEndDate = payPeriodEndDate;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public String getProcessingPeriod() {
        return processingPeriod;
    }

    public void setProcessingPeriod(String processingPeriod) {
        this.processingPeriod = processingPeriod;
    }

    public BigDecimal getPayrollTotal() {
        return payrollTotal;
    }

    public void setPayrollTotal(BigDecimal payrollTotal) {
        this.payrollTotal = payrollTotal;
    }

    public String getStatusInfo() {
        return statusInfo;
    }

    public void setStatusInfo(String statusInfo) {
        this.statusInfo = statusInfo;
    }

    public Integer getNoOfEmployees() {
        return noOfEmployees;
    }

    public void setNoOfEmployees(Integer noOfEmployees) {
        this.noOfEmployees = noOfEmployees;
    }

    public String getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(String approvalType) {
        this.approvalType = approvalType;
    }

    public String getApprovalDetails() {
        return approvalDetails;
    }

    public void setApprovalDetails(String approvalDetails) {
        this.approvalDetails = approvalDetails;
    }

    public String getCompensationName() {
        return compensationName;
    }

    public void setCompensationName(String compensationName) {
        this.compensationName = compensationName;
    }

    public BigDecimal getTotalNetPay() {
        return totalNetPay;
    }

    public void setTotalNetPay(BigDecimal totalNetPay) {
        this.totalNetPay = totalNetPay;
    }

    public BigDecimal getTotalTaxes() {
        return totalTaxes;
    }

    public void setTotalTaxes(BigDecimal totalTaxes) {
        this.totalTaxes = totalTaxes;
    }

    public BigDecimal getTotalBenefits() {
        return totalBenefits;
    }

    public void setTotalBenefits(BigDecimal totalBenefits) {
        this.totalBenefits = totalBenefits;
    }

    public BigDecimal getTotalDonations() {
        return totalDonations;
    }

    public void setTotalDonations(BigDecimal totalDonations) {
        this.totalDonations = totalDonations;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getTotalPayrollCost() {
        return totalPayrollCost;
    }

    public void setTotalPayrollCost(BigDecimal totalPayrollCost) {
        this.totalPayrollCost = totalPayrollCost;
    }

    public Double getBaseDays() {
        return baseDays;
    }

    public void setBaseDays(Double baseDays) {
        this.baseDays = baseDays;
    }

    public Integer getNoOfSkippedEmployees() {
        return noOfSkippedEmployees;
    }

    public void setNoOfSkippedEmployees(Integer noOfSkippedEmployees) {
        this.noOfSkippedEmployees = noOfSkippedEmployees;
    }

    public Boolean getCanEditPaydate() {
        return canEditPaydate;
    }

    public void setCanEditPaydate(Boolean canEditPaydate) {
        this.canEditPaydate = canEditPaydate;
    }

    public Boolean getCanPostPayrunTransactions() {
        return canPostPayrunTransactions;
    }

    public void setCanPostPayrunTransactions(Boolean canPostPayrunTransactions) {
        this.canPostPayrunTransactions = canPostPayrunTransactions;
    }

    public Boolean getHasDirectDepositPayments() {
        return hasDirectDepositPayments;
    }

    public void setHasDirectDepositPayments(Boolean hasDirectDepositPayments) {
        this.hasDirectDepositPayments = hasDirectDepositPayments;
    }

    public Boolean getHasNonDirectDepositPayments() {
        return hasNonDirectDepositPayments;
    }

    public void setHasNonDirectDepositPayments(Boolean hasNonDirectDepositPayments) {
        this.hasNonDirectDepositPayments = hasNonDirectDepositPayments;
    }

    public BigDecimal getTotalUnpaidDdAmount() {
        return totalUnpaidDdAmount;
    }

    public void setTotalUnpaidDdAmount(BigDecimal totalUnpaidDdAmount) {
        this.totalUnpaidDdAmount = totalUnpaidDdAmount;
    }

    public BigDecimal getTotalYetToInitiateDdAmount() {
        return totalYetToInitiateDdAmount;
    }

    public void setTotalYetToInitiateDdAmount(BigDecimal totalYetToInitiateDdAmount) {
        this.totalYetToInitiateDdAmount = totalYetToInitiateDdAmount;
    }

    public Long getTimeLeftForPayrollApprovalInMillis() {
        return timeLeftForPayrollApprovalInMillis;
    }

    public void setTimeLeftForPayrollApprovalInMillis(Long timeLeftForPayrollApprovalInMillis) {
        this.timeLeftForPayrollApprovalInMillis = timeLeftForPayrollApprovalInMillis;
    }

    public Boolean getBonusEarningExist() {
        return isBonusEarningExist;
    }

    public void setBonusEarningExist(Boolean bonusEarningExist) {
        isBonusEarningExist = bonusEarningExist;
    }

    public Boolean getPayrollFailureExist() {
        return isPayrollFailureExist;
    }

    public void setPayrollFailureExist(Boolean payrollFailureExist) {
        isPayrollFailureExist = payrollFailureExist;
    }

    public Boolean getProofMode() {
        return isProofMode;
    }

    public void setProofMode(Boolean proofMode) {
        isProofMode = proofMode;
    }

    public Boolean getProfessionalTaxOverrideAllowed() {
        return isProfessionalTaxOverrideAllowed;
    }

    public void setProfessionalTaxOverrideAllowed(Boolean professionalTaxOverrideAllowed) {
        isProfessionalTaxOverrideAllowed = professionalTaxOverrideAllowed;
    }

    public Boolean getSalaryHoldAllowed() {
        return isSalaryHoldAllowed;
    }

    public void setSalaryHoldAllowed(Boolean salaryHoldAllowed) {
        isSalaryHoldAllowed = salaryHoldAllowed;
    }

    public Boolean getClaimsAndDeclarationsLockedForAllEmployees() {
        return isClaimsAndDeclarationsLockedForAllEmployees;
    }

    public void setClaimsAndDeclarationsLockedForAllEmployees(Boolean claimsAndDeclarationsLockedForAllEmployees) {
        isClaimsAndDeclarationsLockedForAllEmployees = claimsAndDeclarationsLockedForAllEmployees;
    }

    public Boolean getPreviousPayrollPresent() {
        return isPreviousPayrollPresent;
    }

    public void setPreviousPayrollPresent(Boolean previousPayrollPresent) {
        isPreviousPayrollPresent = previousPayrollPresent;
    }

    public Boolean getEmployeePayrollDataRevised() {
        return isEmployeePayrollDataRevised;
    }

    public void setEmployeePayrollDataRevised(Boolean employeePayrollDataRevised) {
        isEmployeePayrollDataRevised = employeePayrollDataRevised;
    }

    public Boolean getPayslipSent() {
        return isPayslipSent;
    }

    public void setPayslipSent(Boolean payslipSent) {
        isPayslipSent = payslipSent;
    }

    public String getEarningJson() {
        return earningJson;
    }

    public void setEarningJson(String earningJson) {
        this.earningJson = earningJson;
    }

    public String getVariablePayEarningsListJson() {
        return variablePayEarningsListJson;
    }

    public void setVariablePayEarningsListJson(String variablePayEarningsListJson) {
        this.variablePayEarningsListJson = variablePayEarningsListJson;
    }

    public String getDeductionsJson() {
        return deductionsJson;
    }

    public void setDeductionsJson(String deductionsJson) {
        this.deductionsJson = deductionsJson;
    }

    public String getExpenseBatchesDetailsJson() {
        return expenseBatchesDetailsJson;
    }

    public void setExpenseBatchesDetailsJson(String expenseBatchesDetailsJson) {
        this.expenseBatchesDetailsJson = expenseBatchesDetailsJson;
    }

    public String getProcessingExtraJson() {
        return processingExtraJson;
    }

    public void setProcessingExtraJson(String processingExtraJson) {
        this.processingExtraJson = processingExtraJson;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public void setRejectedReason(String rejectedReason) {
        this.rejectedReason = rejectedReason;
    }

    public LocalDate getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDate approvedDate) {
        this.approvedDate = approvedDate;
    }

    public BigDecimal getTotalBonus() {
        return totalBonus;
    }

    public void setTotalBonus(BigDecimal totalBonus) {
        this.totalBonus = totalBonus;
    }

    public BigDecimal getTotalClaimDeduction() {
        return totalClaimDeduction;
    }

    public void setTotalClaimDeduction(BigDecimal totalClaimDeduction) {
        this.totalClaimDeduction = totalClaimDeduction;
    }

    public BigDecimal getTotalClaimReimbursement() {
        return totalClaimReimbursement;
    }

    public void setTotalClaimReimbursement(BigDecimal totalClaimReimbursement) {
        this.totalClaimReimbursement = totalClaimReimbursement;
    }
}