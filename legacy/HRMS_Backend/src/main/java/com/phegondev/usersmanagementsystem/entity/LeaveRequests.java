package com.phegondev.usersmanagementsystem.entity;

import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import com.phegondev.usersmanagementsystem.enumuration.OvertimeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "leave_requests")
public class LeaveRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @OneToOne(mappedBy = "leaveRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private LeaveDocument medicalCertificate;



    @Column(name = "is_half_day")
    private boolean isHalfDay;

    @Column(name = "half_day_period")
    private String halfDayPeriod; // "first" or "second"

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

   // @Enumerated(EnumType.STRING)
  //  @Column(name = "status")
   // private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporting_manager_status", nullable = false)
    private LeaveRequestStatus reportingManagerStatus = LeaveRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "hr_status", nullable = false)
    private LeaveRequestStatus hrStatus = LeaveRequestStatus.PENDING;

    @Column(name = "total_days")
    private double totalDays;

    @ElementCollection
    @CollectionTable(
            name = "leave_manual_days",
            joinColumns = @JoinColumn(name = "leave_request_id")
    )
    @MapKeyColumn(name = "leave_type_id")
    @Column(name = "days_allocated")
    private Map<Long, Float> manualDaysAllocation;

    @Column(name = "late_reason", columnDefinition = "TEXT")
    private String lateReason;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "reporting_manager_updated_at")
    private LocalDateTime reportingManagerUpdatedAt;

    @Column(name = "hr_updated_at")
    private LocalDateTime hrUpdatedAt;

    @Column(name = "reporting_manager_comment", length = 1000)
    private String reportingManagerComment;

    @Column(name = "hr_comment", length = 1000)
    private String hrComment;

    @Column(name = "leave_balance_deducted")
    private Boolean leaveBalanceDeducted = false;

    @Column(name = "leave_balance_recredited")
    private Boolean leaveBalanceRecredited = false;

    // inside class LeaveRequests, add fields
    @ElementCollection
    @CollectionTable(name = "leave_selected_dates", joinColumns = @JoinColumn(name = "leave_request_id"))
    @Column(name = "selected_date")
    private Set<LocalDate> selectedDates = new LinkedHashSet<>();

    @Column(name = "lop_generated")
    private Boolean lopGenerated = false;

    @ElementCollection
    @CollectionTable(name = "leave_request_lop", joinColumns = @JoinColumn(name = "leave_request_id"))
    @MapKeyColumn(name = "leave_type_id")
    @Column(name = "lop_days")
    private Map<Long, Float> lopAllocation= new HashMap<>();

    // getter and setter

    public Set<LocalDate> getSelectedDates() {
        return selectedDates;
    }

    public void setSelectedDates(Set<LocalDate> selectedDates) {
        this.selectedDates = selectedDates;
    }

    public Boolean getLeaveBalanceDeducted() {
        return leaveBalanceDeducted;
    }

    public void setLeaveBalanceDeducted(Boolean leaveBalanceDeducted) {
        this.leaveBalanceDeducted = leaveBalanceDeducted;
    }

    public Boolean getLeaveBalanceRecredited() {
        return leaveBalanceRecredited;
    }

    public void setLeaveBalanceRecredited(Boolean leaveBalanceRecredited) {
        this.leaveBalanceRecredited = leaveBalanceRecredited;
    }



    public LeaveRequestStatus getReportingManagerStatus() {
        return reportingManagerStatus;
    }

    public void setReportingManagerStatus(LeaveRequestStatus reportingManagerStatus) {
        this.reportingManagerStatus = reportingManagerStatus;
    }

    public LeaveRequestStatus getHrStatus() {
        return hrStatus;
    }

    public void setHrStatus(LeaveRequestStatus hrStatus) {
        this.hrStatus = hrStatus;
    }

    public LocalDateTime getReportingManagerUpdatedAt() {
        return reportingManagerUpdatedAt;
    }

    public void setReportingManagerUpdatedAt(LocalDateTime reportingManagerUpdatedAt) {
        this.reportingManagerUpdatedAt = reportingManagerUpdatedAt;
    }

    public LocalDateTime getHrUpdatedAt() {
        return hrUpdatedAt;
    }

    public void setHrUpdatedAt(LocalDateTime hrUpdatedAt) {
        this.hrUpdatedAt = hrUpdatedAt;
    }

    public String getReportingManagerComment() {
        return reportingManagerComment;
    }

    public void setReportingManagerComment(String reportingManagerComment) {
        this.reportingManagerComment = reportingManagerComment;
    }

    public String getHrComment() {
        return hrComment;
    }

    public void setHrComment(String hrComment) {
        this.hrComment = hrComment;
    }


    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }


    public String getLateReason() {
        return lateReason;
    }

    public void setLateReason(String lateReason) {
        this.lateReason = lateReason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LeaveDocument getMedicalCertificate() {
        return medicalCertificate;
    }

    public void setMedicalCertificate(LeaveDocument medicalCertificate) {
        this.medicalCertificate = medicalCertificate;
    }


    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public boolean getIsHalfDay() {
        return isHalfDay;
    }

    public void setIsHalfDay(boolean isHalfDay) {
        this.isHalfDay = isHalfDay;
    }

    public String getHalfDayPeriod() {
        return halfDayPeriod;
    }

    public void setHalfDayPeriod(String halfDayPeriod) {
        this.halfDayPeriod = halfDayPeriod;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }


    public double getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(double totalDays) {
        this.totalDays = totalDays;
    }

    public Map<Long, Float> getManualDaysAllocation() {
        return manualDaysAllocation;
    }

    public void setManualDaysAllocation(Map<Long, Float> manualDaysAllocation) {
        this.manualDaysAllocation = manualDaysAllocation;
    }

    public Boolean getLopGenerated() {
        return lopGenerated;
    }

    public void setLopGenerated(Boolean lopGenerated) {
        this.lopGenerated = lopGenerated;
    }

    public Map<Long, Float> getLopAllocation() {
        return lopAllocation;
    }

    public void setLopAllocation(Map<Long, Float> lopAllocation) {
        this.lopAllocation = lopAllocation;
    }
}