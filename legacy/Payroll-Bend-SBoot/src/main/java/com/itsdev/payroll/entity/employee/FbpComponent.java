package com.itsdev.payroll.entity.employee;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_fbp_components")
public class FbpComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Code to uniquely identify the component (e.g., "meal_card", "fuel_allowance")
    @Column(nullable = true)
    private String componentCode;

    private Boolean enabled = true;

    private Double amount;

    @Column(name = "amount_in_percentage")
    private Double amountInPercentage; // optional, if % based

    @Column(name = "max_limit")
    private Double maxLimit; // optional, if limit applies

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

    public String getComponentCode() {
        return componentCode;
    }

    public void setComponentCode(String componentCode) {
        this.componentCode = componentCode;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getAmountInPercentage() { return amountInPercentage; }
    public void setAmountInPercentage(Double amountInPercentage) { this.amountInPercentage = amountInPercentage; }

    public Double getMaxLimit() { return maxLimit; }
    public void setMaxLimit(Double maxLimit) { this.maxLimit = maxLimit; }

    public CtcStructure getCtcStructure() { return ctcStructure; }
    public void setCtcStructure(CtcStructure ctcStructure) { this.ctcStructure = ctcStructure; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}
