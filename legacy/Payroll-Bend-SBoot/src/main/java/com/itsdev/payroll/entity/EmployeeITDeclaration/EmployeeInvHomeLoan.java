package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "employee_inv_home_loan")
public class EmployeeInvHomeLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ========================
       Parent Declaration
       ======================== */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id", nullable = false)
    private EmployeeInvestmentDeclaration declaration;

    /* ========================
       Loan Details
       ======================== */
    @Column(name = "principal_paid", precision = 15, scale = 2)
    private BigDecimal principalPaid;

    @Column(name = "interest_paid", precision = 15, scale = 2)
    private BigDecimal interestPaid;

    @Column(name = "lender_name")
    private String lenderName;

    @Column(name = "lender_pan")
    private String lenderPan;

    @Column(name = "item_id_external")
    private String itemIdExternal;

    /* ========================
       Getters & Setters
       ======================== */

    public Long getId() {
        return id;
    }

    public EmployeeInvestmentDeclaration getDeclaration() {
        return declaration;
    }

    public void setDeclaration(EmployeeInvestmentDeclaration declaration) {
        this.declaration = declaration;
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