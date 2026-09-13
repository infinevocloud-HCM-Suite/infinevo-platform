package com.phegondev.usersmanagementsystem.dto;

public class ProjectNameIdDTO {
	
	private String projectName;
    private Long projectId;
    
	public String getProjectName() {
		return projectName;
	}
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}
	public Long getProjectId() {
		return projectId;
	}
	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}
	public ProjectNameIdDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	public ProjectNameIdDTO(String projectName, Long projectId) {
		super();
		this.projectName = projectName;
		this.projectId = projectId;
	}
	
	public ProjectNameIdDTO( Long projectId, String projectName) {
		super();
		this.projectName = projectName;
		this.projectId = projectId;
	}
	
	
    
    
    

}
