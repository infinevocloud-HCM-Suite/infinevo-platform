package com.phegondev.usersmanagementsystem.dto.useraccess;

public class ActionIdNameDTO {
	
	 private Long actionId;
	 private String actionName;
	 
	public Long getActionId() {
		return actionId;
	}
	public void setActionId(Long actionId) {
		this.actionId = actionId;
	}
	public String getActionName() {
		return actionName;
	}
	public void setActionName(String actionName) {
		this.actionName = actionName;
	}
	
	public ActionIdNameDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	public ActionIdNameDTO(Long actionId, String actionName) {
		super();
		this.actionId = actionId;
		this.actionName = actionName;
	}
	
	@Override
	public String toString() {
		return "ActionIdNameDTO [actionId=" + actionId + ", actionName=" + actionName + "]";
	}

	 
}
