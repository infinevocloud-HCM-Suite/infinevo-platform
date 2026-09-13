package com.itsdev.payroll.dto.leaveAndAttendance.onboarding;

import java.time.LocalDateTime;

public class OnboardingStatusDTO {

    private Long id;
    private Long organizationId;

    private boolean organizationDetailsCompleted;
    private boolean leaveSetupCompleted;
    private boolean holidaySetupCompleted;
    private boolean attendanceSetupCompleted;
    private boolean preferencesSetupCompleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // constructors
    public OnboardingStatusDTO() {}

    public OnboardingStatusDTO(Long id, Long organizationId,
                               boolean organizationDetailsCompleted,
                               boolean leaveSetupCompleted,
                               boolean holidaySetupCompleted,
                               boolean attendanceSetupCompleted,
                               boolean preferencesSetupCompleted,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.organizationDetailsCompleted = organizationDetailsCompleted;
        this.leaveSetupCompleted = leaveSetupCompleted;
        this.holidaySetupCompleted = holidaySetupCompleted;
        this.attendanceSetupCompleted = attendanceSetupCompleted;
        this.preferencesSetupCompleted = preferencesSetupCompleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // getters and setters (you can generate them in IDE)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public boolean isOrganizationDetailsCompleted() {
        return organizationDetailsCompleted;
    }

    public void setOrganizationDetailsCompleted(boolean organizationDetailsCompleted) {
        this.organizationDetailsCompleted = organizationDetailsCompleted;
    }

    public boolean isLeaveSetupCompleted() {
        return leaveSetupCompleted;
    }

    public void setLeaveSetupCompleted(boolean leaveSetupCompleted) {
        this.leaveSetupCompleted = leaveSetupCompleted;
    }

    public boolean isHolidaySetupCompleted() {
        return holidaySetupCompleted;
    }

    public void setHolidaySetupCompleted(boolean holidaySetupCompleted) {
        this.holidaySetupCompleted = holidaySetupCompleted;
    }

    public boolean isAttendanceSetupCompleted() {
        return attendanceSetupCompleted;
    }

    public void setAttendanceSetupCompleted(boolean attendanceSetupCompleted) {
        this.attendanceSetupCompleted = attendanceSetupCompleted;
    }

    public boolean isPreferencesSetupCompleted() {
        return preferencesSetupCompleted;
    }

    public void setPreferencesSetupCompleted(boolean preferencesSetupCompleted) {
        this.preferencesSetupCompleted = preferencesSetupCompleted;
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
}

