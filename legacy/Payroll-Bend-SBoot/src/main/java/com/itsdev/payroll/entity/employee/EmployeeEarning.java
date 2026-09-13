package com.itsdev.payroll.entity.employee;



import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.enumeration.employee.CalculationBasis;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_earnings")
public class EmployeeEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // link to master earning definition
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "earning_id", nullable = false)
    private Earning earning;

    private Boolean enabled;          // is_enabled in payload
    private Double amount;            // amount in payload
    private Double amountInPercentage;// amount_in_percentage in payload
    private Boolean editable;         // restrict edit for some components

    private Boolean isVariable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctc_structure_id", nullable = false)
    private CtcStructure ctcStructure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    private Double overrideAmount;   // Direct flat amount given by UI

    private String earningFrequency; // new: store earning frequency per employee earning

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_basis")
    private CalculationBasis calculationBasis;

    // getters and setters


    public CalculationBasis getCalculationBasis() {
        return calculationBasis;
    }

    public void setCalculationBasis(CalculationBasis calculationBasis) {
        this.calculationBasis = calculationBasis;
    }

    public Double getOverrideAmount() {
        return overrideAmount;
    }

    public void setOverrideAmount(Double overrideAmount) {
        this.overrideAmount = overrideAmount;
    }

    public String getEarningFrequency() {
        return earningFrequency;
    }

    public void setEarningFrequency(String earningFrequency) {
        this.earningFrequency = earningFrequency;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Earning getEarning() {
        return earning;
    }

    public void setEarning(Earning earning) {
        this.earning = earning;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getAmountInPercentage() {
        return amountInPercentage;
    }

    public void setAmountInPercentage(Double amountInPercentage) {
        this.amountInPercentage = amountInPercentage;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public Boolean getIsVariable() {
        return isVariable;
    }

    public void setIsVariable(Boolean isVariable) {
        this.isVariable = isVariable;
    }

    public CtcStructure getCtcStructure() {
        return ctcStructure;
    }

    public void setCtcStructure(CtcStructure ctcStructure) {
        this.ctcStructure = ctcStructure;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }
}

