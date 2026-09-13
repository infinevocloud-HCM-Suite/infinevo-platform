package com.itsdev.payroll.entity.employee;

import java.time.LocalDateTime;
import java.util.UUID;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "employeeInvitation")
public class EmployeeInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 10)
    private String invitationId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private Boolean isPortalEnabled;

    @Column(nullable = false)
    private Boolean isInvitationAccepted = false;

    @Column(name = "acceptance_token", unique = true)
    private String acceptanceToken;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "rejection_date")
    private LocalDateTime rejectionDate;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @PrePersist
    public void generateToken() {
        if (this.acceptanceToken == null) {
            this.acceptanceToken = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // --- Getters & Setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvitationId() {
        return invitationId;
    }

    public void setInvitationId(String invitationId) {
        this.invitationId = invitationId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Boolean getIsPortalEnabled() {
        return isPortalEnabled;
    }

    public void setIsPortalEnabled(Boolean isPortalEnabled) {
        this.isPortalEnabled = isPortalEnabled;
    }

    public Boolean getIsInvitationAccepted() {
        return isInvitationAccepted;
    }

    public void setIsInvitationAccepted(Boolean isInvitationAccepted) {
        this.isInvitationAccepted = isInvitationAccepted;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getAcceptanceToken() {
        return acceptanceToken;
    }

    public void setAcceptanceToken(String acceptanceToken) {
        this.acceptanceToken = acceptanceToken;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getRejectionDate() {
        return rejectionDate;
    }

    public void setRejectionDate(LocalDateTime rejectionDate) {
        this.rejectionDate = rejectionDate;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }


}
