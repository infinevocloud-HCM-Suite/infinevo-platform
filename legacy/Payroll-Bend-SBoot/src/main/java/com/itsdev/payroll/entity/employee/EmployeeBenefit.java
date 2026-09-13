package com.itsdev.payroll.entity.employee;


import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Benefit;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_benefits") // ✅ plural, to avoid confusion with master table "benefits"
public class EmployeeBenefit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Reference to master benefit definition
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_id", nullable = false)  // FK to salarycomponents.benefits
    private Benefit benefit;

    private Boolean enabled = true;

    private Double amount;

    @Column(name = "amount_in_percentage")
    private Double amountInPercentage; // nullable: when null, treat as fixed

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

    public Benefit getBenefit() { return benefit; }
    public void setBenefit(Benefit benefit) { this.benefit = benefit; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getAmountInPercentage() { return amountInPercentage; }
    public void setAmountInPercentage(Double amountInPercentage) { this.amountInPercentage = amountInPercentage; }

    public CtcStructure getCtcStructure() { return ctcStructure; }
    public void setCtcStructure(CtcStructure ctcStructure) { this.ctcStructure = ctcStructure; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}

