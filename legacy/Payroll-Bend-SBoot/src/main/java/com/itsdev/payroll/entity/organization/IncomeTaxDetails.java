package com.itsdev.payroll.entity.organization;

import jakarta.persistence.*;

@Entity
@Table(name = "incomeTaxDetails")
public class IncomeTaxDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tanNumber")
    private String tanNumber;

    @Column(name = "panNumber")
    private String panNumber;

    @Column(name = "tdsCircle")
    private String tdsCircle;

    @Column(name = "authorizedPersonName")
    private String authorizedPersonName;

    @Column(name = "authorizedPersonParent")
    private String authorizedPersonParent;

    @Column(name = "authorizedPersonDesignation")
    private String authorizedPersonDesignation;

    @Column(name = "depositSchedule")
    private String depositSchedule;

    @Column(name = "employeeId")
    private String employeeId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTanNumber() { return tanNumber; }
    public void setTanNumber(String tanNumber) { this.tanNumber = tanNumber; }

    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }

    public String getTdsCircle() { return tdsCircle; }
    public void setTdsCircle(String tdsCircle) { this.tdsCircle = tdsCircle; }

    public String getAuthorizedPersonName() { return authorizedPersonName; }
    public void setAuthorizedPersonName(String authorizedPersonName) { this.authorizedPersonName = authorizedPersonName; }

    public String getAuthorizedPersonParent() { return authorizedPersonParent; }
    public void setAuthorizedPersonParent(String authorizedPersonParent) { this.authorizedPersonParent = authorizedPersonParent; }

    public String getAuthorizedPersonDesignation() { return authorizedPersonDesignation; }
    public void setAuthorizedPersonDesignation(String authorizedPersonDesignation) { this.authorizedPersonDesignation = authorizedPersonDesignation; }

    public String getDepositSchedule() { return depositSchedule; }
    public void setDepositSchedule(String depositSchedule) { this.depositSchedule = depositSchedule; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
}