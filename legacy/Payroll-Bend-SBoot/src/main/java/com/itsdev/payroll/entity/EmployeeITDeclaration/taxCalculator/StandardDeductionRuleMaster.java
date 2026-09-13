package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "standardDeductionRuleMaster")
public class StandardDeductionRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: "2025-26"
    @Column(nullable = false)
    private String financialYear;

    // OLD / NEW / BOTH
    @Column(nullable = false)
    private String taxRegime;

    // Example: 75000
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // Future-proofing (if Govt changes rules)
    private String description;

    private Boolean isActive = true;

    /* ================= GETTERS / SETTERS ================= */

    public Long getId() {
        return id;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public String getDescription() {
        return description;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
