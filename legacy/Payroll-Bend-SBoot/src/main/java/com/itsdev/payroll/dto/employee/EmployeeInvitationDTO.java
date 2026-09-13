package com.itsdev.payroll.dto.employee;

public class EmployeeInvitationDTO {
    private String invitationId;
    private String email;
    private String employeeId;
    private Boolean isPortalEnabled;
    private Boolean isInvitationAccepted;
    private String organizationId;
    private String acceptanceToken;
    private java.time.LocalDateTime expiryDate;

    // --- Getters & Setters ---
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

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getAcceptanceToken() {
        return acceptanceToken;
    }

    public void setAcceptanceToken(String acceptanceToken) {
        this.acceptanceToken = acceptanceToken;
    }

    public java.time.LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(java.time.LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }


}
