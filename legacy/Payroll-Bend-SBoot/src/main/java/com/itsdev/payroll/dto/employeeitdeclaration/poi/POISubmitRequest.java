package com.itsdev.payroll.dto.employeeitdeclaration.poi;

public class POISubmitRequest {
    private String notes; // Optional notes from employee
    
    // Default constructor
    public POISubmitRequest() {
    }
    
    // Getters and Setters
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
}