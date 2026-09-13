package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;

@Entity
@Table(name = "hraRuleMaster")
public class HraRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: "2025-26"
    private String financialYear;

    // OLD only (HRA not allowed in New Regime)
    private String taxRegime;

    // 50 for Metro cities
    private Integer metroPercentageOfBasic;

    // 40 for Non-metro cities
    private Integer nonMetroPercentageOfBasic;

    // Rent paid minus this % of basic (10%)
    private Integer rentMinusBasicPercentage;

    // Annual rent threshold above which PAN is mandatory (100000)
    private Integer panMandatoryThreshold;

    // Whether month-wise calculation is required
    private Boolean isMonthWiseCalculation;

    // Rule active or not
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

    public Integer getMetroPercentageOfBasic() {
        return metroPercentageOfBasic;
    }

    public void setMetroPercentageOfBasic(Integer metroPercentageOfBasic) {
        this.metroPercentageOfBasic = metroPercentageOfBasic;
    }

    public Integer getNonMetroPercentageOfBasic() {
        return nonMetroPercentageOfBasic;
    }

    public void setNonMetroPercentageOfBasic(Integer nonMetroPercentageOfBasic) {
        this.nonMetroPercentageOfBasic = nonMetroPercentageOfBasic;
    }

    public Integer getRentMinusBasicPercentage() {
        return rentMinusBasicPercentage;
    }

    public void setRentMinusBasicPercentage(Integer rentMinusBasicPercentage) {
        this.rentMinusBasicPercentage = rentMinusBasicPercentage;
    }

    public Integer getPanMandatoryThreshold() {
        return panMandatoryThreshold;
    }

    public void setPanMandatoryThreshold(Integer panMandatoryThreshold) {
        this.panMandatoryThreshold = panMandatoryThreshold;
    }

    public Boolean getMonthWiseCalculation() {
        return isMonthWiseCalculation;
    }

    public void setMonthWiseCalculation(Boolean monthWiseCalculation) {
        isMonthWiseCalculation = monthWiseCalculation;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }
}

