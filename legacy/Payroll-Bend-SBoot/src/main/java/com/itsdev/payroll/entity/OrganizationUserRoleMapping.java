package com.itsdev.payroll.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "organizationUserRoleMapping")
public class OrganizationUserRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;  // Keycloak userId

    @Column(nullable = false)
    private String organizationId;

    @Column(nullable = true)
    private String roleId;

    @Column(nullable = true)
    private String roleName;

    @Column(nullable = false)
    private Boolean isEmployeePortalEnable = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Boolean getEmployeePortalEnable() {
        return isEmployeePortalEnable;
    }

    public void setEmployeePortalEnable(Boolean employeePortalEnable) {
        isEmployeePortalEnable = employeePortalEnable;
    }
}
