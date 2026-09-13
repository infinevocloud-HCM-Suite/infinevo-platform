
package com.phegondev.usersmanagementsystem.dto;

import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import jakarta.persistence.Column;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class LeaveRequestsDTO {

    private Long id;
    private String employeeId;
    private String employeeName;
    private LocalDate fromDate;
    private LocalDate toDate;

    private String medicalCertificateUrl;

    private boolean hasMedicalCertificate;
    private String medicalCertificateName;


    private boolean isHalfDay;
    private String halfDayPeriod;
    private String reason;
    private String lateReason;
    private double totalDays;
    private Map<Long, Float> manualDaysAllocation;
    private LocalDateTime createdDate;
    private LocalDateTime reportingManagerUpdatedAt;
    private LocalDateTime hrUpdatedAt;
    private String reportingManagerComment;
    private String hrComment;

    private LeaveRequestStatus reportingManagerStatus;
    private LeaveRequestStatus hrStatus;
    
    

    private Boolean leaveBalanceDeducted;

    private Boolean leaveBalanceRecredited;
    
	private String fileUrl;

	private String publicId;


    // new fields:
    private List<LeaveDistributionDTO> leaveDistribution;
    private Map<String, Double> manualDaysAllocationByName; // optional


    // inside class
    private List<String> selectedDates; // ISO strings "yyyy-MM-dd"

    // getter & setter
    public List<String> getSelectedDates() {
        return selectedDates;
    }
    public void setSelectedDates(List<String> selectedDates) {
        this.selectedDates = selectedDates;
    }


    // getters & setters for new fields
    public List<LeaveDistributionDTO> getLeaveDistribution() { return leaveDistribution; }
    public void setLeaveDistribution(List<LeaveDistributionDTO> leaveDistribution) { this.leaveDistribution = leaveDistribution; }
    public Map<String, Double> getManualDaysAllocationByName() { return manualDaysAllocationByName; }
    public void setManualDaysAllocationByName(Map<String, Double> manualDaysAllocationByName) {
        this.manualDaysAllocationByName = manualDaysAllocationByName;
    }
	


    public String getFileUrl() {
		return fileUrl;
	}

	public void setFileUrl(String fileUrl) {
		this.fileUrl = fileUrl;
	}

	public String getPublicId() {
		return publicId;
	}

	public void setPublicId(String publicId) {
		this.publicId = publicId;
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

    // Getters and Setters

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

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    // Add getter and setter
    public String getMedicalCertificateUrl() {
        return medicalCertificateUrl;
    }

    public void setMedicalCertificateUrl(String medicalCertificateUrl) {
        this.medicalCertificateUrl = medicalCertificateUrl;
    }


    public boolean isHasMedicalCertificate() {
        return hasMedicalCertificate;
    }

    public void setHasMedicalCertificate(boolean hasMedicalCertificate) {
        this.hasMedicalCertificate = hasMedicalCertificate;
    }

    public String getMedicalCertificateName() {
        return medicalCertificateName;
    }

    public void setMedicalCertificateName(String medicalCertificateName) {
        this.medicalCertificateName = medicalCertificateName;
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

    public String getLateReason() {
        return lateReason;
    }

    public void setLateReason(String lateReason) {
        this.lateReason = lateReason;
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

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
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

    @Override
    public String toString() {
        return "LeaveRequestsDTO{" +
                "id=" + id +
                ", employeeId='" + employeeId + '\'' +
                ", employeeName='" + employeeName + '\'' +
                ", fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", isHalfDay=" + isHalfDay +
                ", halfDayPeriod='" + halfDayPeriod + '\'' +
                ", reason='" + reason + '\'' +
                ", lateReason='" + lateReason + '\'' +
                ", totalDays=" + totalDays +
                ", manualDaysAllocation=" + manualDaysAllocation +
                ", createdDate=" + createdDate +
                ", reportingManagerStatus=" + reportingManagerStatus +
                ", hrStatus=" + hrStatus +
                ", reportingManagerUpdatedAt=" + reportingManagerUpdatedAt +
                ", hrUpdatedAt=" + hrUpdatedAt +
                ", reportingManagerComment='" + reportingManagerComment + '\'' +
                ", hrComment='" + hrComment + '\'' +
                '}';
    }
}


