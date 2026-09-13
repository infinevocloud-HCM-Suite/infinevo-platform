package com.itsdev.payroll.dto.leaveAndAttendance.holiday;


import java.util.HashSet;
import java.util.Set;
public class HolidayResponseDTO {

    private String holidayId;
    private String name;
    private String fromDate;
    private String fromDateFormatted;
    private String toDate;
    private String toDateFormatted;
    private String description;
    private boolean restrictedHoliday;
    private boolean canEdit;
    private Set<String> locations = new HashSet<>();

    public String getHolidayId() { return holidayId; }
    public void setHolidayId(String holidayId) { this.holidayId = holidayId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getFromDateFormatted() { return fromDateFormatted; }
    public void setFromDateFormatted(String fromDateFormatted) { this.fromDateFormatted = fromDateFormatted; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public String getToDateFormatted() { return toDateFormatted; }
    public void setToDateFormatted(String toDateFormatted) { this.toDateFormatted = toDateFormatted; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isRestrictedHoliday() { return restrictedHoliday; }
    public void setRestrictedHoliday(boolean restrictedHoliday) { this.restrictedHoliday = restrictedHoliday; }

    public boolean getCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public Set<String> getLocations() { return locations; }
    public void setLocations(Set<String> locations) { this.locations = locations != null ? locations : new HashSet<>(); }
}
