package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;
import java.util.List;

public class LetOutPropertyDTO {

    private Long id;
    private String propertyName;
    private String address;
    private BigDecimal netIncomeLoss;
    private String netIncomeLossFormatted;
    private String itemIdExternal;

    private List<LetOutPropertyDetailDTO> propertyDetails;

    public LetOutPropertyDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getNetIncomeLoss() {
        return netIncomeLoss;
    }

    public void setNetIncomeLoss(BigDecimal netIncomeLoss) {
        this.netIncomeLoss = netIncomeLoss;
    }

    public String getNetIncomeLossFormatted() {
        return netIncomeLossFormatted;
    }

    public void setNetIncomeLossFormatted(String netIncomeLossFormatted) {
        this.netIncomeLossFormatted = netIncomeLossFormatted;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }

    public List<LetOutPropertyDetailDTO> getPropertyDetails() {
        return propertyDetails;
    }

    public void setPropertyDetails(List<LetOutPropertyDetailDTO> propertyDetails) {
        this.propertyDetails = propertyDetails;
    }
}
