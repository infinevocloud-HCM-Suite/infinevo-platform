package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;
import java.util.List;

public class Section6ASummaryDTO {

    private BigDecimal maxLimit;
    private String maxLimitFormatted;
    private List<String> category;   // e.g. ["80c", "80ccc", "80ccd1"]

    public Section6ASummaryDTO() {
    }

    public BigDecimal getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(BigDecimal maxLimit) {
        this.maxLimit = maxLimit;
    }

    public String getMaxLimitFormatted() {
        return maxLimitFormatted;
    }

    public void setMaxLimitFormatted(String maxLimitFormatted) {
        this.maxLimitFormatted = maxLimitFormatted;
    }

    public List<String> getCategory() {
        return category;
    }

    public void setCategory(List<String> category) {
        this.category = category;
    }
}
