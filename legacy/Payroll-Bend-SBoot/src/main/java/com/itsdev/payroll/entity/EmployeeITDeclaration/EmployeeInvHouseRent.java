package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_inv_house_rent")
public class EmployeeInvHouseRent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id")
    private EmployeeInvestmentDeclaration declaration;

    @Column(name = "from_month", length = 7)
    private String fromMonth;

    @Column(name = "to_month", length = 7)
    private String toMonth;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "landlord_name")
    private String landlordName;

    @Column(name = "landlord_pan")
    private String landlordPan;

    @Column(name = "is_metro")
    private Boolean isMetro = Boolean.FALSE;

    @Column(name = "amount_per_month", precision = 15, scale = 2)
    private BigDecimal amountPerMonth = BigDecimal.ZERO;

    @Column(name = "currency")
    private String currency = "INR";

    @Column(name = "item_id_external")
    private String itemIdExternal;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public EmployeeInvHouseRent() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EmployeeInvestmentDeclaration getDeclaration() {
        return declaration;
    }

    public void setDeclaration(EmployeeInvestmentDeclaration declaration) {
        this.declaration = declaration;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
