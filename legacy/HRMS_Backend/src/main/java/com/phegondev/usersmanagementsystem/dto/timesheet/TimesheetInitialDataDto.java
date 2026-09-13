package com.phegondev.usersmanagementsystem.dto.timesheet;

import java.util.List;

public class TimesheetInitialDataDto {

	private String employeeId;
    private String employeeName;
    private List<ProjectEntryDto> projectNameIds;
    
    
    
	public String getEmployeeId() {
		return employeeId;
	}
	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}
	public String getEmployeeName() {
		return employeeName;
	}
	public void setEmployeeName(String employeeName) {
		this.employeeName = employeeName;
	}

	public List<ProjectEntryDto> getProjectNameIds() {
		return projectNameIds;
	}
	public void setProjectNameIds(List<ProjectEntryDto> projectNameIds) {
		this.projectNameIds = projectNameIds;
	}
	public TimesheetInitialDataDto() {
		super();
		// TODO Auto-generated constructor stub
	}
	public TimesheetInitialDataDto( String employeeId, List<ProjectEntryDto> projectNameIds) {
		super();
		this.employeeId = employeeId;
		this.projectNameIds = projectNameIds;
	}
	@Override
	public String toString() {
		return "TimesheetInitialDataDto [ employeeId=" + employeeId + ", projectNameIds="
				+ projectNameIds + "]";
	}
	
	
    
    
   
    

}

