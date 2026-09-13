package com.itsdev.payroll.entity.claimsanddeclarations;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminder")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reminder_id", unique = true, nullable = false)
    private String reminderId; // From Zoho: "reminder_id": "3058894000000033372"

    @Column(name = "is_enabled")
    private boolean isEnabled; // From Zoho: "is_enabled": true

    @Column(name = "number_of_days")
    private int numberOfDays; // From Zoho: "number_of_days": 5

    // KEEP ALL ORIGINAL RELATIONSHIPS
    @ManyToOne
    @JoinColumn(name = "fbp_id")
    private FBP fbp;

    @ManyToOne
    @JoinColumn(name = "reimbursement_claim_id")
    private ReimbursementClaim reimbursementClaim;

    @ManyToOne
    @JoinColumn(name = "income_tax_declaration_id")
    private IncomeTaxDeclaration incomeTaxDeclaration;

    @ManyToOne
    @JoinColumn(name = "proof_of_investment_id")
    private ProofOfInvestment proofOfInvestment;

    // NEW FIELDS FOR EMAIL TRACKING
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private BasicDetails employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "sent_date")
    private LocalDateTime sentDate;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // Static factory method for POI reminders
    public static Reminder createForPOI(BasicDetails employee, Organization organization,
            Integer fiscalYear, long daysBefore) {
        Reminder reminder = new Reminder();
        reminder.setEmployee(employee);
        reminder.setOrganization(organization);
        reminder.setFiscalYear(fiscalYear);
        reminder.setNumberOfDays((int) daysBefore);
        reminder.setEnabled(true);
        reminder.setSentDate(LocalDateTime.now());
        reminder.setReminderId(generateReminderId(employee, organization, fiscalYear, daysBefore));
        reminder.setCreatedTime(LocalDateTime.now());
        reminder.setUpdatedTime(LocalDateTime.now());
        return reminder;
    }

    private static String generateReminderId(BasicDetails employee, Organization organization,
            Integer fiscalYear, long daysBefore) {
        return "RMD-" + organization.getOrganizationId() + "-" +
                employee.getEmployeeId() + "-" + fiscalYear + "-" + daysBefore + "-" +
                System.currentTimeMillis();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReminderId() {
        return reminderId;
    }

    public void setReminderId(String reminderId) {
        this.reminderId = reminderId;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public int getNumberOfDays() {
        return numberOfDays;
    }

    public void setNumberOfDays(int numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public FBP getFbp() {
        return fbp;
    }

    public void setFbp(FBP fbp) {
        this.fbp = fbp;
    }

    public ReimbursementClaim getReimbursementClaim() {
        return reimbursementClaim;
    }

    public void setReimbursementClaim(ReimbursementClaim reimbursementClaim) {
        this.reimbursementClaim = reimbursementClaim;
    }

    public IncomeTaxDeclaration getIncomeTaxDeclaration() {
        return incomeTaxDeclaration;
    }

    public void setIncomeTaxDeclaration(IncomeTaxDeclaration incomeTaxDeclaration) {
        this.incomeTaxDeclaration = incomeTaxDeclaration;
    }

    public ProofOfInvestment getProofOfInvestment() {
        return proofOfInvestment;
    }

    public void setProofOfInvestment(ProofOfInvestment proofOfInvestment) {
        this.proofOfInvestment = proofOfInvestment;
    }

    // New fields getters/setters
    public BasicDetails getEmployee() {
        return employee;
    }

    public void setEmployee(BasicDetails employee) {
        this.employee = employee;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    public void setSentDate(LocalDateTime sentDate) {
        this.sentDate = sentDate;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}