package com.itsdev.payroll.entity.employee;

import com.itsdev.payroll.dto.employee.EmployeeFBPComponentDTO;
import com.itsdev.payroll.dto.employee.EmployeeVariableEarningDTO;
import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ctc_structure")
@Where(clause = "is_deleted = false")
@SQLDelete(sql = """
    UPDATE ctc_structure
    SET is_deleted = true,
        deleted_at = now()
    WHERE id = ?
""")
public class CtcStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double annualCtc;   // full CTC entered in frontend

    // 👇 Employee-specific earnings mapped here
    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeEarning> earnings =  new ArrayList<>();;

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeBenefit> benefits =  new ArrayList<>();;

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeReimbursement> reimbursements =  new ArrayList<>();;

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VariableEarning> variableEarnings =  new ArrayList<>();;

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FbpComponent> fbpComponents =  new ArrayList<>();;

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CtcEpfComponent> epfComponents = new ArrayList<>();

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CtcEsiComponent> esiComponents = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // ✅ Add link to employee (because each employee has a CTC structure)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",
            referencedColumnName = "employee_id",
            nullable = false
    )
    private BasicDetails employee;

    @Column(nullable = false)
    private BigDecimal monthlySalary;



    // ===============================
// CTC REVISION SUPPORT FIELDS
// ===============================

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;







    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;  // New CTC is active by default

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Column(name = "payment_month")
    private String paymentMonth;   // "yyyy-MM"


    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_status")
    private CtcRevisionStatus revisionStatus;


    @Column(name = "is_revision", nullable = false)
    private Boolean revision = false;

    @Column(name= "change_in_percent", nullable = true)
    private BigDecimal changeInPercentage;

    @Column(name = "applied_in_payrun", nullable = false)
    private Boolean appliedInPayrun = false;



    // ============================================================
    // ⭐ CRITICAL FIX — REQUIRED TO PREVENT DUPLICATE IDENTIFIER ERROR
    // ============================================================


    public Boolean getAppliedInPayrun() {
        return appliedInPayrun;
    }

    public void setAppliedInPayrun(Boolean appliedInPayrun) {
        this.appliedInPayrun = appliedInPayrun;
    }

    public BigDecimal getChangeInPercentage() {
        return changeInPercentage;
    }

    public void setChangeInPercentage(BigDecimal changeInPercentage) {
        this.changeInPercentage = changeInPercentage;
    }


    // getters and setters


    public Boolean getRevision() {
        return revision;
    }

    public void setRevision(Boolean revision) {
        this.revision = revision;
    }

    public CtcRevisionStatus getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(CtcRevisionStatus revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getPaymentMonth() {
        return paymentMonth;
    }

    public void setPaymentMonth(String paymentMonth) {
        this.paymentMonth = paymentMonth;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Getter
    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    // Setter
    public void setMonthlySalary(BigDecimal monthlySalary) {
        this.monthlySalary = monthlySalary;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getAnnualCtc() {
        return annualCtc;
    }

    public void setAnnualCtc(Double annualCtc) {
        this.annualCtc = annualCtc;
    }

    public List<EmployeeEarning> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<EmployeeEarning> earnings) {
        this.earnings = earnings;
    }

    public List<EmployeeBenefit> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<EmployeeBenefit> benefits) {
        this.benefits = benefits;
    }

    public List<EmployeeReimbursement> getReimbursements() {
        return reimbursements;
    }

    public void setReimbursements(List<EmployeeReimbursement> reimbursements) {
        this.reimbursements = reimbursements;
    }

    public List<VariableEarning> getVariableEarnings() {
        return variableEarnings;
    }

    public void setVariableEarnings(List<VariableEarning> variableEarnings) {
        this.variableEarnings = variableEarnings;
    }

    public List<FbpComponent> getFbpComponents() {
        return fbpComponents;
    }

    public void setFbpComponents(List<FbpComponent> fbpComponents) {
        this.fbpComponents = fbpComponents;
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

    public List<CtcEpfComponent> getEpfComponents() { return epfComponents; }
    public void setEpfComponents(List<CtcEpfComponent> epfComponents) { this.epfComponents = epfComponents; }

    public List<CtcEsiComponent> getEsiComponents() { return esiComponents; }
    public void setEsiComponents(List<CtcEsiComponent> esiComponents) { this.esiComponents = esiComponents; }
}

