package com.phegondev.usersmanagementsystem.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
//@Data
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Residential address is required")
    private String residentialAddress;

    private String permanentAddress;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Country is required")
    private String country;

    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "^[a-zA-Z0-9\\s-]{3,10}$", message = "Invalid postal code format")
    private String postalCode;

    @NotBlank(message = "Work email is required")
    @Email(message = "Invalid email format")
    private String workEmail;

    @Email(message = "Invalid email format")
    private String personalEmail;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\d{10,15}$", message = "Mobile number must be 10-15 digits")
    private String mobileNumber;

    @NotBlank(message = "Primary emergency contact name is required")
    private String primaryEmergencyContactName;

    @NotBlank(message = "Primary emergency contact number is required")
    @Pattern(regexp = "^\\d{10,15}$", message = "Contact number must be 10-15 digits")
    private String primaryEmergencyContactNumber;

    @NotBlank(message = "Relationship to primary emergency contact is required")
    private String relationshipToPrimaryEmergencyContact;

    private String secondaryEmergencyContactName;

    @Pattern(regexp = "^\\d{10,15}$", message = "Contact number must be 10-15 digits", groups = WhenNotEmpty.class)
    private String secondaryEmergencyContactNumber;

    private String relationshipToSecondaryEmergencyContact;

    private String familyDoctorName;

    @Pattern(regexp = "^\\d{10,15}$", message = "Contact number must be 10-15 digits", groups = WhenNotEmpty.class)
    private String familyDoctorContactNumber;

    @OneToOne(mappedBy = "contact", cascade = CascadeType.ALL)
    @JsonBackReference
    private Employee employee;

    public interface WhenNotEmpty {}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getResidentialAddress() {
		return residentialAddress;
	}

	public void setResidentialAddress(String residentialAddress) {
		this.residentialAddress = residentialAddress;
	}

	public String getPermanentAddress() {
		return permanentAddress;
	}

	public void setPermanentAddress(String permanentAddress) {
		this.permanentAddress = permanentAddress;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public String getWorkEmail() {
		return workEmail;
	}

	public void setWorkEmail(String workEmail) {
		this.workEmail = workEmail;
	}

	public String getPersonalEmail() {
		return personalEmail;
	}

	public void setPersonalEmail(String personalEmail) {
		this.personalEmail = personalEmail;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public void setMobileNumber(String mobileNumber) {
		this.mobileNumber = mobileNumber;
	}

	public String getPrimaryEmergencyContactName() {
		return primaryEmergencyContactName;
	}

	public void setPrimaryEmergencyContactName(String primaryEmergencyContactName) {
		this.primaryEmergencyContactName = primaryEmergencyContactName;
	}

	public String getPrimaryEmergencyContactNumber() {
		return primaryEmergencyContactNumber;
	}

	public void setPrimaryEmergencyContactNumber(String primaryEmergencyContactNumber) {
		this.primaryEmergencyContactNumber = primaryEmergencyContactNumber;
	}

	public String getRelationshipToPrimaryEmergencyContact() {
		return relationshipToPrimaryEmergencyContact;
	}

	public void setRelationshipToPrimaryEmergencyContact(String relationshipToPrimaryEmergencyContact) {
		this.relationshipToPrimaryEmergencyContact = relationshipToPrimaryEmergencyContact;
	}

	public String getSecondaryEmergencyContactName() {
		return secondaryEmergencyContactName;
	}

	public void setSecondaryEmergencyContactName(String secondaryEmergencyContactName) {
		this.secondaryEmergencyContactName = secondaryEmergencyContactName;
	}

	public String getSecondaryEmergencyContactNumber() {
		return secondaryEmergencyContactNumber;
	}

	public void setSecondaryEmergencyContactNumber(String secondaryEmergencyContactNumber) {
		this.secondaryEmergencyContactNumber = secondaryEmergencyContactNumber;
	}

	public String getRelationshipToSecondaryEmergencyContact() {
		return relationshipToSecondaryEmergencyContact;
	}

	public void setRelationshipToSecondaryEmergencyContact(String relationshipToSecondaryEmergencyContact) {
		this.relationshipToSecondaryEmergencyContact = relationshipToSecondaryEmergencyContact;
	}

	public String getFamilyDoctorName() {
		return familyDoctorName;
	}

	public void setFamilyDoctorName(String familyDoctorName) {
		this.familyDoctorName = familyDoctorName;
	}

	public String getFamilyDoctorContactNumber() {
		return familyDoctorContactNumber;
	}

	public void setFamilyDoctorContactNumber(String familyDoctorContactNumber) {
		this.familyDoctorContactNumber = familyDoctorContactNumber;
	}

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}
    
    
}