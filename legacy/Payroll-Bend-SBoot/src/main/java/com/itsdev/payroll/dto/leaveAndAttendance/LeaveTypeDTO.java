package com.itsdev.payroll.dto.leaveAndAttendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public class LeaveTypeDTO {

    private Long id;
    private String name;
    private String code;
    private String description;
    private String type;       // paid/unpaid
    private String unit;       // day_based/hour_based
    private String status;
    private Boolean allowHalfDay;

    // ---------- Eligibility ----------
    private Set<String> genders;
    private Set<String> departmentIds;     // ✅ changed to String
    private Set<String> designationIds;    // ✅ changed to String
    private Set<String> workLocationIds;   // ✅ changed to String

    private LocalDate validityFrom;
    private LocalDate validityTo;

    private AccrualConfigurationDTO accrualConfiguration;
    private ResetConfigurationDTO resetConfiguration;
    private PastBookingConfigurationDTO pastBookingConfiguration;
    private FutureBookingConfigurationDTO futureBookingConfiguration;
    private IncludeWeekendDTO includeWeekend;
    private IncludeHolidayDTO includeHoliday;
    private ExceedBalanceDTO exceedBalance;
    private EffectiveAfterConfigurationDTO effectiveAfterConfiguration;
    private ProRateConfigurationDTO proRateConfiguration;

    private Integer maxLeavePerApplication;

    // ---------- Nested DTOs ----------
    public static class AccrualConfigurationDTO {
        private Boolean enabled;
        private String frequency;
        private BigDecimal units;
        private String regularHours;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }

        public BigDecimal getUnits() { return units; }
        public void setUnits(BigDecimal units) { this.units = units; }

        public String getRegularHours() { return regularHours; }
        public void setRegularHours(String regularHours) { this.regularHours = regularHours; }
    }

    public static class ResetConfigurationDTO {
        private Boolean enabled;
        private String frequency;
        private CarryForwardConfigurationDTO carryForwardConfiguration;
        private EncashmentConfigurationDTO encashmentConfiguration;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }

        public CarryForwardConfigurationDTO getCarryForwardConfiguration() { return carryForwardConfiguration; }
        public void setCarryForwardConfiguration(CarryForwardConfigurationDTO carryForwardConfiguration) { this.carryForwardConfiguration = carryForwardConfiguration; }

        public EncashmentConfigurationDTO getEncashmentConfiguration() { return encashmentConfiguration; }
        public void setEncashmentConfiguration(EncashmentConfigurationDTO encashmentConfiguration) { this.encashmentConfiguration = encashmentConfiguration; }
    }

    public static class CarryForwardConfigurationDTO {
        private Boolean enabled;
        private BigDecimal units;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public BigDecimal getUnits() { return units; }
        public void setUnits(BigDecimal units) { this.units = units; }
    }

    public static class EncashmentConfigurationDTO {
        private Boolean enabled;
        private BigDecimal units;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public BigDecimal getUnits() { return units; }
        public void setUnits(BigDecimal units) { this.units = units; }
    }

    public static class PastBookingConfigurationDTO {
        private Boolean enabled;
        private Integer limitDays;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Integer getLimitDays() { return limitDays; }
        public void setLimitDays(Integer limitDays) { this.limitDays = limitDays; }
    }

    public static class FutureBookingConfigurationDTO {
        private Boolean enabled;
        private Integer limitDays;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Integer getLimitDays() { return limitDays; }
        public void setLimitDays(Integer limitDays) { this.limitDays = limitDays; }
    }

    public static class IncludeWeekendDTO {
        private Boolean enabled;
        private Integer minDays;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Integer getMinDays() { return minDays; }
        public void setMinDays(Integer minDays) { this.minDays = minDays; }
    }

    public static class IncludeHolidayDTO {
        private Boolean enabled;
        private Integer minDays;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Integer getMinDays() { return minDays; }
        public void setMinDays(Integer minDays) { this.minDays = minDays; }
    }

    public static class ExceedBalanceDTO {
        private Boolean enabled;
        private String mode;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    public static class EffectiveAfterConfigurationDTO {
        private String period;
        private Integer units;

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public Integer getUnits() { return units; }
        public void setUnits(Integer units) { this.units = units; }
    }

    public static class ProRateConfigurationDTO {
        private Boolean enabled;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    // ---------- Getters & Setters for main DTO ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getAllowHalfDay() { return allowHalfDay; }
    public void setAllowHalfDay(Boolean allowHalfDay) { this.allowHalfDay = allowHalfDay; }

    public LocalDate getValidityFrom() { return validityFrom; }
    public void setValidityFrom(LocalDate validityFrom) { this.validityFrom = validityFrom; }

    public LocalDate getValidityTo() { return validityTo; }
    public void setValidityTo(LocalDate validityTo) { this.validityTo = validityTo; }

    public AccrualConfigurationDTO getAccrualConfiguration() { return accrualConfiguration; }
    public void setAccrualConfiguration(AccrualConfigurationDTO accrualConfiguration) { this.accrualConfiguration = accrualConfiguration; }

    public ResetConfigurationDTO getResetConfiguration() { return resetConfiguration; }
    public void setResetConfiguration(ResetConfigurationDTO resetConfiguration) { this.resetConfiguration = resetConfiguration; }

    public PastBookingConfigurationDTO getPastBookingConfiguration() { return pastBookingConfiguration; }
    public void setPastBookingConfiguration(PastBookingConfigurationDTO pastBookingConfiguration) { this.pastBookingConfiguration = pastBookingConfiguration; }

    public FutureBookingConfigurationDTO getFutureBookingConfiguration() { return futureBookingConfiguration; }
    public void setFutureBookingConfiguration(FutureBookingConfigurationDTO futureBookingConfiguration) { this.futureBookingConfiguration = futureBookingConfiguration; }

    public IncludeWeekendDTO getIncludeWeekend() { return includeWeekend; }
    public void setIncludeWeekend(IncludeWeekendDTO includeWeekend) { this.includeWeekend = includeWeekend; }

    public IncludeHolidayDTO getIncludeHoliday() { return includeHoliday; }
    public void setIncludeHoliday(IncludeHolidayDTO includeHoliday) { this.includeHoliday = includeHoliday; }

    public ExceedBalanceDTO getExceedBalance() { return exceedBalance; }
    public void setExceedBalance(ExceedBalanceDTO exceedBalance) { this.exceedBalance = exceedBalance; }

    public EffectiveAfterConfigurationDTO getEffectiveAfterConfiguration() { return effectiveAfterConfiguration; }
    public void setEffectiveAfterConfiguration(EffectiveAfterConfigurationDTO effectiveAfterConfiguration) { this.effectiveAfterConfiguration = effectiveAfterConfiguration; }

    public ProRateConfigurationDTO getProRateConfiguration() { return proRateConfiguration; }
    public void setProRateConfiguration(ProRateConfigurationDTO proRateConfiguration) { this.proRateConfiguration = proRateConfiguration; }

    public Integer getMaxLeavePerApplication() { return maxLeavePerApplication; }
    public void setMaxLeavePerApplication(Integer maxLeavePerApplication) { this.maxLeavePerApplication = maxLeavePerApplication; }

    public Set<String> getGenders() { return genders; }
    public void setGenders(Set<String> genders) { this.genders = genders; }

    public Set<String> getDepartmentIds() { return departmentIds; }
    public void setDepartmentIds(Set<String> departmentIds) { this.departmentIds = departmentIds; }

    public Set<String> getDesignationIds() { return designationIds; }
    public void setDesignationIds(Set<String> designationIds) { this.designationIds = designationIds; }

    public Set<String> getWorkLocationIds() { return workLocationIds; }
    public void setWorkLocationIds(Set<String> workLocationIds) { this.workLocationIds = workLocationIds; }
}
