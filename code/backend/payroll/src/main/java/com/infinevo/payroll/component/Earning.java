package com.infinevo.payroll.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Earning component entity in schema payroll (W-26.1).
 */
@Entity
@Table(
        name = "earning",
        schema = "payroll",
        indexes = {
            @Index(name = "uk_earning_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_earning_tenant_deleted_active", columnList = "tenant_id, is_deleted, is_active"),
            @Index(name = "idx_earning_tenant_parent", columnList = "tenant_id, parent_earning_id")
        })
public class Earning extends SalaryComponent {

    @Column(name = "earning_type", nullable = false, length = 32)
    private String earningType;

    @Column(name = "earning_frequency", length = 16)
    private String earningFrequency;

    @Column(name = "parent_earning_id")
    private UUID parentEarningId;

    @Column(name = "is_pro_rata", nullable = false)
    private boolean proRata = false;

    @Column(name = "is_included_in_ctc", nullable = false)
    private boolean includedInCtc = false;

    @Column(name = "is_included_in_salary_structure", nullable = false)
    private boolean includedInSalaryStructure = false;

    @Column(name = "is_taxable", nullable = false)
    private boolean taxable = false;

    @Column(name = "is_variable", nullable = false)
    private boolean variable = false;

    @Column(name = "is_one_time", nullable = false)
    private boolean oneTime = false;

    @Column(name = "is_fbp_component", nullable = false)
    private boolean fbpComponent = false;

    @Column(name = "is_included_in_epf", nullable = false)
    private boolean includedInEpf = false;

    @Column(name = "epf_inclusion_type", length = 32)
    private String epfInclusionType;

    @Column(name = "is_included_in_esi", nullable = false)
    private boolean includedInEsi = false;

    @Column(name = "show_in_payslip", nullable = false)
    private boolean showInPayslip = true;

    protected Earning() {}

    public Earning(UUID tenantId, String actor) {
        super(tenantId, actor);
    }

    public String getEarningType() {
        return earningType;
    }

    public void setEarningType(String earningType) {
        this.earningType = earningType;
    }

    public String getEarningFrequency() {
        return earningFrequency;
    }

    public void setEarningFrequency(String earningFrequency) {
        this.earningFrequency = earningFrequency;
    }

    public UUID getParentEarningId() {
        return parentEarningId;
    }

    public void setParentEarningId(UUID parentEarningId) {
        this.parentEarningId = parentEarningId;
    }

    public boolean isProRata() {
        return proRata;
    }

    public void setProRata(boolean proRata) {
        this.proRata = proRata;
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

    public boolean isTaxable() {
        return taxable;
    }

    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
    }

    public boolean isVariable() {
        return variable;
    }

    public void setVariable(boolean variable) {
        this.variable = variable;
    }

    public boolean isOneTime() {
        return oneTime;
    }

    public void setOneTime(boolean oneTime) {
        this.oneTime = oneTime;
    }

    public boolean isFbpComponent() {
        return fbpComponent;
    }

    public void setFbpComponent(boolean fbpComponent) {
        this.fbpComponent = fbpComponent;
    }

    public boolean isIncludedInEpf() {
        return includedInEpf;
    }

    public void setIncludedInEpf(boolean includedInEpf) {
        this.includedInEpf = includedInEpf;
    }

    public String getEpfInclusionType() {
        return epfInclusionType;
    }

    public void setEpfInclusionType(String epfInclusionType) {
        this.epfInclusionType = epfInclusionType;
    }

    public boolean isIncludedInEsi() {
        return includedInEsi;
    }

    public void setIncludedInEsi(boolean includedInEsi) {
        this.includedInEsi = includedInEsi;
    }

    public boolean isShowInPayslip() {
        return showInPayslip;
    }

    public void setShowInPayslip(boolean showInPayslip) {
        this.showInPayslip = showInPayslip;
    }
}
