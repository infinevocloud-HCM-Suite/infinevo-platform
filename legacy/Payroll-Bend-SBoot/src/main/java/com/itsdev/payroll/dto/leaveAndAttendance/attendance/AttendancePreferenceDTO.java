package com.itsdev.payroll.dto.leaveAndAttendance.attendance;

public class AttendancePreferenceDTO {

    private String attendancePreferenceId;

    private boolean calculationOnFirstInLastOut;
    private boolean minimumHoursRequired;
    private boolean maximumHoursRequired;

    private boolean canIncludeHolidaysForPay;
    private boolean canIncludeLeavesForPay;
    private boolean canIncludeWeekendsForPay;

    private String fullDayMinimumHours;   // e.g. "08:00"
    private String fullDayMaximumHours;   // e.g. "10:00"
    private String halfDayMinimumHours;   // e.g. "04:00"
    private String halfDayMaximumHours;   // e.g. "08:00"

    private String minimumHoursForOvertime;

    private RegularizationDTO regularization;



    private boolean deleted = false;



    // Nested DTO for Regularization
    public static class RegularizationDTO {
        private boolean canCreateNewEntries;
        private boolean allowFutureRegularization;
        private String maximumRequestsAllowed;

        private String periodType;
        private String requestDaysBuffer;

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
    }

    public String getAttendancePreferenceId() {
        return attendancePreferenceId;
    }

    public void setAttendancePreferenceId(String attendancePreferenceId) {
        this.attendancePreferenceId = attendancePreferenceId;
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

    public RegularizationDTO getRegularization() {
        return regularization;
    }

    public void setRegularization(RegularizationDTO regularization) {
        this.regularization = regularization;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
