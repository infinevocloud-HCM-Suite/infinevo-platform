package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.math.BigDecimal;

public class POIItemUpdateRequest {
    private BigDecimal actualAmount;
    private String investmentType;
    private Long section6aItemId;

    // Default constructor
    public POIItemUpdateRequest() {
    }

    // Getters and Setters
    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public String getInvestmentType() {
        return investmentType;
    }

    public void setInvestmentType(String investmentType) {
        this.investmentType = investmentType;
    }

    public Long getSection6aItemId() {
        return section6aItemId;
    }

    public void setSection6aItemId(Long section6aItemId) {
        this.section6aItemId = section6aItemId;
    }
}