package com.itsdev.payroll.entity.employee;


import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_reimbursements")
public class EmployeeReimbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Reference to master reimbursement definition
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reimbursement_id", nullable = false) // FK to salarycomponents.reimbursements
    private Reimbursement reimbursement;

    private Boolean enabled = true;

    private Double amount;

    // Can override master configuration if needed
    private String carryForwardOption;

    // ✅ Link to employee’s CTC structure
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctc_structure_id", nullable = false)
    private CtcStructure ctcStructure;

    // ✅ Link to organization
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Reimbursement getReimbursement() { return reimbursement; }
    public void setReimbursement(Reimbursement reimbursement) { this.reimbursement = reimbursement; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCarryForwardOption() { return carryForwardOption; }
    public void setCarryForwardOption(String carryForwardOption) { this.carryForwardOption = carryForwardOption; }

    public CtcStructure getCtcStructure() { return ctcStructure; }
    public void setCtcStructure(CtcStructure ctcStructure) { this.ctcStructure = ctcStructure; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}

