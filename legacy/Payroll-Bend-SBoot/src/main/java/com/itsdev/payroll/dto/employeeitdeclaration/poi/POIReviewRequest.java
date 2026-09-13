package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.math.BigDecimal;

public class POIReviewRequest {
    private BigDecimal approvedAmount;
    private String adminComment;
    
    // Default constructor
    public POIReviewRequest() {
    }
    
    // Getters and Setters
    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }
    
    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }
    
    public String getAdminComment() {
        return adminComment;
    }
    
    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }
}