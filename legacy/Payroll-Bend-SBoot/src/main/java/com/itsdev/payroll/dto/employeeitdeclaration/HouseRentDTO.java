package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class HouseRentDTO {

    private Long id;
    private String fromMonth;
    private String toMonth;
    private String address;
    private String landlordName;
    private String landlordPan;
    private Boolean isMetro;
    private BigDecimal amountPerMonth;
    private String amountPerMonthFormatted;
    private String currency;
    private String itemIdExternal;

    public HouseRentDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFromMonth() {
        return fromMonth;
    }

    public void setFromMonth(String fromMonth) {
        this.fromMonth = fromMonth;
    }

    public String getToMonth() {
        return toMonth;
    }

    public void setToMonth(String toMonth) {
        this.toMonth = toMonth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLandlordName() {
        return landlordName;
    }

    public void setLandlordName(String landlordName) {
        this.landlordName = landlordName;
    }

    public String getLandlordPan() {
        return landlordPan;
    }

    public void setLandlordPan(String landlordPan) {
        this.landlordPan = landlordPan;
    }

    public Boolean getIsMetro() {
        return isMetro;
    }

    public void setIsMetro(Boolean metro) {
        isMetro = metro;
    }

    public BigDecimal getAmountPerMonth() {
        return amountPerMonth;
    }

    public void setAmountPerMonth(BigDecimal amountPerMonth) {
        this.amountPerMonth = amountPerMonth;
    }

    public String getAmountPerMonthFormatted() {
        return amountPerMonthFormatted;
    }

    public void setAmountPerMonthFormatted(String amountPerMonthFormatted) {
        this.amountPerMonthFormatted = amountPerMonthFormatted;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }
}
