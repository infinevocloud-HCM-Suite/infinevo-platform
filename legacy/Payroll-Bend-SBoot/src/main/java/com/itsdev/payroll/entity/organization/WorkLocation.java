package com.itsdev.payroll.entity.organization;

import jakarta.persistence.*;

@Entity
@Table(name = "workLocations")
public class WorkLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workLocationId", unique = true, nullable = false)
    private String workLocationId;

    @Column(name = "workLocationName", nullable = false)
    private String workLocationName;

    @Column(name = "streetAddress1")
    private String streetAddress1;

    @Column(name = "streetAddress2")
    private String streetAddress2;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "zipCode")
    private String zipCode;

    @Column(name = "country")
    private String country;

    @Column(name = "isFilingAddress")
    private Boolean isFilingAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;
    

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean status = true;
    
	public Boolean getStatus() {
		return status;
	}

	public void setStatus(Boolean status) {
		this.status = status;
	}


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkLocationId() {
        return workLocationId;
    }
    public void setWorkLocationId(String workLocationId) {
        this.workLocationId = workLocationId;
    }

    public String getWorkLocationName() {
        return workLocationName;
    }
    public void setWorkLocationName(String workLocationName) {
        this.workLocationName = workLocationName;
    }

    public String getStreetAddress1() {
        return streetAddress1;
    }
    public void setStreetAddress1(String streetAddress1) {
        this.streetAddress1 = streetAddress1;
    }

    public String getStreetAddress2() {
        return streetAddress2;
    }
    public void setStreetAddress2(String streetAddress2) {
        this.streetAddress2 = streetAddress2;
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

    public String getZipCode() {
        return zipCode;
    }
    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public Boolean getIsFilingAddress() {
        return isFilingAddress;
    }
    public void setIsFilingAddress(Boolean isFilingAddress) {
        this.isFilingAddress = isFilingAddress;
    }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}
