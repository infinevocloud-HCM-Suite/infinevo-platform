package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class Section6ADeclarationDTO {

    private Long id;
    private Long section6aItemId;
    private String category;             // "80c", "80d", ...
    private String categoryFormatted;
    private String type;                 // "lic", "ppf", ...
    private String typeFormatted;

    private BigDecimal amount;
    private String amountFormatted;

    private String itemIdExternal;

    public Section6ADeclarationDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSection6aItemId() {
        return section6aItemId;
    }

    public void setSection6aItemId(Long section6aItemId) {
        this.section6aItemId = section6aItemId;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getAmountFormatted() {
        return amountFormatted;
    }

    public void setAmountFormatted(String amountFormatted) {
        this.amountFormatted = amountFormatted;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }
}
