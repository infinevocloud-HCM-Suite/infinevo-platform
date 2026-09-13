package com.itsdev.payroll.entity.leaveAndAttedance.attendance;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "attendance_preferences")
public class AttendancePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    @Column(name = "calculation_on_firstin_lastout", nullable = false)
    private boolean calculationOnFirstInLastOut;

    @Column(name = "is_minimum_hours_required", nullable = false)
    private boolean minimumHoursRequired;

    @Column(name = "is_maximum_hours_required", nullable = false)
    private boolean maximumHoursRequired;

    @Column(name = "can_include_holidays_for_pay", nullable = false)
    private boolean canIncludeHolidaysForPay;

    @Column(name = "can_include_leaves_for_pay", nullable = false)
    private boolean canIncludeLeavesForPay;

    @Column(name = "can_include_weekends_for_pay", nullable = false)
    private boolean canIncludeWeekendsForPay;

    @Column(name = "full_day_minimum_hours")
    private String fullDayMinimumHours;

    @Column(name = "full_day_maximum_hours")
    private String fullDayMaximumHours;

    @Column(name = "half_day_minimum_hours")
    private String halfDayMinimumHours;

    @Column(name = "half_day_maximum_hours")
    private String halfDayMaximumHours;

    @Column(name = "minimum_hours_for_overtime")
    private String minimumHoursForOvertime;

    @Embedded
    private Regularization regularization;



    @Embeddable
    public static class Regularization {

        @Column(name = "can_create_new_entries", nullable = false)
        private boolean canCreateNewEntries;

        @Column(name = "allow_future_regularization", nullable = false)
        private boolean allowFutureRegularization;

        @Column(name = "maximum_requests_allowed")
        private String maximumRequestsAllowed;

        @Column(name = "period_type")
        private String periodType;

        @Column(name = "request_days_buffer")
        private String requestDaysBuffer;

        public boolean isCanCreateNewEntries() {
            return canCreateNewEntries;
        }

        public void setCanCreateNewEntries(boolean canCreateNewEntries) {
            this.canCreateNewEntries = canCreateNewEntries;
        }

        public boolean isAllowFutureRegularization() {
            return allowFutureRegularization;
        }

        public void setAllowFutureRegularization(boolean allowFutureRegularization) {
            this.allowFutureRegularization = allowFutureRegularization;
        }

        public String getMaximumRequestsAllowed() {
            return maximumRequestsAllowed;
        }

        public void setMaximumRequestsAllowed(String maximumRequestsAllowed) {
            this.maximumRequestsAllowed = maximumRequestsAllowed;
        }

        public String getPeriodType() {
            return periodType;
        }

        public void setPeriodType(String periodType) {
            this.periodType = periodType;
        }

        public String getRequestDaysBuffer() {
            return requestDaysBuffer;
        }

        public void setRequestDaysBuffer(String requestDaysBuffer) {
            this.requestDaysBuffer = requestDaysBuffer;
        }
    }

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

    public boolean isCalculationOnFirstInLastOut() {
        return calculationOnFirstInLastOut;
    }

    public void setCalculationOnFirstInLastOut(boolean calculationOnFirstInLastOut) {
        this.calculationOnFirstInLastOut = calculationOnFirstInLastOut;
    }

    public boolean isMinimumHoursRequired() {
        return minimumHoursRequired;
    }

    public void setMinimumHoursRequired(boolean minimumHoursRequired) {
        this.minimumHoursRequired = minimumHoursRequired;
    }

    public boolean isMaximumHoursRequired() {
        return maximumHoursRequired;
    }

    public void setMaximumHoursRequired(boolean maximumHoursRequired) {
        this.maximumHoursRequired = maximumHoursRequired;
    }

    public boolean isCanIncludeHolidaysForPay() {
        return canIncludeHolidaysForPay;
    }

    public void setCanIncludeHolidaysForPay(boolean canIncludeHolidaysForPay) {
        this.canIncludeHolidaysForPay = canIncludeHolidaysForPay;
    }

    public boolean isCanIncludeLeavesForPay() {
        return canIncludeLeavesForPay;
    }

    public void setCanIncludeLeavesForPay(boolean canIncludeLeavesForPay) {
        this.canIncludeLeavesForPay = canIncludeLeavesForPay;
    }

    public boolean isCanIncludeWeekendsForPay() {
        return canIncludeWeekendsForPay;
    }

    public void setCanIncludeWeekendsForPay(boolean canIncludeWeekendsForPay) {
        this.canIncludeWeekendsForPay = canIncludeWeekendsForPay;
    }

    public String getFullDayMinimumHours() {
        return fullDayMinimumHours;
    }

    public void setFullDayMinimumHours(String fullDayMinimumHours) {
        this.fullDayMinimumHours = fullDayMinimumHours;
    }

    public String getFullDayMaximumHours() {
        return fullDayMaximumHours;
    }

    public void setFullDayMaximumHours(String fullDayMaximumHours) {
        this.fullDayMaximumHours = fullDayMaximumHours;
    }

    public String getHalfDayMinimumHours() {
        return halfDayMinimumHours;
    }

    public void setHalfDayMinimumHours(String halfDayMinimumHours) {
        this.halfDayMinimumHours = halfDayMinimumHours;
    }

    public String getHalfDayMaximumHours() {
        return halfDayMaximumHours;
    }

    public void setHalfDayMaximumHours(String halfDayMaximumHours) {
        this.halfDayMaximumHours = halfDayMaximumHours;
    }

    public String getMinimumHoursForOvertime() {
        return minimumHoursForOvertime;
    }

    public void setMinimumHoursForOvertime(String minimumHoursForOvertime) {
        this.minimumHoursForOvertime = minimumHoursForOvertime;
    }

    public Regularization getRegularization() {
        return regularization;
    }

    public void setRegularization(Regularization regularization) {
        this.regularization = regularization;
    }

}
