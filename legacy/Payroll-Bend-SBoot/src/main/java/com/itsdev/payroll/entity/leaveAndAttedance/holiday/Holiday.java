package com.itsdev.payroll.entity.leaveAndAttedance.holiday;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Internal primary key

    @Column(name = "holidayId", nullable = false, unique = true)
    private String holidayId; // Public ID for API calls

    @Column(nullable = false)
    private String name; // Holiday name

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate; // Start date

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate; // End date

    @Column
    private String description; // Holiday description

    @Column(name = "is_restricted_holiday", nullable = false)
    private boolean restrictedHoliday; // Restricted holiday flag

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization; // Multi-org support

    @ElementCollection
    @CollectionTable(
            name = "holiday_locations",
            joinColumns = @JoinColumn(name = "holiday_id")
    )
    @Column(name = "location")
    private Set<String> locations = new HashSet<>(); // Multiple locations as simple strings

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean status = true; // Active/Inactive flag

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHolidayId() {
        return holidayId;
    }

    public void setHolidayId(String holidayId) {
        this.holidayId = holidayId;
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
        return restrictedHoliday;
    }

    public void setRestrictedHoliday(boolean restrictedHoliday) {
        this.restrictedHoliday = restrictedHoliday;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Set<String> getLocations() {
        return locations;
    }

    public void setLocations(Set<String> locations) {
        this.locations = locations;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }
}

