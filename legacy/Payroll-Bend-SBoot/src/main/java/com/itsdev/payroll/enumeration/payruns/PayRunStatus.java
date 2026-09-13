package com.itsdev.payroll.enumeration.payruns;

public enum PayRunStatus {
    READY,
    DRAFT,
    SUBMITTED,
    APPROVAL_PENDING, // Changed from "APPROVAL PENDING" to "APPROVAL_PENDING"
    APPROVED,
    REJECTED,
    COMPLETED;

    // Helper method to get formatted display name
    public String getFormattedName() {
        switch (this) {
            case DRAFT:
                return "Draft";
            case SUBMITTED:
                return "Submitted";
            case APPROVAL_PENDING:
                return "Approval Pending";
            case APPROVED:
                return "Approved";
            case REJECTED:
                return "Rejected";
            default:
                return this.name();
        }
    }
}