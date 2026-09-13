package com.phegondev.usersmanagementsystem.dto.useraccess;

import java.util.List;

public class MapUserRequestDto {
	
	 private Integer userId;
	 private List<String> roleActionIds;
	 
	public Integer getUserId() {
		return userId;
	}
	public void setUserId(Integer userId) {
		this.userId = userId;
	}
	public List<String> getRoleActionIds() {
		return roleActionIds;
	}
	public void setRoleActionIds(List<String> roleActionIds) {
		this.roleActionIds = roleActionIds;
	}
	@Override
	public String toString() {
		return "MapUserRequestDto [userId=" + userId + ", roleActionIds=" + roleActionIds + "]";
	}

	
	 
	 

}
