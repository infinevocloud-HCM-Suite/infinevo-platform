package com.phegondev.usersmanagementsystem.dto.useraccess;

public class RoleIdNameDTO {
	
	private Long roleId;
	private String roleName;
	
	public Long getRoleId() {
		return roleId;
	}
	public void setRoleId(Long roleId) {
		this.roleId = roleId;
	}
	public String getRoleName() {
		return roleName;
	}
	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}
	
	public RoleIdNameDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	public RoleIdNameDTO(Long roleId, String roleName) {
		super();
		this.roleId = roleId;
		this.roleName = roleName;
	}
	@Override
	public String toString() {
		return "RoleIdNameDTO [roleId=" + roleId + ", roleName=" + roleName + "]";
	}
	
	

}
