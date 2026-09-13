package com.itsdev.payroll.entity.statutorycomponents;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "slabRateConfiguration")
public class SlabRateConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String taxRateSettingsId;
    private String taxConfigurationFrequency;
    private String grossSalaryConfigurationFrequency;
    private String deductionFrequency;

    private String effectiveFrom;
    private String effectiveTo;

    private boolean isActive;
    private boolean isEditable;

    @ManyToOne
    @JoinColumn(name = "professionalTaxId")
    private ProfessionalTax professionalTax;

    @OneToMany(mappedBy = "slabRateConfiguration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlabDetail> slabDetails;

    // Getters & Setters


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaxRateSettingsId() {
        return taxRateSettingsId;
    }

    public void setTaxRateSettingsId(String taxRateSettingsId) {
        this.taxRateSettingsId = taxRateSettingsId;
    }

    public String getTaxConfigurationFrequency() {
        return taxConfigurationFrequency;
    }

    public void setTaxConfigurationFrequency(String taxConfigurationFrequency) {
        this.taxConfigurationFrequency = taxConfigurationFrequency;
    }

    public String getGrossSalaryConfigurationFrequency() {
        return grossSalaryConfigurationFrequency;
    }

    public void setGrossSalaryConfigurationFrequency(String grossSalaryConfigurationFrequency) {
        this.grossSalaryConfigurationFrequency = grossSalaryConfigurationFrequency;
    }

    public String getDeductionFrequency() {
        return deductionFrequency;
    }

    public void setDeductionFrequency(String deductionFrequency) {
        this.deductionFrequency = deductionFrequency;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(String effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isEditable() {
        return isEditable;
    }

    public void setEditable(boolean editable) {
        isEditable = editable;
    }

    public ProfessionalTax getProfessionalTax() {
        return professionalTax;
    }

    public void setProfessionalTax(ProfessionalTax professionalTax) {
        this.professionalTax = professionalTax;
    }

    public List<SlabDetail> getSlabDetails() {
        return slabDetails;
    }

    public void setSlabDetails(List<SlabDetail> slabDetails) {
        this.slabDetails = slabDetails;
    }
}
