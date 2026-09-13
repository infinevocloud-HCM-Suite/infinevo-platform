package com.phegondev.usersmanagementsystem.dto.useraccess;

import java.util.List;

public class MapActionRequestDto {
	
	 private Long roleId;
	 private List<Long> actionList;


	    public Long getRoleId() {
	        return roleId;
	    }

	    public void setRoleId(Long roleId) {
	        this.roleId = roleId;
	    }

	    public List<Long> getActionList() {
	        return actionList;
	    }

	    public void setActionList(List<Long> actionList) {
	        this.actionList = actionList;
	    }

}
