package com.itsdev.payroll.entity.EmployeeITDeclaration.poi;

import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvestmentDeclaration;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "employee_proof_of_investment",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"organization_id", "employee_id", "fiscal_year"})
        }
)
public class EmployeeProofOfInvestment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK → employee_investment_declaration.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id", nullable = false)
    private EmployeeInvestmentDeclaration declaration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private BasicDetails employee;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "tax_regime_at_submission")
    private String taxRegimeAtSubmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayRunStatus status; // DRAFT / SUBMITTED / APPROVED / REJECTED

    @Column(name = "submitted_by")
    private String submittedBy; // EMPLOYEE / ADMIN

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    // ================= IT CONSIDERATION =================

    @Column(name = "considered_for_it", nullable = false)
    private Boolean consideredForIt = Boolean.FALSE;

    @Column(name = "considered_by")
    private String consideredBy;

    @Column(name = "considered_date")
    private LocalDateTime consideredDate;

    @Column(name = "final_annual_tax", precision = 15, scale = 2)
    private BigDecimal finalAnnualTax;

    @Column(name = "tax_regime_at_consideration", length = 10)
    private String taxRegimeAtConsideration;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @OneToMany(
            mappedBy = "proofOfInvestment",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeePOIItem> poiItems = new ArrayList<>();

    /* ================= GETTERS & SETTERS ================= */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmployeeInvestmentDeclaration getDeclaration() { return declaration; }
    public void setDeclaration(EmployeeInvestmentDeclaration declaration) { this.declaration = declaration; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public BasicDetails getEmployee() { return employee; }
    public void setEmployee(BasicDetails employee) { this.employee = employee; }

    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }

    public String getTaxRegimeAtSubmission() { return taxRegimeAtSubmission; }
    public void setTaxRegimeAtSubmission(String taxRegimeAtSubmission) {
        this.taxRegimeAtSubmission = taxRegimeAtSubmission;
    }

public PayRunStatus getStatus() {
    return status;
}

public void setStatus(PayRunStatus status) {
    this.status = status;
}


    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public LocalDateTime getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(LocalDateTime submittedDate) { this.submittedDate = submittedDate; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedDate() { return approvedDate; }
    public void setApprovedDate(LocalDateTime approvedDate) { this.approvedDate = approvedDate; }

    public Boolean getConsideredForIt() { return consideredForIt; }
    public void setConsideredForIt(Boolean consideredForIt) { this.consideredForIt = consideredForIt; }

        public String getConsideredBy() {
        return consideredBy;
    }

    public void setConsideredBy(String consideredBy) {
        this.consideredBy = consideredBy;
    }

    public LocalDateTime getConsideredDate() {
        return consideredDate;
    }

    public void setConsideredDate(LocalDateTime consideredDate) {
        this.consideredDate = consideredDate;
    }

    // ================= FINAL TAX FREEZE =================

public BigDecimal getFinalAnnualTax() {
    return finalAnnualTax;
}

public void setFinalAnnualTax(BigDecimal finalAnnualTax) {
    this.finalAnnualTax = finalAnnualTax;
}

public String getTaxRegimeAtConsideration() {
    return taxRegimeAtConsideration;
}

public void setTaxRegimeAtConsideration(String taxRegimeAtConsideration) {
    this.taxRegimeAtConsideration = taxRegimeAtConsideration;
}


    public LocalDateTime getCreatedTime() { return createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }

    public List<EmployeePOIItem> getPoiItems() { return poiItems; }
    public void setPoiItems(List<EmployeePOIItem> poiItems) { this.poiItems = poiItems; }
}
