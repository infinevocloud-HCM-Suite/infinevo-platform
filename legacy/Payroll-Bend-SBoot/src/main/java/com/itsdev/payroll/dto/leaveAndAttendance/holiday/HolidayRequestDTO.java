package com.itsdev.payroll.dto.leaveAndAttendance.holiday;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class HolidayRequestDTO {

    @NotBlank(message = "Organization ID is required")
    private String organizationId; // Multi-org support

    @NotBlank(message = "Holiday name is required")
    @Size(max = 100, message = "Holiday name can be at most 100 characters")
    private String name;

    @NotNull(message = "From date is required")
    private LocalDate fromDate;

    @NotNull(message = "To date is required")
    private LocalDate toDate;

    @Size(max = 500, message = "Description can be at most 500 characters")
    private String description;

    private boolean isRestrictedHoliday;

    // Locations can be empty but not null
    private Set<@NotBlank(message = "Location cannot be blank") String> locations = new HashSet<>();


    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRestrictedHoliday() {
        return isRestrictedHoliday;
    }

    public void setRestrictedHoliday(boolean restrictedHoliday) {
        isRestrictedHoliday = restrictedHoliday;
    }

    public Set<String> getLocations() {
        return locations;
    }

    public void setLocations(Set<String> locations) {
        this.locations = locations;
    }
}

