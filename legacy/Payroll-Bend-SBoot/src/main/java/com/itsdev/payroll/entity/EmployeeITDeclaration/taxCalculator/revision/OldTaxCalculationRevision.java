package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.revision;



import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "old_tax_calculation_revision",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"organization_id", "employee_id", "financial_year"}
        )
)
public class OldTaxCalculationRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= SCOPE ================= */

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "financial_year", nullable = false)
    private Integer financialYear;

    /**
     * Optional link to original OLD tax calculation
     */
    @Column(name = "base_old_tax_calculation_id")
    private Long baseOldTaxCalculationId;

    /* ================= INCOME ================= */

    @Column(name = "gross_income", precision = 15, scale = 2)
    private BigDecimal grossIncome;

    @Column(name = "income_from_salary", precision = 15, scale = 2)
    private BigDecimal incomeFromSalary;

    @Column(name = "income_from_house_property", precision = 15, scale = 2)
    private BigDecimal incomeFromHouseProperty;

    @Column(name = "income_from_other_sources", precision = 15, scale = 2)
    private BigDecimal incomeFromOtherSources;

    /* ================= EXEMPTIONS & DEDUCTIONS ================= */

    @Column(name = "hra_exemption", precision = 15, scale = 2)
    private BigDecimal hraExemption;

    @Column(name = "standard_deduction", precision = 15, scale = 2)
    private BigDecimal standardDeduction;

    @Column(name = "total_chapter_via", precision = 15, scale = 2)
    private BigDecimal totalChapterVIA;

    /* ================= TAX ================= */

    @Column(name = "taxable_income", precision = 15, scale = 2)
    private BigDecimal taxableIncome;

    @Column(name = "tax_before_rebate", precision = 15, scale = 2)
    private BigDecimal taxBeforeRebate;

    @Column(name = "rebate_amount", precision = 15, scale = 2)
    private BigDecimal rebateAmount;

    @Column(name = "surcharge", precision = 15, scale = 2)
    private BigDecimal surcharge;

    @Column(name = "cess", precision = 15, scale = 2)
    private BigDecimal cess;

    @Column(name = "tax_payable", precision = 15, scale = 2)
    private BigDecimal taxPayable;

    /* ================= REVISION META ================= */

    @Column(name = "revision_reason", length = 50)
    private String revisionReason; // e.g. SALARY_REVISION

    @Column(name = "revision_effective_from")
    private LocalDate revisionEffectiveFrom;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt = LocalDateTime.now();

    /* ================= SECTION-WISE BREAKUP ================= */

    @OneToMany(
            mappedBy = "revision",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OldTaxRevisionSectionDeduction> sectionWiseDeductions =
            new ArrayList<>();

    /* ================= GETTERS / SETTERS ================= */

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

    public Long getBaseOldTaxCalculationId() {
        return baseOldTaxCalculationId;
    }

    public void setBaseOldTaxCalculationId(Long baseOldTaxCalculationId) {
        this.baseOldTaxCalculationId = baseOldTaxCalculationId;
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

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public void setStandardDeduction(BigDecimal standardDeduction) {
        this.standardDeduction = standardDeduction;
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

    public String getRevisionReason() {
        return revisionReason;
    }

    public void setRevisionReason(String revisionReason) {
        this.revisionReason = revisionReason;
    }

    public LocalDate getRevisionEffectiveFrom() {
        return revisionEffectiveFrom;
    }

    public void setRevisionEffectiveFrom(LocalDate revisionEffectiveFrom) {
        this.revisionEffectiveFrom = revisionEffectiveFrom;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public List<OldTaxRevisionSectionDeduction> getSectionWiseDeductions() {
        return sectionWiseDeductions;
    }

    public void setSectionWiseDeductions(List<OldTaxRevisionSectionDeduction> sectionWiseDeductions) {
        this.sectionWiseDeductions = sectionWiseDeductions;
    }
}

