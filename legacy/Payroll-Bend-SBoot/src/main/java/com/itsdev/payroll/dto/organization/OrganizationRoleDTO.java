package com.itsdev.payroll.dto.organization;

import jakarta.validation.constraints.NotBlank;

public class OrganizationRoleDTO {
    private String roleId;
    
    @NotBlank(message = "Role name is required")
    
    private String roleName;
    private String accessType;
    private Boolean userActionRequired;
    private Boolean isDefault;
    private String roleDescription;
    private String status  = "active";
    private Boolean isDeleted;


    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getAccessType() { return accessType; }
    public void setAccessType(String accessType) { this.accessType = accessType; }

    public Boolean getUserActionRequired() { return userActionRequired; }
    public void setUserActionRequired(Boolean userActionRequired) { this.userActionRequired = userActionRequired; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public String getRoleDescription() { return roleDescription; }
    public void setRoleDescription(String roleDescription) { this.roleDescription = roleDescription; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }


}
