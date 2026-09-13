package com.itsdev.payroll.entity.employee;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_variable_earnings")
public class VariableEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A unique code to identify variable earning type (e.g., "bonus", "performance_incentive")
    @Column(nullable = false)
    private String variableCode;

    private Boolean enabled = true;

    private Double amount;  // actual value

    @Column(name = "amount_in_percentage")
    private Double amountInPercentage;  // % of CTC (if applicable)

    private Boolean editable = true; // if employee/admin can change

    // --- Relationships ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctc_structure_id", nullable = false)
    private CtcStructure ctcStructure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVariableCode() { return variableCode; }
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getAmountInPercentage() { return amountInPercentage; }
    public void setAmountInPercentage(Double amountInPercentage) { this.amountInPercentage = amountInPercentage; }

    public Boolean getEditable() { return editable; }
    public void setEditable(Boolean editable) { this.editable = editable; }

    public CtcStructure getCtcStructure() { return ctcStructure; }
    public void setCtcStructure(CtcStructure ctcStructure) { this.ctcStructure = ctcStructure; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}
