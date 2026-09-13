package com.itsdev.payroll.entity.statutorycomponents;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "slabDetail")
public class SlabDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double startAmount;
    private Double endAmount;
    private Double payAmount;

    private boolean isFemaleExempted;

    @ElementCollection
    @CollectionTable(name = "slabDeductionMonths", joinColumns = @JoinColumn(name = "slabId"))
    @Column(name = "deductionMonth")
    private List<String> deductionMonths;

    // Many SlabDetails belong to ProfessionalTax
    @ManyToOne
    @JoinColumn(name = "professionalTaxId")
    private ProfessionalTax professionalTax;

    // Many SlabDetails belong to SlabRateConfiguration
    @ManyToOne
    @JoinColumn(name = "slabRateConfigId")
    private SlabRateConfiguration slabRateConfiguration;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean defaultFromMaster = true;

    
    

    // Getters & Setters

    public boolean getDefaultFromMaster() {
		return defaultFromMaster;
	}

	public void setDefaultFromMaster(boolean defaultFromMaster) {
		this.defaultFromMaster = defaultFromMaster;
	}

	public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getStartAmount() {
        return startAmount;
    }

    public void setStartAmount(Double startAmount) {
        this.startAmount = startAmount;
    }

    public Double getEndAmount() {
        return endAmount;
    }

    public void setEndAmount(Double endAmount) {
        this.endAmount = endAmount;
    }

    public Double getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Double payAmount) {
        this.payAmount = payAmount;
    }

    public boolean isFemaleExempted() {
        return isFemaleExempted;
    }

    public void setFemaleExempted(boolean femaleExempted) {
        isFemaleExempted = femaleExempted;
    }

    public List<String> getDeductionMonths() {
        return deductionMonths;
    }

    public void setDeductionMonths(List<String> deductionMonths) {
        this.deductionMonths = deductionMonths;
    }

    public ProfessionalTax getProfessionalTax() {
        return professionalTax;
    }

    public void setProfessionalTax(ProfessionalTax professionalTax) {
        this.professionalTax = professionalTax;
    }

    public SlabRateConfiguration getSlabRateConfiguration() {
        return slabRateConfiguration;
    }

    public void setSlabRateConfiguration(SlabRateConfiguration slabRateConfiguration) {
        this.slabRateConfiguration = slabRateConfiguration;
    }
}
