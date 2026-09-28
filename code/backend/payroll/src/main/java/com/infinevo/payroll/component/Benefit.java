package com.infinevo.payroll.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Benefit component entity in schema payroll (W-26.1).
 */
@Entity
@Table(
        name = "benefit",
        schema = "payroll",
        indexes = {
            @Index(name = "uk_benefit_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_benefit_tenant_deleted_active", columnList = "tenant_id, is_deleted, is_active")
        })
public class Benefit extends SalaryComponent {

    @Column(name = "benefit_plan", length = 64)
    private String benefitPlan;

    @Column(name = "benefit_category", length = 32)
    private String benefitCategory;

    @Column(name = "is_pre_tax", nullable = false)
    private boolean preTax = false;

    @Column(name = "is_one_time", nullable = false)
    private boolean oneTime = false;

    @Column(name = "is_pro_rata", nullable = false)
    private boolean proRata = false;

    @Column(name = "is_superannuation", nullable = false)
    private boolean superannuation = false;

    @Column(name = "is_included_in_ctc", nullable = false)
    private boolean includedInCtc = false;

    @Column(name = "is_included_in_salary_structure", nullable = false)
    private boolean includedInSalaryStructure = false;

    @Column(name = "allows_employer_contribution", nullable = false)
    private boolean allowsEmployerContribution = false;

    @Column(name = "allows_employee_contribution", nullable = false)
    private boolean allowsEmployeeContribution = false;

    @Column(name = "tax_exempt_section", length = 16)
    private String taxExemptSection;

    @Column(name = "tax_exemption_sub_type", length = 32)
    private String taxExemptionSubType;

    protected Benefit() {}

    public Benefit(UUID tenantId, String actor) {
        super(tenantId, actor);
    }

    public String getBenefitPlan() {
        return benefitPlan;
    }

    public void setBenefitPlan(String benefitPlan) {
        this.benefitPlan = benefitPlan;
    }

    public String getBenefitCategory() {
        return benefitCategory;
    }

    public void setBenefitCategory(String benefitCategory) {
        this.benefitCategory = benefitCategory;
    }

    public boolean isPreTax() {
        return preTax;
    }

    public void setPreTax(boolean preTax) {
        this.preTax = preTax;
    }

    public boolean isOneTime() {
        return oneTime;
    }

    public void setOneTime(boolean oneTime) {
        this.oneTime = oneTime;
    }

    public boolean isProRata() {
        return proRata;
    }

    public void setProRata(boolean proRata) {
        this.proRata = proRata;
    }

    public boolean isSuperannuation() {
        return superannuation;
    }

    public void setSuperannuation(boolean superannuation) {
        this.superannuation = superannuation;
    }

    public boolean isIncludedInCtc() {
        return includedInCtc;
    }

    public void setIncludedInCtc(boolean includedInCtc) {
        this.includedInCtc = includedInCtc;
    }

    public boolean isIncludedInSalaryStructure() {
        return includedInSalaryStructure;
    }

    public void setIncludedInSalaryStructure(boolean includedInSalaryStructure) {
        this.includedInSalaryStructure = includedInSalaryStructure;
    }

    public boolean isAllowsEmployerContribution() {
        return allowsEmployerContribution;
    }

    public void setAllowsEmployerContribution(boolean allowsEmployerContribution) {
        this.allowsEmployerContribution = allowsEmployerContribution;
    }

    public boolean isAllowsEmployeeContribution() {
        return allowsEmployeeContribution;
    }

    public void setAllowsEmployeeContribution(boolean allowsEmployeeContribution) {
        this.allowsEmployeeContribution = allowsEmployeeContribution;
    }

    public String getTaxExemptSection() {
        return taxExemptSection;
    }

    public void setTaxExemptSection(String taxExemptSection) {
        this.taxExemptSection = taxExemptSection;
    }

    public String getTaxExemptionSubType() {
        return taxExemptionSubType;
    }

    public void setTaxExemptionSubType(String taxExemptionSubType) {
        this.taxExemptionSubType = taxExemptionSubType;
    }
}
