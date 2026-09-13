package com.itsdev.payroll.entity.employee;


import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ctc_epf_components")
public class CtcEpfComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ctc_structure_id")
    private CtcStructure ctcStructure;

    // ✅ Link to organization
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    private String componentCode;      // "EPF_EMPLOYER", "EDLI", "ADMIN"
    private String componentLabel;     // "EPF - Employer Contribution"
    private String percentage;         // "12", "0.50"
    private String calculationType;    // "12% of PF Wages"

    private BigDecimal monthlyAmount;
    private BigDecimal annualAmount;

    // getters/setters


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CtcStructure getCtcStructure() {
        return ctcStructure;
    }

    public void setCtcStructure(CtcStructure ctcStructure) {
        this.ctcStructure = ctcStructure;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public void setComponentCode(String componentCode) {
        this.componentCode = componentCode;
    }

    public String getComponentLabel() {
        return componentLabel;
    }

    public void setComponentLabel(String componentLabel) {
        this.componentLabel = componentLabel;
    }

    public String getPercentage() {
        return percentage;
    }

    public void setPercentage(String percentage) {
        this.percentage = percentage;
    }

    public String getCalculationType() {
        return calculationType;
    }

    public void setCalculationType(String calculationType) {
        this.calculationType = calculationType;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public void setMonthlyAmount(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }

    public void setAnnualAmount(BigDecimal annualAmount) {
        this.annualAmount = annualAmount;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }
}

