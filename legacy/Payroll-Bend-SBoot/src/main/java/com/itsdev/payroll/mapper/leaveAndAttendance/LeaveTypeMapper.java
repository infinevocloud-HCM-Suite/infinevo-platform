package com.itsdev.payroll.mapper.leaveAndAttendance;

import com.itsdev.payroll.dto.leaveAndAttendance.LeaveTypeDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.LeaveType;
import com.itsdev.payroll.entity.organization.Department;
import com.itsdev.payroll.entity.organization.Designation;
import com.itsdev.payroll.entity.organization.WorkLocation;

import java.util.Set;
import java.util.stream.Collectors;

public class LeaveTypeMapper {

    // ---------------- Entity -> DTO ----------------
    public static LeaveTypeDTO toDTO(LeaveType entity) {
        if (entity == null) return null;

        LeaveTypeDTO dto = new LeaveTypeDTO();

        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setDescription(entity.getDescription());
        dto.setType(entity.getType());
        dto.setUnit(entity.getUnit());
        dto.setStatus(entity.getStatus());
        dto.setAllowHalfDay(entity.getAllowHalfDay());
        dto.setValidityFrom(entity.getValidityFrom());
        dto.setValidityTo(entity.getValidityTo());
        dto.setMaxLeavePerApplication(entity.getMaxLeavePerApplication());

        // ----- Eligibility -----
        dto.setGenders(entity.getGenders());

        if (entity.getDepartments() != null) {
            dto.setDepartmentIds(
                    entity.getDepartments().stream()
                            .map(Department::getDepartmentId) // ✅ use business key
                            .collect(Collectors.toSet())
            );
        }

        if (entity.getDesignations() != null) {
            dto.setDesignationIds(
                    entity.getDesignations().stream()
                            .map(Designation::getDesignationId) // ✅ use business key
                            .collect(Collectors.toSet())
            );
        }

        if (entity.getWorkLocations() != null) {
            dto.setWorkLocationIds(
                    entity.getWorkLocations().stream()
                            .map(WorkLocation::getWorkLocationId) // ✅ use business key
                            .collect(Collectors.toSet())
            );
        }

        // ----- Accrual -----
        LeaveTypeDTO.AccrualConfigurationDTO accrual = new LeaveTypeDTO.AccrualConfigurationDTO();
        accrual.setEnabled(entity.getAccrualEnabled());
        accrual.setFrequency(entity.getAccrualFrequency());
        accrual.setUnits(entity.getAccrualUnits());
        accrual.setRegularHours(entity.getAccrualRegularHours());
        dto.setAccrualConfiguration(accrual);

        // ----- Reset -----
        LeaveTypeDTO.ResetConfigurationDTO reset = new LeaveTypeDTO.ResetConfigurationDTO();
        reset.setEnabled(entity.getResetEnabled());
        reset.setFrequency(entity.getResetFrequency());

        LeaveTypeDTO.CarryForwardConfigurationDTO cf = new LeaveTypeDTO.CarryForwardConfigurationDTO();
        cf.setEnabled(entity.getCarryForwardEnabled());
        cf.setUnits(entity.getCarryForwardUnits());
        reset.setCarryForwardConfiguration(cf);

        LeaveTypeDTO.EncashmentConfigurationDTO encash = new LeaveTypeDTO.EncashmentConfigurationDTO();
        encash.setEnabled(entity.getEncashmentEnabled());
        encash.setUnits(entity.getEncashmentUnits());
        reset.setEncashmentConfiguration(encash);

        dto.setResetConfiguration(reset);

        // ----- Past booking -----
        LeaveTypeDTO.PastBookingConfigurationDTO past = new LeaveTypeDTO.PastBookingConfigurationDTO();
        past.setEnabled(entity.getPastBookingEnabled());
        past.setLimitDays(entity.getPastBookingLimitDays());
        dto.setPastBookingConfiguration(past);

        // ----- Future booking -----
        LeaveTypeDTO.FutureBookingConfigurationDTO future = new LeaveTypeDTO.FutureBookingConfigurationDTO();
        future.setEnabled(entity.getFutureBookingEnabled());
        future.setLimitDays(entity.getFutureBookingLimitDays());
        dto.setFutureBookingConfiguration(future);

        // ----- Include weekend -----
        LeaveTypeDTO.IncludeWeekendDTO weekend = new LeaveTypeDTO.IncludeWeekendDTO();
        weekend.setEnabled(entity.getIncludeWeekendEnabled());
        weekend.setMinDays(entity.getIncludeWeekendMinDays());
        dto.setIncludeWeekend(weekend);

        // ----- Include holiday -----
        LeaveTypeDTO.IncludeHolidayDTO holiday = new LeaveTypeDTO.IncludeHolidayDTO();
        holiday.setEnabled(entity.getIncludeHolidayEnabled());
        holiday.setMinDays(entity.getIncludeHolidayMinDays());
        dto.setIncludeHoliday(holiday);

        // ----- Exceed balance -----
        LeaveTypeDTO.ExceedBalanceDTO exceed = new LeaveTypeDTO.ExceedBalanceDTO();
        exceed.setEnabled(entity.getExceedBalanceEnabled());
        exceed.setMode(entity.getExceedBalanceMode());
        dto.setExceedBalance(exceed);

        // ----- Effective after -----
        LeaveTypeDTO.EffectiveAfterConfigurationDTO effective = new LeaveTypeDTO.EffectiveAfterConfigurationDTO();
        effective.setPeriod(entity.getEffectiveAfterPeriod());
        effective.setUnits(entity.getEffectiveAfterUnits());
        dto.setEffectiveAfterConfiguration(effective);

        // ----- Pro-rate -----
        LeaveTypeDTO.ProRateConfigurationDTO proRate = new LeaveTypeDTO.ProRateConfigurationDTO();
        proRate.setEnabled(entity.getProRateEnabled());
        dto.setProRateConfiguration(proRate);

        return dto;
    }

    // ---------------- DTO -> Entity ----------------
    public static LeaveType toEntity(LeaveTypeDTO dto) {
        if (dto == null) return null;

        LeaveType entity = new LeaveType();

        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());
        entity.setType(dto.getType());
        entity.setUnit(dto.getUnit());
        entity.setStatus(dto.getStatus());
        entity.setAllowHalfDay(dto.getAllowHalfDay());
        entity.setValidityFrom(dto.getValidityFrom());
        entity.setValidityTo(dto.getValidityTo());
        entity.setMaxLeavePerApplication(dto.getMaxLeavePerApplication());

        // ----- Eligibility -----
        entity.setGenders(dto.getGenders());

        // ⚠️ Map using business keys
        if (dto.getDepartmentIds() != null) {
            Set<Department> depts = dto.getDepartmentIds().stream()
                    .map(businessId -> {
                        Department d = new Department();
                        d.setDepartmentId(businessId); // ✅ set business key
                        return d;
                    })
                    .collect(Collectors.toSet());
            entity.setDepartments(depts);
        }

        if (dto.getDesignationIds() != null) {
            Set<Designation> desigs = dto.getDesignationIds().stream()
                    .map(businessId -> {
                        Designation d = new Designation();
                        d.setDesignationId(businessId); // ✅ set business key
                        return d;
                    })
                    .collect(Collectors.toSet());
            entity.setDesignations(desigs);
        }

        if (dto.getWorkLocationIds() != null) {
            Set<WorkLocation> locs = dto.getWorkLocationIds().stream()
                    .map(businessId -> {
                        WorkLocation wl = new WorkLocation();
                        wl.setWorkLocationId(businessId); // ✅ set business key
                        return wl;
                    })
                    .collect(Collectors.toSet());
            entity.setWorkLocations(locs);
        }

        // ----- Accrual -----
        if (dto.getAccrualConfiguration() != null) {
            entity.setAccrualEnabled(dto.getAccrualConfiguration().getEnabled());
            entity.setAccrualFrequency(dto.getAccrualConfiguration().getFrequency());
            entity.setAccrualUnits(dto.getAccrualConfiguration().getUnits());
            entity.setAccrualRegularHours(dto.getAccrualConfiguration().getRegularHours());
        }

        // ----- Reset -----
        if (dto.getResetConfiguration() != null) {
            entity.setResetEnabled(dto.getResetConfiguration().getEnabled());
            entity.setResetFrequency(dto.getResetConfiguration().getFrequency());

            if (dto.getResetConfiguration().getCarryForwardConfiguration() != null) {
                entity.setCarryForwardEnabled(dto.getResetConfiguration().getCarryForwardConfiguration().getEnabled());
                entity.setCarryForwardUnits(dto.getResetConfiguration().getCarryForwardConfiguration().getUnits());
            }

            if (dto.getResetConfiguration().getEncashmentConfiguration() != null) {
                entity.setEncashmentEnabled(dto.getResetConfiguration().getEncashmentConfiguration().getEnabled());
                entity.setEncashmentUnits(dto.getResetConfiguration().getEncashmentConfiguration().getUnits());
            }
        }

        // ----- Past booking -----
        if (dto.getPastBookingConfiguration() != null) {
            entity.setPastBookingEnabled(dto.getPastBookingConfiguration().getEnabled());
            entity.setPastBookingLimitDays(dto.getPastBookingConfiguration().getLimitDays());
        }

        // ----- Future booking -----
        if (dto.getFutureBookingConfiguration() != null) {
            entity.setFutureBookingEnabled(dto.getFutureBookingConfiguration().getEnabled());
            entity.setFutureBookingLimitDays(dto.getFutureBookingConfiguration().getLimitDays());
        }

        // ----- Include weekend -----
        if (dto.getIncludeWeekend() != null) {
            entity.setIncludeWeekendEnabled(dto.getIncludeWeekend().getEnabled());
            entity.setIncludeWeekendMinDays(dto.getIncludeWeekend().getMinDays());
        }

        // ----- Include holiday -----
        if (dto.getIncludeHoliday() != null) {
            entity.setIncludeHolidayEnabled(dto.getIncludeHoliday().getEnabled());
            entity.setIncludeHolidayMinDays(dto.getIncludeHoliday().getMinDays());
        }

        // ----- Exceed balance -----
        if (dto.getExceedBalance() != null) {
            entity.setExceedBalanceEnabled(dto.getExceedBalance().getEnabled());
            entity.setExceedBalanceMode(dto.getExceedBalance().getMode());
        }

        // ----- Effective after -----
        if (dto.getEffectiveAfterConfiguration() != null) {
            entity.setEffectiveAfterPeriod(dto.getEffectiveAfterConfiguration().getPeriod());
            entity.setEffectiveAfterUnits(dto.getEffectiveAfterConfiguration().getUnits());
        }

        // ----- Pro-rate -----
        if (dto.getProRateConfiguration() != null) {
            entity.setProRateEnabled(dto.getProRateConfiguration().getEnabled());
        }

        return entity;
    }
}
