package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class EmployeeHomeLoanDTO {

    private Long id;

    private BigDecimal principalPaid;
    private BigDecimal interestPaid;

    private String lenderName;
    private String lenderPan;

    private String itemIdExternal;

    /* ========================
       Getters & Setters
       ======================== */

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
    this.id = id;
}

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public void setPrincipalPaid(BigDecimal principalPaid) {
        this.principalPaid = principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public void setInterestPaid(BigDecimal interestPaid) {
        this.interestPaid = interestPaid;
    }

    public String getLenderName() {
        return lenderName;
    }

    public void setLenderName(String lenderName) {
        this.lenderName = lenderName;
    }

    public String getLenderPan() {
        return lenderPan;
    }

    public void setLenderPan(String lenderPan) {
        this.lenderPan = lenderPan;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }
}
