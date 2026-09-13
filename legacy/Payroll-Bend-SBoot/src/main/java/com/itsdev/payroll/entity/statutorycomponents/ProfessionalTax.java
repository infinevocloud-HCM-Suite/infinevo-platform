package com.itsdev.payroll.entity.statutorycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "professionalTax")
public class ProfessionalTax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String taxId;

    private String state;
    private String stateCode;
    private String locationName;
    private String registrationNumber;
    private String registrationDate;

    private String taxConfigurationFrequency;
    private String grossSalaryConfigurationFrequency;
    private String deductionFrequency;

    private String effectiveFrom;

    private boolean isProfessionalTaxSupported;
    private boolean isReadOnly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // Slab details directly attached
    @OneToMany(mappedBy = "professionalTax", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlabDetail> slabDetails;

    // Historical tax slab rate configurations
    @OneToMany(mappedBy = "professionalTax", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlabRateConfiguration> slabRateConfigurations;

    // Getters & Setters


    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
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

    public boolean isProfessionalTaxSupported() {
        return isProfessionalTaxSupported;
    }

    public void setProfessionalTaxSupported(boolean professionalTaxSupported) {
        isProfessionalTaxSupported = professionalTaxSupported;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public List<SlabDetail> getSlabDetails() {
        return slabDetails;
    }

    public void setSlabDetails(List<SlabDetail> slabDetails) {
        this.slabDetails = slabDetails;
    }

    public List<SlabRateConfiguration> getSlabRateConfigurations() {
        return slabRateConfigurations;
    }

    public void setSlabRateConfigurations(List<SlabRateConfiguration> slabRateConfigurations) {
        this.slabRateConfigurations = slabRateConfigurations;
    }
}
