package com.phegondev.usersmanagementsystem.dto;

public class AttendanceRequest {
    private String date; // "2025-08-21"
    private String time; // "10:20:42"

    // Getter and Setter for date
    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }

    // Getter and Setter for time
    public String getTime() {
        return time;
    }
    public void setTime(String time) {
        this.time = time;
    }

}
