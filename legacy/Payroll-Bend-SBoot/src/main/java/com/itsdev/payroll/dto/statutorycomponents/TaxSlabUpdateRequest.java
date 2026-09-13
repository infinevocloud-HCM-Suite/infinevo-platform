package com.itsdev.payroll.dto.statutorycomponents;

import java.util.List;

public class TaxSlabUpdateRequest {

    private String effectiveFrom;

    // Only slab details you want to update
    private List<SlabDetailDTO> slabDetails;

	public String getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(String effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public List<SlabDetailDTO> getSlabDetails() {
		return slabDetails;
	}

	public void setSlabDetails(List<SlabDetailDTO> slabDetails) {
		this.slabDetails = slabDetails;
	}

	@Override
	public String toString() {
		return "TaxSlabUpdateRequest [effectiveFrom=" + effectiveFrom + ", slabDetails=" + slabDetails + "]";
	}


    
    
    
    
}
