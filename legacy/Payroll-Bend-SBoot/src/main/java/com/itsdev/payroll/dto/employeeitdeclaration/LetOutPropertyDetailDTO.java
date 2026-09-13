package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class LetOutPropertyDetailDTO {

    private Long id;
    private String type;              // e.g. interest, municipal_tax, etc.
    private BigDecimal amount;
    private String amountFormatted;
    private String nameOfLender;
    private String panOfLender;

    public LetOutPropertyDetailDTO() {
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
}
