package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class OtherIncomeDTO {

    private Long id;
    private String type;               // "interest_on_home_loan_self_occupied", etc.
    private String typeFormatted;
    private String name;

    private BigDecimal amount;
    private String amountFormatted;

    private BigDecimal declaredAmount;
    private String declaredAmountFormatted;

    private Boolean canEditInPortal;

    private String nameOfLender;
    private String panOfLender;

    private String itemIdExternal;

    public OtherIncomeDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public BigDecimal getDeclaredAmount() {
        return declaredAmount;
    }

    public void setDeclaredAmount(BigDecimal declaredAmount) {
        this.declaredAmount = declaredAmount;
    }

    public String getDeclaredAmountFormatted() {
        return declaredAmountFormatted;
    }

    public void setDeclaredAmountFormatted(String declaredAmountFormatted) {
        this.declaredAmountFormatted = declaredAmountFormatted;
    }

    public Boolean getCanEditInPortal() {
        return canEditInPortal;
    }

    public void setCanEditInPortal(Boolean canEditInPortal) {
        this.canEditInPortal = canEditInPortal;
    }

    public String getNameOfLender() {
        return nameOfLender;
    }

    public void setNameOfLender(String nameOfLender) {
        this.nameOfLender = nameOfLender;
    }

    public String getPanOfLender() {
        return panOfLender;
    }

    public void setPanOfLender(String panOfLender) {
        this.panOfLender = panOfLender;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }
}
