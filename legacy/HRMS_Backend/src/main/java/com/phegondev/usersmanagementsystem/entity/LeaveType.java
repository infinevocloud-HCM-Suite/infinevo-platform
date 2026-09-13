package com.phegondev.usersmanagementsystem.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_days", nullable = false)
    private double defaultDays; // Changed from int to double

  //  @Column(name = "is_active", nullable = false)
   // private boolean isActive = true;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;


    @Column(name = "leave_carried_forward", nullable = false)
    private boolean leaveCarriedForward = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ElementCollection
    @CollectionTable(name = "leave_type_employee_ids", joinColumns = @JoinColumn(name = "leave_type_id"))
    @Column(name = "employee_id")
    private Set<String> employeeIds = new HashSet<>();
    
    @Column(name = "apply_to_all_employees", nullable = false)
    private boolean applyToAllEmployees = false;

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

	public double getDefaultDays() {
		return defaultDays;
	}

	public void setDefaultDays(double defaultDays) {
		this.defaultDays = defaultDays;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public boolean isLeaveCarriedForward() {
		return leaveCarriedForward;
	}

	public void setLeaveCarriedForward(boolean leaveCarriedForward) {
		this.leaveCarriedForward = leaveCarriedForward;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Set<String> getEmployeeIds() {
		return employeeIds;
	}

	public void setEmployeeIds(Set<String> employeeIds) {
		this.employeeIds = employeeIds;
	}
	
	

	public boolean isApplyToAllEmployees() {
		return applyToAllEmployees;
	}

	public void setApplyToAllEmployees(boolean applyToAllEmployees) {
		this.applyToAllEmployees = applyToAllEmployees;
	}

	@Override
	public String toString() {
		return "LeaveType [id=" + id + ", name=" + name + ", description=" + description + ", defaultDays="
				+ defaultDays + ", startDate=" + startDate + ", endDate=" + endDate + ", leaveCarriedForward="
				+ leaveCarriedForward + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ", employeeIds="
				+ employeeIds + ", applyToAllEmployees=" + applyToAllEmployees + "]";
	}

    
 
	
	

    
    
}