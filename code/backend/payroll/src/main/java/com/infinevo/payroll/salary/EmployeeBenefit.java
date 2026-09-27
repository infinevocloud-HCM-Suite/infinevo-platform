package com.infinevo.payroll.salary;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Employee benefit component entity in a CTC version (W-26.2).
 */
@Entity
@Table(
        name = "employee_benefit",
        schema = "payroll",
        indexes = {
            @Index(name = "idx_employee_benefit_tenant_structure", columnList = "tenant_id, ctc_structure_id"),
            @Index(name = "idx_employee_benefit_tenant_component", columnList = "tenant_id, component_id")
        })
public class EmployeeBenefit extends EmployeeSalaryComponent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctc_structure_id", nullable = false)
    private CtcStructure ctcStructure;

    protected EmployeeBenefit() {}

    public EmployeeBenefit(UUID tenantId, UUID componentId, CtcStructure ctcStructure, String actor) {
        super(tenantId, componentId, actor);
        this.ctcStructure = ctcStructure;
    }

    public CtcStructure getCtcStructure() {
        return ctcStructure;
    }

    public void setCtcStructure(CtcStructure ctcStructure) {
        this.ctcStructure = ctcStructure;
    }
}
