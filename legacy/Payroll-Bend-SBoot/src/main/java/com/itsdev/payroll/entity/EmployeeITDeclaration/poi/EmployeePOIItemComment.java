package com.itsdev.payroll.entity.EmployeeITDeclaration.poi;

import com.itsdev.payroll.entity.employee.BasicDetails;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee_poi_item_comment")
public class EmployeePOIItemComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_item_id", nullable = false)
    private EmployeePOIItem poiItem;

    @Column(name = "comment", columnDefinition = "TEXT", nullable = false)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commented_by_employee_id")
    private BasicDetails commentedByEmployee;

    @Column(name = "commented_by_admin")
    private String commentedByAdmin; // Admin user ID or name

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_to_comment_id")
    private EmployeePOIItemComment responseTo; // For threaded conversations

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EmployeePOIItem getPoiItem() {
        return poiItem;
    }

    public void setPoiItem(EmployeePOIItem poiItem) {
        this.poiItem = poiItem;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public BasicDetails getCommentedByEmployee() {
        return commentedByEmployee;
    }

    public void setCommentedByEmployee(BasicDetails commentedByEmployee) {
        this.commentedByEmployee = commentedByEmployee;
    }

    public String getCommentedByAdmin() {
        return commentedByAdmin;
    }

    public void setCommentedByAdmin(String commentedByAdmin) {
        this.commentedByAdmin = commentedByAdmin;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public EmployeePOIItemComment getResponseTo() {
        return responseTo;
    }

    public void setResponseTo(EmployeePOIItemComment responseTo) {
        this.responseTo = responseTo;
    }
}