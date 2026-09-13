package com.phegondev.usersmanagementsystem.dto;

public class TaskNameIdDTO {
	
	   private Long taskId;
	   private String taskName;
	   
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
	
	public TaskNameIdDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	public TaskNameIdDTO(Long taskId, String taskName) {
		super();
		this.taskId = taskId;
		this.taskName = taskName;
	}
	@Override
	public String toString() {
		return "TaskNameIdDTO [taskId=" + taskId + ", taskName=" + taskName + "]";
	}
	   
	   
	   
	   

}
