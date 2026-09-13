package com.itsdev.payroll.mapper;

import com.itsdev.payroll.dto.PayScheduleDTO;
import com.itsdev.payroll.entity.PaySchedule;

public class PayScheduleMapper {

    public static PayScheduleDTO toDTO(PaySchedule entity) {
        PayScheduleDTO dto = new PayScheduleDTO();
        dto.setPayScheduleId(entity.getPayScheduleId());
        dto.setPayScheduleType(entity.getPayScheduleType());
        dto.setPayDay(entity.getPayDay());
        dto.setPayPeriodStartDate(entity.getPayPeriodStartDate());
        dto.setPayPeriodEndDate(entity.getPayPeriodEndDate());
        dto.setPayDate(entity.getPayDate());
        dto.setWorkingDays(entity.getWorkingDays());
        dto.setNoOfWorkingDays(entity.getNoOfWorkingDays());
        dto.setIncludeHolidays(entity.getIncludeHolidays());
        dto.setIncludeWeekends(entity.getIncludeWeekends());
        dto.setWorkingDaysCalculationType(entity.getWorkingDaysCalculationType());
        return dto;
    }

    public static PaySchedule toEntity(PayScheduleDTO dto) {
        PaySchedule entity = new PaySchedule();
        entity.setPayScheduleType(dto.getPayScheduleType() != null ? dto.getPayScheduleType() : "monthly");
        entity.setPayDay(dto.getPayDay());
        entity.setPayPeriodStartDate(dto.getPayPeriodStartDate());
        entity.setPayPeriodEndDate(dto.getPayPeriodEndDate());
        entity.setPayDate(dto.getPayDate());
        entity.setWorkingDays(dto.getWorkingDays());
        entity.setNoOfWorkingDays(dto.getNoOfWorkingDays());
        entity.setIncludeHolidays(dto.getIncludeHolidays());
        entity.setIncludeWeekends(dto.getIncludeWeekends());
        entity.setWorkingDaysCalculationType(dto.getWorkingDaysCalculationType());
        return entity;
    }
}
