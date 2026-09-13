package com.itsdev.payroll.mapper.leaveAndAttendance.attendance;

import com.itsdev.payroll.dto.leaveAndAttendance.attendance.AttendancePreferenceDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.attendance.AttendancePreference;

public class AttendancePreferenceMapper {

    public static AttendancePreferenceDTO toDTO(AttendancePreference entity) {
        AttendancePreferenceDTO dto = new AttendancePreferenceDTO();
        dto.setAttendancePreferenceId(entity.getId() != null ? String.valueOf(entity.getId()) : null);
        dto.setCalculationOnFirstInLastOut(entity.isCalculationOnFirstInLastOut());
        dto.setMinimumHoursRequired(entity.isMinimumHoursRequired());
        dto.setMaximumHoursRequired(entity.isMaximumHoursRequired());
        dto.setCanIncludeHolidaysForPay(entity.isCanIncludeHolidaysForPay());
        dto.setCanIncludeLeavesForPay(entity.isCanIncludeLeavesForPay());
        dto.setCanIncludeWeekendsForPay(entity.isCanIncludeWeekendsForPay());
        dto.setFullDayMinimumHours(entity.getFullDayMinimumHours());
        dto.setFullDayMaximumHours(entity.getFullDayMaximumHours());
        dto.setHalfDayMinimumHours(entity.getHalfDayMinimumHours());
        dto.setHalfDayMaximumHours(entity.getHalfDayMaximumHours());
        dto.setMinimumHoursForOvertime(entity.getMinimumHoursForOvertime());

        if (entity.getRegularization() != null) {
            AttendancePreferenceDTO.RegularizationDTO regDTO = new AttendancePreferenceDTO.RegularizationDTO();
            regDTO.setCanCreateNewEntries(entity.getRegularization().isCanCreateNewEntries());
            regDTO.setAllowFutureRegularization(entity.getRegularization().isAllowFutureRegularization());
            regDTO.setMaximumRequestsAllowed(entity.getRegularization().getMaximumRequestsAllowed());
            regDTO.setPeriodType(entity.getRegularization().getPeriodType());
            regDTO.setRequestDaysBuffer(entity.getRegularization().getRequestDaysBuffer());
            dto.setRegularization(regDTO);
        }

        return dto;
    }

    public static AttendancePreference toEntity(AttendancePreferenceDTO dto) {
        AttendancePreference entity = new AttendancePreference();
        // ignore dto.getAttendancePreferenceId() (DB will handle ID)
        entity.setCalculationOnFirstInLastOut(dto.isCalculationOnFirstInLastOut());
        entity.setMinimumHoursRequired(dto.isMinimumHoursRequired());
        entity.setMaximumHoursRequired(dto.isMaximumHoursRequired());
        entity.setCanIncludeHolidaysForPay(dto.isCanIncludeHolidaysForPay());
        entity.setCanIncludeLeavesForPay(dto.isCanIncludeLeavesForPay());
        entity.setCanIncludeWeekendsForPay(dto.isCanIncludeWeekendsForPay());
        entity.setFullDayMinimumHours(dto.getFullDayMinimumHours());
        entity.setFullDayMaximumHours(dto.getFullDayMaximumHours());
        entity.setHalfDayMinimumHours(dto.getHalfDayMinimumHours());
        entity.setHalfDayMaximumHours(dto.getHalfDayMaximumHours());
        entity.setMinimumHoursForOvertime(dto.getMinimumHoursForOvertime());

        if (dto.getRegularization() != null) {
            AttendancePreference.Regularization reg = new AttendancePreference.Regularization();
            reg.setCanCreateNewEntries(dto.getRegularization().isCanCreateNewEntries());
            reg.setAllowFutureRegularization(dto.getRegularization().isAllowFutureRegularization());
            reg.setMaximumRequestsAllowed(dto.getRegularization().getMaximumRequestsAllowed());
            reg.setPeriodType(dto.getRegularization().getPeriodType());
            reg.setRequestDaysBuffer(dto.getRegularization().getRequestDaysBuffer());
            entity.setRegularization(reg);
        }

        return entity;
    }

    public static void updateEntityFromDTO(AttendancePreferenceDTO dto, AttendancePreference entity) {
        entity.setCalculationOnFirstInLastOut(dto.isCalculationOnFirstInLastOut());
        entity.setMinimumHoursRequired(dto.isMinimumHoursRequired());
        entity.setMaximumHoursRequired(dto.isMaximumHoursRequired());
        entity.setCanIncludeHolidaysForPay(dto.isCanIncludeHolidaysForPay());
        entity.setCanIncludeLeavesForPay(dto.isCanIncludeLeavesForPay());
        entity.setCanIncludeWeekendsForPay(dto.isCanIncludeWeekendsForPay());
        entity.setFullDayMinimumHours(dto.getFullDayMinimumHours());
        entity.setFullDayMaximumHours(dto.getFullDayMaximumHours());
        entity.setHalfDayMinimumHours(dto.getHalfDayMinimumHours());
        entity.setHalfDayMaximumHours(dto.getHalfDayMaximumHours());
        entity.setMinimumHoursForOvertime(dto.getMinimumHoursForOvertime());

        if (dto.getRegularization() != null) {
            AttendancePreference.Regularization reg = new AttendancePreference.Regularization();
            reg.setCanCreateNewEntries(dto.getRegularization().isCanCreateNewEntries());
            reg.setAllowFutureRegularization(dto.getRegularization().isAllowFutureRegularization());
            reg.setMaximumRequestsAllowed(dto.getRegularization().getMaximumRequestsAllowed());
            reg.setPeriodType(dto.getRegularization().getPeriodType());
            reg.setRequestDaysBuffer(dto.getRegularization().getRequestDaysBuffer());
            entity.setRegularization(reg);
        } else {
            entity.setRegularization(null);
        }
    }
}
