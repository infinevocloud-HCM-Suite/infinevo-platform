package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_inv_other_income")
public class EmployeeInvOtherIncome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id")
    private EmployeeInvestmentDeclaration declaration;

    @Column(name = "type")
    private String type;

    @Column(name = "type_formatted")
    private String typeFormatted;

    @Column(name = "name")
    private String name;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "declared_amount", precision = 15, scale = 2)
    private BigDecimal declaredAmount = BigDecimal.ZERO;

    @Column(name = "can_edit_in_portal")
    private Boolean canEditInPortal = Boolean.TRUE;

    @Column(name = "name_of_lender")
    private String nameOfLender;

    @Column(name = "pan_of_lender")
    private String panOfLender;

    @Column(name = "item_id_external")
    private String itemIdExternal;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public EmployeeInvOtherIncome() {
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

    public BigDecimal getDeclaredAmount() {
        return declaredAmount;
    }

    public void setDeclaredAmount(BigDecimal declaredAmount) {
        this.declaredAmount = declaredAmount;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
