//package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;
//
//
//import com.itsdev.payroll.entity.employee.BasicDetails;
//import com.itsdev.payroll.entity.organization.Organization;
//import jakarta.persistence.*;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "employee_tax_calculation_result")
//public class EmployeeTaxCalculationResult {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    /* ========================
//       Scope
//       ======================== */
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "organization_id", nullable = false)
//    private Organization organization;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "employee_id", nullable = false)
//    private BasicDetails employee;
//
//    @Column(nullable = false)
//    private String taxRegime;   // OLD / NEW
//
//    @Column(nullable = false)
//    private String financialYear; // 2025-26
//
//    /* ========================
//       Income
//       ======================== */
//    private BigDecimal grossTotalIncome;
//    private BigDecimal taxableIncome;
//
//    /* ========================
//       Exemptions & Deductions
//       ======================== */
//    private BigDecimal hraExemption;
//    private BigDecimal chapterVIADeduction;
//
//    /* ========================
//       Tax Calculation
//       ======================== */
//    private BigDecimal taxBeforeRebate;
//    private BigDecimal rebate87A;
//    private BigDecimal surcharge;
//    private BigDecimal cess;
//    private BigDecimal finalTaxPayable;
//
//    /* ========================
//       Audit
//       ======================== */
//    private LocalDateTime calculatedAt;
//
//    /* ========================
//       Getters / Setters
//       ======================== */
//    // generate via IDE
//
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
//    public String getTaxRegime() {
//        return taxRegime;
//    }
//
//    public void setTaxRegime(String taxRegime) {
//        this.taxRegime = taxRegime;
//    }
//
//    public String getFinancialYear() {
//        return financialYear;
//    }
//
//    public void setFinancialYear(String financialYear) {
//        this.financialYear = financialYear;
//    }
//
//    public BigDecimal getGrossTotalIncome() {
//        return grossTotalIncome;
//    }
//
//    public void setGrossTotalIncome(BigDecimal grossTotalIncome) {
//        this.grossTotalIncome = grossTotalIncome;
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
//    public BigDecimal getHraExemption() {
//        return hraExemption;
//    }
//
//    public void setHraExemption(BigDecimal hraExemption) {
//        this.hraExemption = hraExemption;
//    }
//
//    public BigDecimal getChapterVIADeduction() {
//        return chapterVIADeduction;
//    }
//
//    public void setChapterVIADeduction(BigDecimal chapterVIADeduction) {
//        this.chapterVIADeduction = chapterVIADeduction;
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
//    public BigDecimal getRebate87A() {
//        return rebate87A;
//    }
//
//    public void setRebate87A(BigDecimal rebate87A) {
//        this.rebate87A = rebate87A;
//    }
//
//    public BigDecimal getSurcharge() {
//        return surcharge;
//    }
//
//    public void setSurcharge(BigDecimal surcharge) {
//        this.surcharge = surcharge;
//    }
//
//    public BigDecimal getCess() {
//        return cess;
//    }
//
//    public void setCess(BigDecimal cess) {
//        this.cess = cess;
//    }
//
//    public BigDecimal getFinalTaxPayable() {
//        return finalTaxPayable;
//    }
//
//    public void setFinalTaxPayable(BigDecimal finalTaxPayable) {
//        this.finalTaxPayable = finalTaxPayable;
//    }
//
//    public LocalDateTime getCalculatedAt() {
//        return calculatedAt;
//    }
//
//    public void setCalculatedAt(LocalDateTime calculatedAt) {
//        this.calculatedAt = calculatedAt;
//    }
//}
//
