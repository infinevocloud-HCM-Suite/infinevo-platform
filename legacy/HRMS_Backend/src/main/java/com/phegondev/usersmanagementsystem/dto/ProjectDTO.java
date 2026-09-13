package com.phegondev.usersmanagementsystem.dto;

import java.util.Date;
import java.util.List;

public class ProjectDTO {
    private Long id;
    private String name;
    private String category;
    private Date startDate;
    private Date endDate;
    // private String notification;
    private String priority;
    private Double budget;
    private String description;
    private String status;
    private Integer progress;
    private List<String> assignedEmployeeNames;

    public List<String> getAssignedEmployeeNames() {
        return assignedEmployeeNames;
    }

    public void setAssignedEmployeeNames(List<String> assignedEmployeeNames) {
        this.assignedEmployeeNames = assignedEmployeeNames;
    }

    // ✅ Add this field
    private List<AssignmentDTO> assignments;

    private String managerId;
    private String managerName;

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    // public String getNotification() {
    //     return notification;
    // }

    // public void setNotification(String notification) {
    //     this.notification = notification;
    // }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Double getBudget() {
        return budget;
    }

    public void setBudget(Double budget) {
        this.budget = budget;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public List<AssignmentDTO> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<AssignmentDTO> assignments) {
        this.assignments = assignments;
    }
}
