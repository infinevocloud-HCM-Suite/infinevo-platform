package com.phegondev.usersmanagementsystem.dto.timesheet;

import java.time.DayOfWeek;
import java.time.LocalDate;


public class DayEntryDto {
    private LocalDate date;
    private DayOfWeek dayName;
    private Float hours;
    private String description;
	public LocalDate getDate() {
		return date;
	}
	public void setDate(LocalDate date) {
		this.date = date;
	}
	public DayOfWeek getDayName() {
		return dayName;
	}
	public void setDayName(DayOfWeek dayName) {
		this.dayName = dayName;
	}
	public Float getHours() {
		return hours;
	}
	public void setHours(Float hours) {
		this.hours = hours;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	
	
	public DayEntryDto() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public String toString() {
		return "DayEntryDto [date=" + date + ", dayName=" + dayName + ", hours=" + hours + ", description="
				+ description + "]";
	}
    
    
}
