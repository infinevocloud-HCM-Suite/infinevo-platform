package com.phegondev.usersmanagementsystem.dto.useraccess;

public class RoleDTO {
    private Long roleId;
    private String roleName;
    private String description;

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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public RoleDTO() {
		super();
		// TODO Auto-generated constructor stub
	}


	public RoleDTO(Long roleId, String roleName, String description) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
    }

	@Override
	public String toString() {
		return "RoleDTO [roleId=" + roleId + ", roleName=" + roleName + ", description=" + description + "]";
	}
	
	

}
