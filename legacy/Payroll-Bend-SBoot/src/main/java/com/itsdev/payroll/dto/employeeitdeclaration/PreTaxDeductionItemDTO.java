package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class PreTaxDeductionItemDTO {

    private Long id;
    private String codeString;
    private String category;
    private String categoryFormatted;
    private String type;
    private String typeFormatted;

    private BigDecimal amount;
    private String amountFormatted;

    private BigDecimal investmentAmount;
    private String investmentAmountFormatted;

    public PreTaxDeductionItemDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodeString() {
        return codeString;
    }

    public void setCodeString(String codeString) {
        this.codeString = codeString;
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

    public BigDecimal getInvestmentAmount() {
        return investmentAmount;
    }

    public void setInvestmentAmount(BigDecimal investmentAmount) {
        this.investmentAmount = investmentAmount;
    }

    public String getInvestmentAmountFormatted() {
        return investmentAmountFormatted;
    }

    public void setInvestmentAmountFormatted(String investmentAmountFormatted) {
        this.investmentAmountFormatted = investmentAmountFormatted;
    }
}
