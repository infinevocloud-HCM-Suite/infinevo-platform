package com.phegondev.usersmanagementsystem.dto;

import java.util.List;

public class TimesheetInitialDataDTO {
	
	private String timesheetId;
    private String empId;
    private List<ProjectNameIdDTO> projectNameIds;
	public String getTimesheetId() {
		return timesheetId;
	}
	public void setTimesheetId(String timesheetId) {
		this.timesheetId = timesheetId;
	}
	public String getEmpId() {
		return empId;
	}
	public void setEmpId(String empId) {
		this.empId = empId;
	}
	public List<ProjectNameIdDTO> getProjectNameIds() {
		return projectNameIds;
	}
	public void setProjectNameIds(List<ProjectNameIdDTO> projectNameIds) {
		this.projectNameIds = projectNameIds;
	}
	public TimesheetInitialDataDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	public TimesheetInitialDataDTO(String timesheetId, String empId, List<ProjectNameIdDTO> projectNameIds) {
		super();
		this.timesheetId = timesheetId;
		this.empId = empId;
		this.projectNameIds = projectNameIds;
	}
	@Override
	public String toString() {
		return "TimesheetInitialDataDTO [timesheetId=" + timesheetId + ", empId=" + empId + ", projectNameIds="
				+ projectNameIds + "]";
	}
	
	
    
    
   
    

}
