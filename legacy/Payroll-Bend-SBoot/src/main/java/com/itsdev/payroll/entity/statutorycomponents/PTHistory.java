package com.itsdev.payroll.entity.statutorycomponents;

import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.databind.JsonNode;
import com.itsdev.payroll.entity.organization.Organization;
import com.vladmihalcea.hibernate.type.json.JsonType;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pt_history")
public class PTHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String organizationId;

    @Column(nullable = false)
    private String state;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private JsonNode oldJson;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private JsonNode newJson;

    private String operation;   // "INSERT", "UPDATE", "DELETE"

    private LocalDateTime changedAt;

    private String changedBy;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(String organizationId) {
		this.organizationId = organizationId;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public JsonNode getOldJson() {
		return oldJson;
	}

	public void setOldJson(JsonNode oldJson) {
		this.oldJson = oldJson;
	}

	public JsonNode getNewJson() {
		return newJson;
	}

	public void setNewJson(JsonNode newJson) {
		this.newJson = newJson;
	}

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}

	public LocalDateTime getChangedAt() {
		return changedAt;
	}

	public void setChangedAt(LocalDateTime changedAt) {
		this.changedAt = changedAt;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public void setChangedBy(String changedBy) {
		this.changedBy = changedBy;
	}

    
}
