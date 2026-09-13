package com.phegondev.usersmanagementsystem.dto.useraccess;

import java.util.List;

public class MapRoleRequestDto {
	
    private Long categoryId;
    private List<Long> roleList;
    
	public Long getCategoryId() {
		return categoryId;
	}
	public void setCategoryId(Long categoryId) {
		this.categoryId = categoryId;
	}
	public List<Long> getRoleList() {
		return roleList;
	}
	public void setRoleList(List<Long> roleList) {
		this.roleList = roleList;
	}
	
	@Override
	public String toString() {
		return "MapRoleRequestDto [categoryId=" + categoryId + ", roleList=" + roleList + "]";
	}

    
    
}
