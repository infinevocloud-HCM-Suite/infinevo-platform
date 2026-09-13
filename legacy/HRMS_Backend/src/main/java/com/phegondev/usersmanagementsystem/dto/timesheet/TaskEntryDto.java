package com.phegondev.usersmanagementsystem.dto.timesheet;

import java.util.List;

public class TaskEntryDto {
    private Long taskId;
    private String taskName;
    private List<DayEntryDto> days;
	public Long getTaskId() {
		return taskId;
	}
	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}
	public String getTaskName() {
		return taskName;
	}
	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}
	public List<DayEntryDto> getDays() {
		return days;
	}
	public void setDays(List<DayEntryDto> days) {
		this.days = days;
	}
	public TaskEntryDto(Long taskId, String taskName) {
		super();
		this.taskId = taskId;
		this.taskName = taskName;
	}
	
	
	
	public TaskEntryDto() {
		super();
		// TODO Auto-generated constructor stub
	}
	@Override
	public String toString() {
		return "TaskEntryDto [taskId=" + taskId + ", taskName=" + taskName + ", days=" + days + "]";
	}
    
    
}

