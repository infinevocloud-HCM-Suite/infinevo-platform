package com.itsdev.payroll.dto.employeeitdeclaration;

import java.util.List;

public class EmployeeInvestmentDeclarationRequestDTO {

    /* =========================
       HEADER / USER CHOICES
       ========================= */

    private String taxRegime; // OLD / NEW

    private Boolean isStayingInRentedHouse;
    private Boolean isRepayingSelfOccupiedLoan;
    private Boolean hasLetOutProperty;


    /* =========================
       USER ENTERED DECLARATIONS
       ========================= */

    private List<HouseRentDTO> houseRentDeclarations;

    private List<Section6ADeclarationDTO> section6aDeclarations;

    private List<PrevEmploymentDTO> previousEmploymentDeclarations;

    private List<OtherIncomeDTO> otherIncomesDeclarations;

    private List<LetOutPropertyDTO> letOutPropertyDeclarations;


    /* =========================
       GETTERS & SETTERS
       ========================= */

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public Boolean getIsStayingInRentedHouse() {
        return isStayingInRentedHouse;
    }

    public void setIsStayingInRentedHouse(Boolean stayingInRentedHouse) {
        isStayingInRentedHouse = stayingInRentedHouse;
    }

    public Boolean getIsRepayingSelfOccupiedLoan() {
        return isRepayingSelfOccupiedLoan;
    }

    public void setIsRepayingSelfOccupiedLoan(Boolean repayingSelfOccupiedLoan) {
        isRepayingSelfOccupiedLoan = repayingSelfOccupiedLoan;
    }

    public Boolean getHasLetOutProperty() {
        return hasLetOutProperty;
    }

    public void setHasLetOutProperty(Boolean hasLetOutProperty) {
        this.hasLetOutProperty = hasLetOutProperty;
    }

    public List<HouseRentDTO> getHouseRentDeclarations() {
        return houseRentDeclarations;
    }

    public void setHouseRentDeclarations(List<HouseRentDTO> houseRentDeclarations) {
        this.houseRentDeclarations = houseRentDeclarations;
    }

    public List<Section6ADeclarationDTO> getSection6aDeclarations() {
        return section6aDeclarations;
    }

    public void setSection6aDeclarations(List<Section6ADeclarationDTO> section6aDeclarations) {
        this.section6aDeclarations = section6aDeclarations;
    }

    public List<PrevEmploymentDTO> getPreviousEmploymentDeclarations() {
        return previousEmploymentDeclarations;
    }

    public void setPreviousEmploymentDeclarations(List<PrevEmploymentDTO> previousEmploymentDeclarations) {
        this.previousEmploymentDeclarations = previousEmploymentDeclarations;
    }

    public List<OtherIncomeDTO> getOtherIncomesDeclarations() {
        return otherIncomesDeclarations;
    }

    public void setOtherIncomesDeclarations(List<OtherIncomeDTO> otherIncomesDeclarations) {
        this.otherIncomesDeclarations = otherIncomesDeclarations;
    }

    public List<LetOutPropertyDTO> getLetOutPropertyDeclarations() {
        return letOutPropertyDeclarations;
    }

    public void setLetOutPropertyDeclarations(List<LetOutPropertyDTO> letOutPropertyDeclarations) {
        this.letOutPropertyDeclarations = letOutPropertyDeclarations;
    }

    private List<EmployeeHomeLoanDTO> homeLoanDeclarations;

public List<EmployeeHomeLoanDTO> getHomeLoanDeclarations() {
    return homeLoanDeclarations;
}

public void setHomeLoanDeclarations(List<EmployeeHomeLoanDTO> homeLoanDeclarations) {
    this.homeLoanDeclarations = homeLoanDeclarations;
}


}
