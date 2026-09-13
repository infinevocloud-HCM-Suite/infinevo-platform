package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.time.LocalDateTime;

public class POIItemCommentDTO {
    private Long id;
    private Long poiItemId;
    private String comment;
    private Long commentedByEmployeeId;
    private String commentedByEmployeeName;
    private String commentedByAdmin;
    private String commentedByAdminName; // Optional: Admin display name
    private LocalDateTime createdTime;
    private Long responseToCommentId;
    private POIItemCommentDTO responseTo; // Nested for threading (optional)
    private boolean isEmployeeComment; // Helper field for frontend

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPoiItemId() {
        return poiItemId;
    }

    public void setPoiItemId(Long poiItemId) {
        this.poiItemId = poiItemId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getCommentedByEmployeeId() {
        return commentedByEmployeeId;
    }

    public void setCommentedByEmployeeId(Long commentedByEmployeeId) {
        this.commentedByEmployeeId = commentedByEmployeeId;
    }

    public String getCommentedByEmployeeName() {
        return commentedByEmployeeName;
    }

    public void setCommentedByEmployeeName(String commentedByEmployeeName) {
        this.commentedByEmployeeName = commentedByEmployeeName;
    }

    public String getCommentedByAdmin() {
        return commentedByAdmin;
    }

    public void setCommentedByAdmin(String commentedByAdmin) {
        this.commentedByAdmin = commentedByAdmin;
    }

    public String getCommentedByAdminName() {
        return commentedByAdminName;
    }

    public void setCommentedByAdminName(String commentedByAdminName) {
        this.commentedByAdminName = commentedByAdminName;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public Long getResponseToCommentId() {
        return responseToCommentId;
    }

    public void setResponseToCommentId(Long responseToCommentId) {
        this.responseToCommentId = responseToCommentId;
    }

    public POIItemCommentDTO getResponseTo() {
        return responseTo;
    }

    public void setResponseTo(POIItemCommentDTO responseTo) {
        this.responseTo = responseTo;
    }

    public boolean getIsEmployeeComment() {
        return commentedByEmployeeId != null;
    }

    public void setIsEmployeeComment(boolean isEmployeeComment) {
        this.isEmployeeComment = isEmployeeComment;
    }

    // Helper method to determine comment origin
    public boolean isEmployeeComment() {
        return commentedByEmployeeId != null;
    }
}