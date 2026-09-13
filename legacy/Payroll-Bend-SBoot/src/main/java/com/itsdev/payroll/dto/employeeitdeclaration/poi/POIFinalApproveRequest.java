package com.itsdev.payroll.dto.employeeitdeclaration.poi;

public class POIFinalApproveRequest {
    // private Boolean consideredForIt;
    private String adminComment;

    // Default constructor
    public POIFinalApproveRequest() {
    }

    // Getters and Setters
    // public Boolean getConsideredForIt() {
    // return consideredForIt;
    // }

    // public void setConsideredForIt(Boolean consideredForIt) {
    // this.consideredForIt = consideredForIt;
    // }

    public String getAdminComment() {
        return adminComment;
    }

    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }
}