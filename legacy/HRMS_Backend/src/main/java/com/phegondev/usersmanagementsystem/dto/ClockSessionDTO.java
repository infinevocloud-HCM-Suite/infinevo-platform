package com.phegondev.usersmanagementsystem.dto;

import java.time.LocalDateTime;

public class ClockSessionDTO {
    private Long id;
    private LocalDateTime inTime;
    private LocalDateTime outTime;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getInTime() { return inTime; }
    public void setInTime(LocalDateTime inTime) { this.inTime = inTime; }

    public LocalDateTime getOutTime() { return outTime; }
    public void setOutTime(LocalDateTime outTime) { this.outTime = outTime; }
}

