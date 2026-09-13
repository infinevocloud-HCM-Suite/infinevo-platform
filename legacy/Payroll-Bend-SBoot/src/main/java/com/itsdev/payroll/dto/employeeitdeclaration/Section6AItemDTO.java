package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class Section6AItemDTO {

    private Long id;

    private String category;
    private String categoryFormatted;

    private String type;
    private String typeFormatted;

    private BigDecimal maxLimit;
    private String maxLimitFormatted;

    // 🔹 UI control flags (REQUIRED)
    private Boolean is80c;
    private Boolean is80d;
    private Boolean isOtherSection;
    private Boolean isActive;

    public Section6AItemDTO() {
    }

    // getters & setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryFormatted() {
        return categoryFormatted;
    }

    public void setCategoryFormatted(String categoryFormatted) {
        this.categoryFormatted = categoryFormatted;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeFormatted() {
        return typeFormatted;
    }

    public void setTypeFormatted(String typeFormatted) {
        this.typeFormatted = typeFormatted;
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

    public Boolean getIs80c() {
        return is80c;
    }

    public void setIs80c(Boolean is80c) {
        this.is80c = is80c;
    }

    public Boolean getIs80d() {
        return is80d;
    }

    public void setIs80d(Boolean is80d) {
        this.is80d = is80d;
    }

    public Boolean getIsOtherSection() {
        return isOtherSection;
    }

    public void setIsOtherSection(Boolean isOtherSection) {
        this.isOtherSection = isOtherSection;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
