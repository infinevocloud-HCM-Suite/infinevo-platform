package com.phegondev.usersmanagementsystem.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeeLeaveBalanceDTO {
    private Long id;
    private String name;
    private String description;
    private Double defaultDays;
    private Boolean carryForward;  // Changed to boolean
    private Float  remainingDays;
    private LocalDate endDate;
	private LocalDateTime createdAt;


	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public Double getDefaultDays() {
		return defaultDays;
	}
	public void setDefaultDays(Double defaultDays) {
		this.defaultDays = defaultDays;
	}
	public Boolean getCarryForward() {
		return carryForward;
	}
	public void setCarryForward(Boolean carryForward) {
		this.carryForward = carryForward;
	}
	public Float getRemainingDays() {
		return remainingDays;
	}
	public void setRemainingDays(Float remainingDays) {
		this.remainingDays = remainingDays;
	}
	public LocalDate getEndDate() {
		return endDate;
	}
	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	@Override
	public String toString() {
		return "EmployeeLeaveBalanceDTO{" +
				"id=" + id +
				", name='" + name + '\'' +
				", description='" + description + '\'' +
				", defaultDays=" + defaultDays +
				", carryForward=" + carryForward +
				", remainingDays=" + remainingDays +
				", endDate=" + endDate +
				", createdAt=" + createdAt +
				'}';
	}
}