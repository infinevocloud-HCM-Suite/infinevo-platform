package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
	    name = "old_tax_calculation",
	    uniqueConstraints = @UniqueConstraint(
	        columnNames = {"organizationId", "employeeId", "financialYear"}
	    )
	)
public class OldTaxCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= CONTEXT ================= */
    @Column(nullable = false)
    private String organizationId;

    @Column(nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private Integer financialYear;

    /* ================= INCOME ================= */
    @Column(nullable = false)
    private BigDecimal grossIncome;

    @Column(nullable = false)
    private BigDecimal incomeFromSalary;

    private BigDecimal incomeFromHouseProperty;
    private BigDecimal incomeFromOtherSources;

    /* ================= EXEMPTIONS ================= */
    private BigDecimal hraExemption;

    /* ================= DEDUCTIONS ================= */
    private BigDecimal totalChapterVIA;

    /* ================= TAX ================= */
    @Column(nullable = false)
    private BigDecimal taxableIncome;

    @Column(nullable = false)
    private BigDecimal taxBeforeRebate;

    private BigDecimal rebateAmount;

    private BigDecimal surcharge;

    private BigDecimal cess;

    @Column(nullable = false)
    private BigDecimal taxPayable;

    /* ================= MONTHLY ================= */
    private BigDecimal taxPerMonth;
    private Integer remainingMonths;

    /* ================= SECTION WISE ================= */
    @OneToMany(
            mappedBy = "oldTaxCalculation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OldTaxSectionDeduction> sectionWiseDeductions;

    /* ================= AUDIT ================= */
    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

	@Column(name = "standard_deduction")
	private BigDecimal standardDeduction;

	// getters & setters


	public BigDecimal getStandardDeduction() {
		return standardDeduction;
	}

	public void setStandardDeduction(BigDecimal standardDeduction) {
		this.standardDeduction = standardDeduction;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(String organizationId) {
		this.organizationId = organizationId;
	}

	public String getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}

	public Integer getFinancialYear() {
		return financialYear;
	}

	public void setFinancialYear(Integer financialYear) {
		this.financialYear = financialYear;
	}

	public BigDecimal getGrossIncome() {
		return grossIncome;
	}

	public void setGrossIncome(BigDecimal grossIncome) {
		this.grossIncome = grossIncome;
	}

	public BigDecimal getIncomeFromSalary() {
		return incomeFromSalary;
	}

	public void setIncomeFromSalary(BigDecimal incomeFromSalary) {
		this.incomeFromSalary = incomeFromSalary;
	}

	public BigDecimal getIncomeFromHouseProperty() {
		return incomeFromHouseProperty;
	}

	public void setIncomeFromHouseProperty(BigDecimal incomeFromHouseProperty) {
		this.incomeFromHouseProperty = incomeFromHouseProperty;
	}

	public BigDecimal getIncomeFromOtherSources() {
		return incomeFromOtherSources;
	}

	public void setIncomeFromOtherSources(BigDecimal incomeFromOtherSources) {
		this.incomeFromOtherSources = incomeFromOtherSources;
	}

	public BigDecimal getHraExemption() {
		return hraExemption;
	}

	public void setHraExemption(BigDecimal hraExemption) {
		this.hraExemption = hraExemption;
	}

	public BigDecimal getTotalChapterVIA() {
		return totalChapterVIA;
	}

	public void setTotalChapterVIA(BigDecimal totalChapterVIA) {
		this.totalChapterVIA = totalChapterVIA;
	}

	public BigDecimal getTaxableIncome() {
		return taxableIncome;
	}

	public void setTaxableIncome(BigDecimal taxableIncome) {
		this.taxableIncome = taxableIncome;
	}

	public BigDecimal getTaxBeforeRebate() {
		return taxBeforeRebate;
	}

	public void setTaxBeforeRebate(BigDecimal taxBeforeRebate) {
		this.taxBeforeRebate = taxBeforeRebate;
	}

	public BigDecimal getRebateAmount() {
		return rebateAmount;
	}

	public void setRebateAmount(BigDecimal rebateAmount) {
		this.rebateAmount = rebateAmount;
	}

	public BigDecimal getSurcharge() {
		return surcharge;
	}

	public void setSurcharge(BigDecimal surcharge) {
		this.surcharge = surcharge;
	}

	public BigDecimal getCess() {
		return cess;
	}

	public void setCess(BigDecimal cess) {
		this.cess = cess;
	}

	public BigDecimal getTaxPayable() {
		return taxPayable;
	}

	public void setTaxPayable(BigDecimal taxPayable) {
		this.taxPayable = taxPayable;
	}

	public BigDecimal getTaxPerMonth() {
		return taxPerMonth;
	}

	public void setTaxPerMonth(BigDecimal taxPerMonth) {
		this.taxPerMonth = taxPerMonth;
	}

	public Integer getRemainingMonths() {
		return remainingMonths;
	}

	public void setRemainingMonths(Integer remainingMonths) {
		this.remainingMonths = remainingMonths;
	}

	public List<OldTaxSectionDeduction> getSectionWiseDeductions() {
		return sectionWiseDeductions;
	}

	public void setSectionWiseDeductions(List<OldTaxSectionDeduction> sectionWiseDeductions) {
		this.sectionWiseDeductions = sectionWiseDeductions;
	}

	public LocalDateTime getCreatedTime() {
		return createdTime;
	}

	public void setCreatedTime(LocalDateTime createdTime) {
		this.createdTime = createdTime;
	}

	public LocalDateTime getUpdatedTime() {
		return updatedTime;
	}

	public void setUpdatedTime(LocalDateTime updatedTime) {
		this.updatedTime = updatedTime;
	}
    
    
    
}
