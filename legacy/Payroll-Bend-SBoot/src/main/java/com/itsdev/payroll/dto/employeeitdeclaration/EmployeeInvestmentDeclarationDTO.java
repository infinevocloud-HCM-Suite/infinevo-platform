package com.itsdev.payroll.dto.employeeitdeclaration;

import java.util.List;

public class EmployeeInvestmentDeclarationDTO {

    private Integer fiscalYear;

    private String declarationTaxYearStart;
    private String declarationTaxYearEnd;
    private String currentTaxYearStart;
    private String currentTaxYearEnd;

    private Boolean canAllowEdit;
    private String taxRegime;
    private String taxRegimeFormatted;
    private Boolean isMultipleTaxRegimesApplicable;
    private Boolean isLenderpanMandatory;
    private Boolean canChangeTaxRegime;
    private Boolean isEmployeeRehiredOnSameTaxyear;

    private Boolean isStayingInRentedHouse;
    private Boolean isRepayingSelfOccupiedLoan;
    private Boolean hasLetOutProperty;
    private String status;
    private String statusFormatted;
    private String messageTypes;
    private Integer taxPlanCount;

    private List<HouseRentDTO> houseRentDeclarations;
    private List<Section6ADeclarationDTO> section6aDeclarations;
    private List<PrevEmploymentDTO> previousEmploymentDeclarations;
    private List<OtherIncomeDTO> otherIncomesDeclarations;
    private List<LetOutPropertyDTO> letOutPropertyDeclarations;
    private List<Section6AItemDTO> section6aItems;

    private Section6ASummaryDTO section6a80cDetails;
    private Section6ASummaryDTO section6a80dDetails;

    private List<PreTaxDeductionItemDTO> section6aPreTaxDeductionsItems;
    private List<String> itExcludeItemsInPortal;

    private List<TaxSummaryDTO> taxSummaries;

    private List<EmployeeHomeLoanDTO> homeLoanDeclarations;

    // -------- ORG DERIVED (READ ONLY) --------
private String lastDateForItDeclaration;
private Boolean canChangeTaxRegimeIt;


public String getLastDateForItDeclaration() {
    return lastDateForItDeclaration;
}

public void setLastDateForItDeclaration(String lastDateForItDeclaration) {
    this.lastDateForItDeclaration = lastDateForItDeclaration;
}

public Boolean getCanChangeTaxRegimeIt() {
    return canChangeTaxRegimeIt;
}

public void setCanChangeTaxRegimeIt(Boolean canChangeTaxRegimeIt) {
    this.canChangeTaxRegimeIt = canChangeTaxRegimeIt;
}


    public EmployeeInvestmentDeclarationDTO() {
    }

     public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public String getDeclarationTaxYearStart() {
        return declarationTaxYearStart;
    }

    public void setDeclarationTaxYearStart(String declarationTaxYearStart) {
        this.declarationTaxYearStart = declarationTaxYearStart;
    }

    public String getDeclarationTaxYearEnd() {
        return declarationTaxYearEnd;
    }

    public void setDeclarationTaxYearEnd(String declarationTaxYearEnd) {
        this.declarationTaxYearEnd = declarationTaxYearEnd;
    }

    public String getCurrentTaxYearStart() {
        return currentTaxYearStart;
    }

    public void setCurrentTaxYearStart(String currentTaxYearStart) {
        this.currentTaxYearStart = currentTaxYearStart;
    }

    public String getCurrentTaxYearEnd() {
        return currentTaxYearEnd;
    }

    public void setCurrentTaxYearEnd(String currentTaxYearEnd) {
        this.currentTaxYearEnd = currentTaxYearEnd;
    }

    public Boolean getCanAllowEdit() {
        return canAllowEdit;
    }

    public void setCanAllowEdit(Boolean canAllowEdit) {
        this.canAllowEdit = canAllowEdit;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public String getTaxRegimeFormatted() {
        return taxRegimeFormatted;
    }

    public void setTaxRegimeFormatted(String taxRegimeFormatted) {
        this.taxRegimeFormatted = taxRegimeFormatted;
    }

    public Boolean getIsMultipleTaxRegimesApplicable() {
        return isMultipleTaxRegimesApplicable;
    }

    public void setIsMultipleTaxRegimesApplicable(Boolean multipleTaxRegimesApplicable) {
        isMultipleTaxRegimesApplicable = multipleTaxRegimesApplicable;
    }

    public Boolean getIsLenderpanMandatory() {
        return isLenderpanMandatory;
    }

    public void setIsLenderpanMandatory(Boolean lenderpanMandatory) {
        isLenderpanMandatory = lenderpanMandatory;
    }

    public Boolean getCanChangeTaxRegime() {
        return canChangeTaxRegime;
    }

    public void setCanChangeTaxRegime(Boolean canChangeTaxRegime) {
        this.canChangeTaxRegime = canChangeTaxRegime;
    }

    public Boolean getIsEmployeeRehiredOnSameTaxyear() {
        return isEmployeeRehiredOnSameTaxyear;
    }

    public void setIsEmployeeRehiredOnSameTaxyear(Boolean employeeRehiredOnSameTaxyear) {
        isEmployeeRehiredOnSameTaxyear = employeeRehiredOnSameTaxyear;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusFormatted() {
        return statusFormatted;
    }

    public void setStatusFormatted(String statusFormatted) {
        this.statusFormatted = statusFormatted;
    }

    public String getMessageTypes() {
        return messageTypes;
    }

    public void setMessageTypes(String messageTypes) {
        this.messageTypes = messageTypes;
    }

    public Integer getTaxPlanCount() {
        return taxPlanCount;
    }

    public void setTaxPlanCount(Integer taxPlanCount) {
        this.taxPlanCount = taxPlanCount;
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

    public List<Section6AItemDTO> getSection6aItems() {
        return section6aItems;
    }

    public void setSection6aItems(List<Section6AItemDTO> section6aItems) {
        this.section6aItems = section6aItems;
    }

    public Section6ASummaryDTO getSection6a80cDetails() {
        return section6a80cDetails;
    }

    public void setSection6a80cDetails(Section6ASummaryDTO section6a80cDetails) {
        this.section6a80cDetails = section6a80cDetails;
    }

    public Section6ASummaryDTO getSection6a80dDetails() {
        return section6a80dDetails;
    }

    public void setSection6a80dDetails(Section6ASummaryDTO section6a80dDetails) {
        this.section6a80dDetails = section6a80dDetails;
    }

    public List<PreTaxDeductionItemDTO> getSection6aPreTaxDeductionsItems() {
        return section6aPreTaxDeductionsItems;
    }

    public void setSection6aPreTaxDeductionsItems(List<PreTaxDeductionItemDTO> section6aPreTaxDeductionsItems) {
        this.section6aPreTaxDeductionsItems = section6aPreTaxDeductionsItems;
    }

    public List<String> getItExcludeItemsInPortal() {
        return itExcludeItemsInPortal;
    }

    public void setItExcludeItemsInPortal(List<String> itExcludeItemsInPortal) {
        this.itExcludeItemsInPortal = itExcludeItemsInPortal;
    }

    public List<TaxSummaryDTO> getTaxSummaries() {
        return taxSummaries;
    }

    public void setTaxSummaries(List<TaxSummaryDTO> taxSummaries) {
        this.taxSummaries = taxSummaries;
    }

    public List<EmployeeHomeLoanDTO> getHomeLoanDeclarations() {
    return homeLoanDeclarations;
}

public void setHomeLoanDeclarations(List<EmployeeHomeLoanDTO> homeLoanDeclarations) {
    this.homeLoanDeclarations = homeLoanDeclarations;
}

}
