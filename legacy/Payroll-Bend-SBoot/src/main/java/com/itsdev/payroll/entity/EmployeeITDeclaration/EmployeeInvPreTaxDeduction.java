package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_inv_pre_tax_deduction")
public class EmployeeInvPreTaxDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id")
    private EmployeeInvestmentDeclaration declaration;

    @Column(name = "code_string")
    private String codeString;

    @Column(name = "category")
    private String category;

    @Column(name = "category_formatted")
    private String categoryFormatted;

    @Column(name = "type")
    private String type;

    @Column(name = "type_formatted")
    private String typeFormatted;

    @Column(name = "amount", precision = 15, scale = 3)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "amount_formatted")
    private String amountFormatted;

    @Column(name = "investment_amount", precision = 15, scale = 3)
    private BigDecimal investmentAmount = BigDecimal.ZERO;

    @Column(name = "investment_amount_formatted")
    private String investmentAmountFormatted;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public EmployeeInvPreTaxDeduction() {
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
