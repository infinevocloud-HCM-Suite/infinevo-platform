package com.infinevo.payroll.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Employee reimbursement component entity in a CTC version (W-26.2).
 */
@Entity
@Table(
        name = "employee_reimbursement",
        schema = "payroll",
        indexes = {
            @Index(name = "idx_employee_reimbursement_tenant_structure", columnList = "tenant_id, ctc_structure_id"),
            @Index(name = "idx_employee_reimbursement_tenant_component", columnList = "tenant_id, component_id")
        })
public class EmployeeReimbursement extends EmployeeSalaryComponent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctc_structure_id", nullable = false)
    private CtcStructure ctcStructure;

    @Column(name = "carry_forward_option", length = 32)
    private String carryForwardOption;

    protected EmployeeReimbursement() {}

    public EmployeeReimbursement(UUID tenantId, UUID componentId, CtcStructure ctcStructure, String actor) {
        super(tenantId, componentId, actor);
        this.ctcStructure = ctcStructure;
    }

    public CtcStructure getCtcStructure() {
        return ctcStructure;
    }

    public void setCtcStructure(CtcStructure ctcStructure) {
        this.ctcStructure = ctcStructure;
    }

    public String getCarryForwardOption() {
        return carryForwardOption;
    }

    public void setCarryForwardOption(String carryForwardOption) {
        this.carryForwardOption = carryForwardOption;
    }
}
