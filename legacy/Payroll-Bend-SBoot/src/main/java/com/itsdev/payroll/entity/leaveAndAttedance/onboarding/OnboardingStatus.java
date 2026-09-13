package com.itsdev.payroll.entity.leaveAndAttedance.onboarding;


import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "onboarding_status")
public class OnboardingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to organization
    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    // Onboarding step statuses (5 steps only)
    @Column(name = "organization_details_completed", nullable = false)
    private boolean organizationDetailsCompleted = false;

    @Column(name = "leave_setup_completed", nullable = false)
    private boolean leaveSetupCompleted = false;

    @Column(name = "holiday_setup_completed", nullable = false)
    private boolean holidaySetupCompleted = false;

    @Column(name = "attendance_setup_completed", nullable = false)
    private boolean attendanceSetupCompleted = false;

    @Column(name = "preferences_setup_completed", nullable = false)
    private boolean preferencesSetupCompleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
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

