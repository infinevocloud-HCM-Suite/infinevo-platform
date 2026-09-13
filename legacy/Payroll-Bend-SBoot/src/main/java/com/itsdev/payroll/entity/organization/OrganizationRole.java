package com.itsdev.payroll.entity.organization;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizationRole")
public class OrganizationRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    private String roleName;
    private String accessType;
    private Boolean userActionRequired;
    private Boolean isDefault;
    private String roleDescription;

    @Column(nullable = false)
    private String status = "active";

    @Column(nullable = false)
    private Boolean isDeleted = false;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

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
