package com.infinevo.payroll.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Reimbursement component entity in schema payroll (W-26.1).
 */
@Entity
@Table(
        name = "reimbursement",
        schema = "payroll",
        indexes = {
            @Index(name = "uk_reimbursement_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_reimbursement_tenant_deleted_active", columnList = "tenant_id, is_deleted, is_active")
        })
public class Reimbursement extends SalaryComponent {

    @Column(name = "reimbursement_type", nullable = false, length = 32)
    private String reimbursementType;

    @Column(name = "carry_forward_option", length = 32)
    private String carryForwardOption;

    @Column(name = "is_included_in_ctc", nullable = false)
    private boolean includedInCtc = false;

    @Column(name = "is_included_in_salary_structure", nullable = false)
    private boolean includedInSalaryStructure = false;

    @Column(name = "is_fbp_component", nullable = false)
    private boolean fbpComponent = false;

    @Column(name = "is_opt_in", nullable = false)
    private boolean optIn = false;

    protected Reimbursement() {}

    public Reimbursement(UUID tenantId, String actor) {
        super(tenantId, actor);
    }

    public String getReimbursementType() {
        return reimbursementType;
    }

    public void setReimbursementType(String reimbursementType) {
        this.reimbursementType = reimbursementType;
    }

    public String getCarryForwardOption() {
        return carryForwardOption;
    }

    public void setCarryForwardOption(String carryForwardOption) {
        this.carryForwardOption = carryForwardOption;
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

    public boolean isFbpComponent() {
        return fbpComponent;
    }

    public void setFbpComponent(boolean fbpComponent) {
        this.fbpComponent = fbpComponent;
    }

    public boolean isOptIn() {
        return optIn;
    }

    public void setOptIn(boolean optIn) {
        this.optIn = optIn;
    }
}
