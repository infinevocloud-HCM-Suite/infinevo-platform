package com.itsdev.payroll.dto.payruns;


public class PayrollSummaryDTO {

    private Double grossPay;
    private Double grossEarnings;
    private Double totalDeductions;
    private Double netPay;
    private String formattedPayType;
    private String payMonth;

    // NEW
    private Double professionalTax; // or BigDecimal professionalTax
    // NEW: bonus fields
    private Double bonus;
    private Boolean bonusEarningExistForEmployee;

    // getters + setters
    public Double getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(Double professionalTax) { this.professionalTax = professionalTax; }

    public Double getBonus() { return bonus; }
    public void setBonus(Double bonus) { this.bonus = bonus; }

    public Boolean getBonusEarningExistForEmployee() { return bonusEarningExistForEmployee; }
    public void setBonusEarningExistForEmployee(Boolean bonusEarningExistForEmployee) { this.bonusEarningExistForEmployee = bonusEarningExistForEmployee; }


    // --- Getters and Setters ---
    public Double getGrossPay() { return grossPay; }
    public void setGrossPay(Double grossPay) { this.grossPay = grossPay; }

    public Double getGrossEarnings() { return grossEarnings; }
    public void setGrossEarnings(Double grossEarnings) { this.grossEarnings = grossEarnings; }

    public Double getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(Double totalDeductions) { this.totalDeductions = totalDeductions; }

    public Double getNetPay() { return netPay; }
    public void setNetPay(Double netPay) { this.netPay = netPay; }

    public String getFormattedPayType() { return formattedPayType; }
    public void setFormattedPayType(String formattedPayType) { this.formattedPayType = formattedPayType; }

    public String getPayMonth() { return payMonth; }
    public void setPayMonth(String payMonth) { this.payMonth = payMonth; }
}

