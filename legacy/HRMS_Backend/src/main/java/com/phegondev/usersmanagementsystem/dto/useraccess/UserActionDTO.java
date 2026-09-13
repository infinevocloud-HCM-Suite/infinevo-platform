package com.phegondev.usersmanagementsystem.dto.useraccess;

public class UserActionDTO {
	
	 private Long roleId;
	 private Long actionId;
	 

	public Long getRoleId() {
		return roleId;
	}
	public void setRoleId(Long roleId) {
		this.roleId = roleId;
	}
	public Long getActionId() {
		return actionId;
	}
	public void setActionId(Long actionId) {
		this.actionId = actionId;
	}
	
	public UserActionDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	public UserActionDTO( Long roleId, Long actionId) {
		super();
	
		this.roleId = roleId;
		this.actionId = actionId;
	}
	
	@Override
	public String toString() {
		return "UserActionDTO [ roleId=" + roleId + ", actionId=" + actionId + "]";
	}
	 
	 
	 

}
