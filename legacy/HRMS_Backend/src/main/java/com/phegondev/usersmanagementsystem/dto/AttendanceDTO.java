package com.phegondev.usersmanagementsystem.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AttendanceDTO {
    private Long id;
    private String employeeId;
    private String employeeName;
    private LocalDateTime inTime;
    private LocalDateTime outTime;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ClockSessionDTO> clockSessions;

    private boolean endDay;

    public boolean isEndDay() {
        return endDay;
    }

    public void setEndDay(boolean endDay) {
        this.endDay = endDay;
    }




    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public LocalDateTime getInTime() { return inTime; }
    public void setInTime(LocalDateTime inTime) { this.inTime = inTime; }

    public LocalDateTime getOutTime() { return outTime; }
    public void setOutTime(LocalDateTime outTime) { this.outTime = outTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<ClockSessionDTO> getClockSessions() { return clockSessions; }
    public void setClockSessions(List<ClockSessionDTO> clockSessions) { this.clockSessions = clockSessions; }
}
