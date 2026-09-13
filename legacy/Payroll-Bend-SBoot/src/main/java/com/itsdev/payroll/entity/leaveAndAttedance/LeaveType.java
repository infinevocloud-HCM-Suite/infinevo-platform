package com.itsdev.payroll.entity.leaveAndAttedance;

import com.itsdev.payroll.entity.organization.Department;
import com.itsdev.payroll.entity.organization.Designation;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.WorkLocation;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "leave_type")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------------- Organization ----------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // ---------------- Basic Info ----------------

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private String type; // e.g., "paid" / "unpaid"

    @Column(nullable = false)
    private String unit; // e.g., "day_based"

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "allow_half_day")
    private Boolean allowHalfDay = false;

    @Column(name = "validity_from")
    private LocalDate validityFrom;

    @Column(name = "validity_to")
    private LocalDate validityTo;

    // ---------------- Eligibility ----------------
    @ElementCollection
    @CollectionTable(
            name = "leave_type_genders",
            joinColumns = @JoinColumn(name = "leave_type_id")
    )
    @Column(name = "gender")
    private Set<String> genders;

    @ManyToMany
    @JoinTable(
            name = "leave_type_departments",
            joinColumns = @JoinColumn(name = "leave_type_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id", referencedColumnName = "departmentId")
    )
    private Set<Department> departments;

    @ManyToMany
    @JoinTable(
            name = "leave_type_designations",
            joinColumns = @JoinColumn(name = "leave_type_id"),
            inverseJoinColumns = @JoinColumn(name = "designation_id", referencedColumnName = "designationId")
    )

    private Set<Designation> designations;
    @ManyToMany
    @JoinTable(
            name = "leave_type_work_locations",
            joinColumns = @JoinColumn(name = "leave_type_id"),
            inverseJoinColumns = @JoinColumn(name = "work_location_id", referencedColumnName = "workLocationId")
    )
    private Set<WorkLocation> workLocations;

    // ---------------- Accrual configuration ----------------
    @Column(name = "accrual_is_enabled")
    private Boolean accrualEnabled = false;

    @Column(name = "accrual_frequency")
    private String accrualFrequency;

    @Column(name = "accrual_units", precision = 10, scale = 2)
    private BigDecimal accrualUnits;

    @Column(name = "accrual_regular_hours")
    private String accrualRegularHours;

    // ---------------- Reset configuration ----------------
    @Column(name = "reset_is_enabled")
    private Boolean resetEnabled = false;

    @Column(name = "reset_frequency")
    private String resetFrequency;

    // ---------------- Carry forward ----------------
    @Column(name = "carry_forward_is_enabled")
    private Boolean carryForwardEnabled = false;

    @Column(name = "carry_forward_units", precision = 10, scale = 2)
    private BigDecimal carryForwardUnits;

    // ---------------- Encashment ----------------
    @Column(name = "encashment_is_enabled")
    private Boolean encashmentEnabled = false;

    @Column(name = "encashment_units", precision = 10, scale = 2)
    private BigDecimal encashmentUnits;

    // ---------------- Past / Future booking ----------------
    @Column(name = "past_booking_is_enabled")
    private Boolean pastBookingEnabled = false;

    @Column(name = "past_booking_limit_days")
    private Integer pastBookingLimitDays;

    @Column(name = "future_booking_is_enabled")
    private Boolean futureBookingEnabled = false;

    @Column(name = "future_booking_limit_days")
    private Integer futureBookingLimitDays;

    // ---------------- Include weekend / holiday ----------------
    @Column(name = "include_weekend_is_enabled")
    private Boolean includeWeekendEnabled = false;

    @Column(name = "include_weekend_min_days")
    private Integer includeWeekendMinDays;

    @Column(name = "include_holiday_is_enabled")
    private Boolean includeHolidayEnabled = false;

    @Column(name = "include_holiday_min_days")
    private Integer includeHolidayMinDays;

    // ---------------- Exceed balance ----------------
    @Column(name = "exceed_balance_is_enabled")
    private Boolean exceedBalanceEnabled = false;

    @Column(name = "exceed_balance_mode")
    private String exceedBalanceMode;

    // ---------------- Effective after ----------------
    @Column(name = "effective_after_period")
    private String effectiveAfterPeriod;

    @Column(name = "effective_after_units")
    private Integer effectiveAfterUnits;

    // ---------------- Pro-rate ----------------
    @Column(name = "pro_rate_is_enabled")
    private Boolean proRateEnabled = false;

    // ---------------- Max leave per application ----------------
    @Column(name = "max_leave_per_application")
    private Integer maxLeavePerApplication; // null or -1 = unlimited


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getAllowHalfDay() {
        return allowHalfDay;
    }

    public void setAllowHalfDay(Boolean allowHalfDay) {
        this.allowHalfDay = allowHalfDay;
    }

    public LocalDate getValidityFrom() {
        return validityFrom;
    }

    public void setValidityFrom(LocalDate validityFrom) {
        this.validityFrom = validityFrom;
    }

    public LocalDate getValidityTo() {
        return validityTo;
    }

    public void setValidityTo(LocalDate validityTo) {
        this.validityTo = validityTo;
    }

    public Set<String> getGenders() {
        return genders;
    }

    public void setGenders(Set<String> genders) {
        this.genders = genders;
    }

    public Set<Department> getDepartments() {
        return departments;
    }

    public void setDepartments(Set<Department> departments) {
        this.departments = departments;
    }

    public Set<Designation> getDesignations() {
        return designations;
    }

    public void setDesignations(Set<Designation> designations) {
        this.designations = designations;
    }

    public Set<WorkLocation> getWorkLocations() {
        return workLocations;
    }

    public void setWorkLocations(Set<WorkLocation> workLocations) {
        this.workLocations = workLocations;
    }

    public Boolean getAccrualEnabled() {
        return accrualEnabled;
    }

    public void setAccrualEnabled(Boolean accrualEnabled) {
        this.accrualEnabled = accrualEnabled;
    }

    public String getAccrualFrequency() {
        return accrualFrequency;
    }

    public void setAccrualFrequency(String accrualFrequency) {
        this.accrualFrequency = accrualFrequency;
    }

    public BigDecimal getAccrualUnits() {
        return accrualUnits;
    }

    public void setAccrualUnits(BigDecimal accrualUnits) {
        this.accrualUnits = accrualUnits;
    }

    public String getAccrualRegularHours() {
        return accrualRegularHours;
    }

    public void setAccrualRegularHours(String accrualRegularHours) {
        this.accrualRegularHours = accrualRegularHours;
    }

    public Boolean getResetEnabled() {
        return resetEnabled;
    }

    public void setResetEnabled(Boolean resetEnabled) {
        this.resetEnabled = resetEnabled;
    }

    public String getResetFrequency() {
        return resetFrequency;
    }

    public void setResetFrequency(String resetFrequency) {
        this.resetFrequency = resetFrequency;
    }

    public Boolean getCarryForwardEnabled() {
        return carryForwardEnabled;
    }

    public void setCarryForwardEnabled(Boolean carryForwardEnabled) {
        this.carryForwardEnabled = carryForwardEnabled;
    }

    public BigDecimal getCarryForwardUnits() {
        return carryForwardUnits;
    }

    public void setCarryForwardUnits(BigDecimal carryForwardUnits) {
        this.carryForwardUnits = carryForwardUnits;
    }

    public Boolean getEncashmentEnabled() {
        return encashmentEnabled;
    }

    public void setEncashmentEnabled(Boolean encashmentEnabled) {
        this.encashmentEnabled = encashmentEnabled;
    }

    public BigDecimal getEncashmentUnits() {
        return encashmentUnits;
    }

    public void setEncashmentUnits(BigDecimal encashmentUnits) {
        this.encashmentUnits = encashmentUnits;
    }

    public Boolean getPastBookingEnabled() {
        return pastBookingEnabled;
    }

    public void setPastBookingEnabled(Boolean pastBookingEnabled) {
        this.pastBookingEnabled = pastBookingEnabled;
    }

    public Integer getPastBookingLimitDays() {
        return pastBookingLimitDays;
    }

    public void setPastBookingLimitDays(Integer pastBookingLimitDays) {
        this.pastBookingLimitDays = pastBookingLimitDays;
    }

    public Boolean getFutureBookingEnabled() {
        return futureBookingEnabled;
    }

    public void setFutureBookingEnabled(Boolean futureBookingEnabled) {
        this.futureBookingEnabled = futureBookingEnabled;
    }

    public Integer getFutureBookingLimitDays() {
        return futureBookingLimitDays;
    }

    public void setFutureBookingLimitDays(Integer futureBookingLimitDays) {
        this.futureBookingLimitDays = futureBookingLimitDays;
    }

    public Boolean getIncludeWeekendEnabled() {
        return includeWeekendEnabled;
    }

    public void setIncludeWeekendEnabled(Boolean includeWeekendEnabled) {
        this.includeWeekendEnabled = includeWeekendEnabled;
    }

    public Integer getIncludeWeekendMinDays() {
        return includeWeekendMinDays;
    }

    public void setIncludeWeekendMinDays(Integer includeWeekendMinDays) {
        this.includeWeekendMinDays = includeWeekendMinDays;
    }

    public Boolean getIncludeHolidayEnabled() {
        return includeHolidayEnabled;
    }

    public void setIncludeHolidayEnabled(Boolean includeHolidayEnabled) {
        this.includeHolidayEnabled = includeHolidayEnabled;
    }

    public Integer getIncludeHolidayMinDays() {
        return includeHolidayMinDays;
    }

    public void setIncludeHolidayMinDays(Integer includeHolidayMinDays) {
        this.includeHolidayMinDays = includeHolidayMinDays;
    }

    public Boolean getExceedBalanceEnabled() {
        return exceedBalanceEnabled;
    }

    public void setExceedBalanceEnabled(Boolean exceedBalanceEnabled) {
        this.exceedBalanceEnabled = exceedBalanceEnabled;
    }

    public String getExceedBalanceMode() {
        return exceedBalanceMode;
    }

    public void setExceedBalanceMode(String exceedBalanceMode) {
        this.exceedBalanceMode = exceedBalanceMode;
    }

    public String getEffectiveAfterPeriod() {
        return effectiveAfterPeriod;
    }

    public void setEffectiveAfterPeriod(String effectiveAfterPeriod) {
        this.effectiveAfterPeriod = effectiveAfterPeriod;
    }

    public Integer getEffectiveAfterUnits() {
        return effectiveAfterUnits;
    }

    public void setEffectiveAfterUnits(Integer effectiveAfterUnits) {
        this.effectiveAfterUnits = effectiveAfterUnits;
    }

    public Boolean getProRateEnabled() {
        return proRateEnabled;
    }

    public void setProRateEnabled(Boolean proRateEnabled) {
        this.proRateEnabled = proRateEnabled;
    }

    public Integer getMaxLeavePerApplication() {
        return maxLeavePerApplication;
    }

    public void setMaxLeavePerApplication(Integer maxLeavePerApplication) {
        this.maxLeavePerApplication = maxLeavePerApplication;
    }
}
