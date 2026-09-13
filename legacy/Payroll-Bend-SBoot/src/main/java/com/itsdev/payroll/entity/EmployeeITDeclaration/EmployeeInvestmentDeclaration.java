package com.itsdev.payroll.entity.EmployeeITDeclaration;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee_investment_declaration")
public class EmployeeInvestmentDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Organization (FK -> organization.organization_id i.e. Organization.id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // Employee (FK -> employee.employee_id i.e. BasicDetails.id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private BasicDetails employee;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "declaration_tax_year_start", length = 7)
    private String declarationTaxYearStart;

    @Column(name = "declaration_tax_year_end", length = 7)
    private String declarationTaxYearEnd;

    @Column(name = "current_tax_year_start", length = 7)
    private String currentTaxYearStart;

    @Column(name = "current_tax_year_end", length = 7)
    private String currentTaxYearEnd;

    @Column(name = "tax_regime")
    private String taxRegime;

    @Column(name = "tax_regime_formatted")
    private String taxRegimeFormatted;

    @Column(name = "is_multiple_tax_regimes_applicable")
    private Boolean isMultipleTaxRegimesApplicable = Boolean.FALSE;

    @Column(name = "is_lender_pan_mandatory")
    private Boolean isLenderPanMandatory = Boolean.FALSE;

    @Column(name = "can_change_tax_regime")
    private Boolean canChangeTaxRegime = Boolean.TRUE;

    @Column(name = "can_allow_edit")
    private Boolean canAllowEdit = Boolean.TRUE;

    @Column(name = "is_staying_in_rented_house")
    private Boolean isStayingInRentedHouse = Boolean.FALSE;

    @Column(name = "is_repaying_self_occupied_loan")
    private Boolean isRepayingSelfOccupiedLoan = Boolean.FALSE;

    @Column(name = "has_let_out_property")
    private Boolean hasLetOutProperty = Boolean.FALSE;

    @Column(name = "status")
    private String status;

    @Column(name = "status_formatted")
    private String statusFormatted;

    @Column(name = "message_types")
    private String messageTypes;

    @Column(name = "tax_plan_count")
    private Integer taxPlanCount;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvHouseRent> houseRents = new ArrayList<>();

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvOtherIncome> otherIncomes = new ArrayList<>();

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvLetOutProperty> letOutProperties = new ArrayList<>();

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvSection6A> section6aDeclarations = new ArrayList<>();

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvPrevEmployment> prevEmploymentDeclarations = new ArrayList<>();

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvPreTaxDeduction> preTaxDeductions = new ArrayList<>();

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeInvTaxSummary> taxSummaries = new ArrayList<>();

    @OneToMany(
    mappedBy = "declaration",
    cascade = CascadeType.ALL,
    orphanRemoval = true
)
private List<EmployeeInvHomeLoan> homeLoans = new ArrayList<>();


    public EmployeeInvestmentDeclaration() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public BasicDetails getEmployee() {
        return employee;
    }

    public void setEmployee(BasicDetails employee) {
        this.employee = employee;
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

    public Boolean getIsLenderPanMandatory() {
        return isLenderPanMandatory;
    }

    public void setIsLenderPanMandatory(Boolean lenderPanMandatory) {
        isLenderPanMandatory = lenderPanMandatory;
    }

    public Boolean getCanChangeTaxRegime() {
        return canChangeTaxRegime;
    }

    public void setCanChangeTaxRegime(Boolean canChangeTaxRegime) {
        this.canChangeTaxRegime = canChangeTaxRegime;
    }

    public Boolean getCanAllowEdit() {
        return canAllowEdit;
    }

    public void setCanAllowEdit(Boolean canAllowEdit) {
        this.canAllowEdit = canAllowEdit;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public List<EmployeeInvHomeLoan> getHomeLoans() {
    return homeLoans;
}

    public void setHomeLoans(List<EmployeeInvHomeLoan> homeLoans) {
        this.homeLoans = homeLoans;
    }

    public List<EmployeeInvHouseRent> getHouseRents() {
        return houseRents;
    }

    public void setHouseRents(List<EmployeeInvHouseRent> houseRents) {
        this.houseRents = houseRents;
    }

    public List<EmployeeInvOtherIncome> getOtherIncomes() {
        return otherIncomes;
    }

    public void setOtherIncomes(List<EmployeeInvOtherIncome> otherIncomes) {
        this.otherIncomes = otherIncomes;
    }

    public List<EmployeeInvLetOutProperty> getLetOutProperties() {
        return letOutProperties;
    }

    public void setLetOutProperties(List<EmployeeInvLetOutProperty> letOutProperties) {
        this.letOutProperties = letOutProperties;
    }

    public List<EmployeeInvSection6A> getSection6aDeclarations() {
        return section6aDeclarations;
    }

    public void setSection6aDeclarations(List<EmployeeInvSection6A> section6aDeclarations) {
        this.section6aDeclarations = section6aDeclarations;
    }

    public List<EmployeeInvPrevEmployment> getPrevEmploymentDeclarations() {
        return prevEmploymentDeclarations;
    }

    public void setPrevEmploymentDeclarations(List<EmployeeInvPrevEmployment> prevEmploymentDeclarations) {
        this.prevEmploymentDeclarations = prevEmploymentDeclarations;
    }

    public List<EmployeeInvPreTaxDeduction> getPreTaxDeductions() {
        return preTaxDeductions;
    }

    public void setPreTaxDeductions(List<EmployeeInvPreTaxDeduction> preTaxDeductions) {
        this.preTaxDeductions = preTaxDeductions;
    }

    public List<EmployeeInvTaxSummary> getTaxSummaries() {
        return taxSummaries;
    }

    public void setTaxSummaries(List<EmployeeInvTaxSummary> taxSummaries) {
        this.taxSummaries = taxSummaries;
    }
}
