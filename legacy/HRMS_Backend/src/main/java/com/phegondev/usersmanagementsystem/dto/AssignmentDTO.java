package com.phegondev.usersmanagementsystem.dto;


public class AssignmentDTO {
    private Long id;
    private String empId;
    private String empName;
    private Long projectId;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmpId() {
        return empId;
    }

    public void setEmpId(String empId) {
        this.empId = empId;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

	@Override
	public String toString() {
		return "AssignmentDTO [id=" + id + ", empId=" + empId + ", empName=" + empName + ", projectId=" + projectId
				+ "]";
	}
    
    
}