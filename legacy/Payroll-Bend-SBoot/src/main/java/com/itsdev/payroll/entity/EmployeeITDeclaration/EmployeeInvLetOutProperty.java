package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee_inv_let_out_property")
public class EmployeeInvLetOutProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id")
    private EmployeeInvestmentDeclaration declaration;

    @Column(name = "property_name")
    private String propertyName;

    @Column(name = "address")
    private String address;

    @Column(name = "net_income_loss", precision = 15, scale = 2)
    private BigDecimal netIncomeLoss = BigDecimal.ZERO;

    @Column(name = "item_id_external")
    private String itemIdExternal;

    @OneToMany(mappedBy = "letOutProperty", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvLetOutPropertyDetail> propertyDetails = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public EmployeeInvLetOutProperty() {
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

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }

    public List<EmployeeInvLetOutPropertyDetail> getPropertyDetails() {
        return propertyDetails;
    }

    public void setPropertyDetails(List<EmployeeInvLetOutPropertyDetail> propertyDetails) {
        this.propertyDetails = propertyDetails;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
