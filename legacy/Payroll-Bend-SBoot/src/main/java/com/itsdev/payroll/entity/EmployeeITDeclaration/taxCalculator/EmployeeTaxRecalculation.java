//package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;
//
//import com.itsdev.payroll.entity.employee.BasicDetails;
//import com.itsdev.payroll.entity.organization.Organization;
//import jakarta.persistence.*;
//import org.hibernate.annotations.CreationTimestamp;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "employee_tax_recalculation")
//public class EmployeeTaxRecalculation {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "organization_id", nullable = false)
//    private Organization organization;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "employee_id", nullable = false)
//    private BasicDetails employee;
//
//    @Column(name = "financial_year", nullable = false)
//    private Integer financialYear;
//
//    @Column(name = "tax_regime", nullable = false)
//    private String taxRegime;
//
//    @Column(name = "gross_income")
//    private BigDecimal grossIncome;
//
//    @Column(name = "total_chapter_via")
//    private BigDecimal totalChapterVIA;
//
//    @Column(name = "taxable_income")
//    private BigDecimal taxableIncome;
//
//    @Column(name = "tax_before_rebate")
//    private BigDecimal taxBeforeRebate;
//
//    @Column(name = "tax_payable")
//    private BigDecimal taxPayable;
//
//    @Column(name = "recalculation_reason")
//    private String recalculationReason; // POI_APPROVED / MANUAL / CORRECTION
//
//    @Column(name = "is_applied")
//    private Boolean isApplied = false; // payroll applied or not
//
//    @CreationTimestamp
//    private LocalDateTime
//            createdAt;
//
//    public Long getId() {
//        return id;
//    }
//
//    public void setId(Long id) {
//        this.id = id;
//    }
//
//    public Organization getOrganization() {
//        return organization;
//    }
//
//    public void setOrganization(Organization organization) {
//        this.organization = organization;
//    }
//
//    public BasicDetails getEmployee() {
//        return employee;
//    }
//
//    public void setEmployee(BasicDetails employee) {
//        this.employee = employee;
//    }
//
//    public Integer getFinancialYear() {
//        return financialYear;
//    }
//
//    public void setFinancialYear(Integer financialYear) {
//        this.financialYear = financialYear;
//    }
//
//    public String getTaxRegime() {
//        return taxRegime;
//    }
//
//    public void setTaxRegime(String taxRegime) {
//        this.taxRegime = taxRegime;
//    }
//
//    public BigDecimal getGrossIncome() {
//        return grossIncome;
//    }
//
//    public void setGrossIncome(BigDecimal grossIncome) {
//        this.grossIncome = grossIncome;
//    }
//
//    public BigDecimal getTotalChapterVIA() {
//        return totalChapterVIA;
//    }
//
//    public void setTotalChapterVIA(BigDecimal totalChapterVIA) {
//        this.totalChapterVIA = totalChapterVIA;
//    }
//
//    public BigDecimal getTaxableIncome() {
//        return taxableIncome;
//    }
//
//    public void setTaxableIncome(BigDecimal taxableIncome) {
//        this.taxableIncome = taxableIncome;
//    }
//
//    public BigDecimal getTaxBeforeRebate() {
//        return taxBeforeRebate;
//    }
//
//    public void setTaxBeforeRebate(BigDecimal taxBeforeRebate) {
//        this.taxBeforeRebate = taxBeforeRebate;
//    }
//
//    public BigDecimal getTaxPayable() {
//        return taxPayable;
//    }
//
//    public void setTaxPayable(BigDecimal taxPayable) {
//        this.taxPayable = taxPayable;
//    }
//
//    public String getRecalculationReason() {
//        return recalculationReason;
//    }
//
//    public void setRecalculationReason(String recalculationReason) {
//        this.recalculationReason = recalculationReason;
//    }
//
//    public Boolean getApplied() {
//        return isApplied;
//    }
//
//    public void setApplied(Boolean applied) {
//        isApplied = applied;
//    }
//
//    public LocalDateTime getCreatedAt() {
//        return createdAt;
//    }
//
//    public void setCreatedAt(LocalDateTime createdAt) {
//        this.createdAt = createdAt;
//    }
//}
//
