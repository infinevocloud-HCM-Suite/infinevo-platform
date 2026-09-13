package com.phegondev.usersmanagementsystem.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
//@Data
public class Identification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Immigration status is required")
    private String immigrationStatus;

	@NotBlank(message = "Aadhar Card number is required")
    @Pattern(regexp = "^\\d{12}$", message = "Aadhar Card must be 12 digits")
    private String aadharCardNumber;

	@NotBlank(message = "PAN Card number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN Card must be in format AAAAA9999A")
    private String panCardNumber;

	@NotBlank(message = "Address Proof is required")
    private String addressProof;

    private String addressDocumentName;

    @NotBlank(message = "Address document number is required")
    private String addressDocumentNumber;

    @Pattern(regexp = "^$|^\\d{10}$", message = "Personal Tax ID must be 10 digits if provided")
    private String personalTaxId;

    private String socialInsurance;

    @NotBlank(message = "ID Proof is required")
    private String idProof;

    private String documentName;

    @NotBlank(message = "Document number is required")
    private String documentNumber;

    @OneToOne(mappedBy = "identification", cascade = CascadeType.ALL)
    @JsonBackReference
    private Employee employee;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getImmigrationStatus() {
		return immigrationStatus;
	}

	public void setImmigrationStatus(String immigrationStatus) {
		this.immigrationStatus = immigrationStatus;
	}

	public String getAadharCardNumber() {
        return aadharCardNumber;
    }

    public void setAadharCardNumber(String aadharCardNumber) {
        this.aadharCardNumber = aadharCardNumber;
    }

	public String getPanCardNumber() {
        return panCardNumber;
    }

    public void setPanCardNumber(String panCardNumber) {
        this.panCardNumber = panCardNumber;
    }


	public String getPersonalTaxId() {
		return personalTaxId;
	}

	public void setPersonalTaxId(String personalTaxId) {
		this.personalTaxId = personalTaxId;
	}

	public String getSocialInsurance() {
		return socialInsurance;
	}

	public void setSocialInsurance(String socialInsurance) {
		this.socialInsurance = socialInsurance;
	}

	public String getIdProof() {
		return idProof;
	}

	public void setIdProof(String idProof) {
		this.idProof = idProof;
	}

	public String getDocumentName() {
		return documentName;
	}

	public void setDocumentName(String documentName) {
		this.documentName = documentName;
	}

	public String getDocumentNumber() {
		return documentNumber;
	}

	public void setDocumentNumber(String documentNumber) {
		this.documentNumber = documentNumber;
	}

	public String getAddressProof() {
        return addressProof;
    }

    public void setAddressProof(String addressProof) {
        this.addressProof = addressProof;
    }

    public String getAddressDocumentName() {
        return addressDocumentName;
    }

    public void setAddressDocumentName(String addressDocumentName) {
        this.addressDocumentName = addressDocumentName;
    }

    public String getAddressDocumentNumber() {
        return addressDocumentNumber;
    }

    public void setAddressDocumentNumber(String addressDocumentNumber) {
        this.addressDocumentNumber = addressDocumentNumber;
    }

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}
    
    
}