package com.itsdev.payroll.dto.statutorycomponents;

import java.util.List;

public class SlabDetailDTO {
	 private Long id;
    private Double startAmount;
    private Double endAmount;
    private Double payAmount;
    private boolean isFemaleExempted;
    private List<String> deductionMonths;
    private boolean defaultFromMaster;


    // Getters & Setters

    public boolean getDefaultFromMaster() {
		return defaultFromMaster;
	}

	public void setDefaultFromMaster(boolean defaultFromMaster) {
		this.defaultFromMaster = defaultFromMaster;
	}

    // Getters & Setters
    
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

	@Override
	public String toString() {
		return "SlabDetailDTO [id=" + id + ", startAmount=" + startAmount + ", endAmount=" + endAmount + ", payAmount="
				+ payAmount + ", isFemaleExempted=" + isFemaleExempted + ", deductionMonths=" + deductionMonths + "]";
	}
    
    
}
