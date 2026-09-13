package com.itsdev.payroll.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "organizationUserMapping")
public class OrganizationUserMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId; // From Keycloak (sub claim)

    @Column(nullable = false)
    private String organizationId; // From Organization entity

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

}