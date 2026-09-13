package com.itsdev.payroll.entity.statutorycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "esi")
public class Esi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean isActive;
    private String employeeContribution;
    private String employerContribution;
    private String registrationNumber;
    private LocalDate registrationDate;
    private Boolean canEnableEmployerEsiInCtc;
    private Boolean isIncludedInSalaryStructure;
    private String deductionCycle;
    private String deductionCycleFormatted;
    private String registrationDateFormatted;
    private Boolean isIncludedInCtc;
    private String name;
    private Boolean isAssociatedWithEmployee;

    @OneToOne
    @JoinColumn(name = "organizationId")
    private Organization organization;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getEmployeeContribution() { return employeeContribution; }
    public void setEmployeeContribution(String employeeContribution) { this.employeeContribution = employeeContribution; }

    public String getEmployerContribution() { return employerContribution; }
    public void setEmployerContribution(String employerContribution) { this.employerContribution = employerContribution; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public LocalDate getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }

    public Boolean getCanEnableEmployerEsiInCtc() { return canEnableEmployerEsiInCtc; }
    public void setCanEnableEmployerEsiInCtc(Boolean canEnableEmployerEsiInCtc) { this.canEnableEmployerEsiInCtc = canEnableEmployerEsiInCtc; }

    public Boolean getIsIncludedInSalaryStructure() { return isIncludedInSalaryStructure; }
    public void setIsIncludedInSalaryStructure(Boolean isIncludedInSalaryStructure) { this.isIncludedInSalaryStructure = isIncludedInSalaryStructure; }

    public String getDeductionCycle() { return deductionCycle; }
    public void setDeductionCycle(String deductionCycle) { this.deductionCycle = deductionCycle; }

    public String getDeductionCycleFormatted() { return deductionCycleFormatted; }
    public void setDeductionCycleFormatted(String deductionCycleFormatted) { this.deductionCycleFormatted = deductionCycleFormatted; }

    public String getRegistrationDateFormatted() { return registrationDateFormatted; }
    public void setRegistrationDateFormatted(String registrationDateFormatted) { this.registrationDateFormatted = registrationDateFormatted; }

    public Boolean getIsIncludedInCtc() { return isIncludedInCtc; }
    public void setIsIncludedInCtc(Boolean isIncludedInCtc) { this.isIncludedInCtc = isIncludedInCtc; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsAssociatedWithEmployee() { return isAssociatedWithEmployee; }
    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) { this.isAssociatedWithEmployee = isAssociatedWithEmployee; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}