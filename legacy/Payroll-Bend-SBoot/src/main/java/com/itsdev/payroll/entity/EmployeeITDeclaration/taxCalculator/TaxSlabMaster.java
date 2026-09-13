package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;

@Entity
@Table(name = "taxSlabMaster")
public class TaxSlabMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: "2025-26"
    private String financialYear;

    // OLD or NEW
    private String taxRegime;

    /*
     * JSON example:
     * [
     *   { "from": 0, "to": 250000, "rate": 0 },
     *   { "from": 250001, "to": 500000, "rate": 5 },
     *   { "from": 500001, "to": 1000000, "rate": 20 },
     *   { "from": 1000001, "to": null, "rate": 30 }
     * ]
     */
    @Column(columnDefinition = "json")
    private String slabJson;

    private Boolean isActive = true;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public String getSlabJson() {
        return slabJson;
    }

    public void setSlabJson(String slabJson) {
        this.slabJson = slabJson;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }
}

