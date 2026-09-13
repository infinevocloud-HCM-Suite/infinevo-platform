package com.itsdev.payroll.entity.employee;


import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Table(name = "employee_personal_detail")
public class EmployeePersonalDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "personal_mail", nullable = false)
    private String personalMail;


    @Column(name = "date_of_birth", nullable = false)
    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Column(name = "father_name")
    private String fatherName;

    @Column(name = "pan", unique = true, length = 10)
    @Size(min = 10, max = 10, message = "PAN must be exactly 10 characters")
    @Pattern(
            regexp = "[A-Z]{5}[0-9]{4}[A-Z]",
            message = "Invalid PAN format"
    )
    private String pan;

    @Column(name = "differently_abled_type")
    private String differentlyAbledType;

    @Column(name = "is_eligible_for_full_income_tax_exemption")
    private Boolean isEligibleForFullIncomeTaxExemption = false;

    // ===== Present Residential Address (Embedded) =====
    @Embedded
    private ResidentialAddress presentResidentialAddress;

    // ===== Custom Fields (JSON or child table, here we keep as JSON column) =====
    @Lob
    @Column(name = "custom_fields")
    private String customFields; // store as JSON string for flexibility

    // ===== Organization Mapping =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // EmployeePersonalDetail.java

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private BasicDetails employee;


    // Getters & Setters


    public Boolean getEligibleForFullIncomeTaxExemption() {
        return isEligibleForFullIncomeTaxExemption;
    }

    public void setEligibleForFullIncomeTaxExemption(Boolean eligibleForFullIncomeTaxExemption) {
        isEligibleForFullIncomeTaxExemption = eligibleForFullIncomeTaxExemption;
    }

    public BasicDetails getEmployee() {
        return employee;
    }

    public void setEmployee(BasicDetails employee) {
        this.employee = employee;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPersonalMail() {
        return personalMail;
    }

    public void setPersonalMail(String personalMail) {
        this.personalMail = personalMail;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getFatherName() {
        return fatherName;
    }

    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getDifferentlyAbledType() {
        return differentlyAbledType;
    }

    public void setDifferentlyAbledType(String differentlyAbledType) {
        this.differentlyAbledType = differentlyAbledType;
    }

    public Boolean getIsEligibleForFullIncomeTaxExemption() {
        return isEligibleForFullIncomeTaxExemption;
    }

    public void setIsEligibleForFullIncomeTaxExemption(Boolean isEligibleForFullIncomeTaxExemption) {
        this.isEligibleForFullIncomeTaxExemption = isEligibleForFullIncomeTaxExemption;
    }

    public ResidentialAddress getPresentResidentialAddress() {
        return presentResidentialAddress;
    }

    public void setPresentResidentialAddress(ResidentialAddress presentResidentialAddress) {
        this.presentResidentialAddress = presentResidentialAddress;
    }

    public String getCustomFields() {
        return customFields;
    }

    public void setCustomFields(String customFields) {
        this.customFields = customFields;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }
}

