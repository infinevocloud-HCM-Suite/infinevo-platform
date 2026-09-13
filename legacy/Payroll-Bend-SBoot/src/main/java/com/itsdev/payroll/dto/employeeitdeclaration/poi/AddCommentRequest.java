package com.itsdev.payroll.dto.employeeitdeclaration.poi;

public class AddCommentRequest {
    private String comment;
    private Long responseToCommentId; // Optional: For threaded replies

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getResponseToCommentId() {
        return responseToCommentId;
    }

    public void setResponseToCommentId(Long responseToCommentId) {
        this.responseToCommentId = responseToCommentId;
    }
}