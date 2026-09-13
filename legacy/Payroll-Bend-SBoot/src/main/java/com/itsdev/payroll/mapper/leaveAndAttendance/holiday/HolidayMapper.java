package com.itsdev.payroll.mapper.leaveAndAttendance.holiday;

import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayRequestDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayResponseDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.holiday.Holiday;
import com.itsdev.payroll.entity.organization.Organization;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class HolidayMapper {

    private static final DateTimeFormatter DB_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // RequestDTO → Entity
    public static Holiday toEntity(HolidayRequestDTO dto, Organization organization) {
        Holiday holiday = new Holiday();
        holiday.setName(dto.getName());
        holiday.setFromDate(dto.getFromDate());
        holiday.setToDate(dto.getToDate());
        holiday.setDescription(dto.getDescription());
        holiday.setRestrictedHoliday(dto.isRestrictedHoliday());
        holiday.setOrganization(organization);

        // Store locations (multiple)
        if (dto.getLocations() != null && !dto.getLocations().isEmpty()) {
            holiday.setLocations(dto.getLocations());
        }

        return holiday;
    }

    // Entity → ResponseDTO
    public static HolidayResponseDTO toResponse(Holiday holiday) {
        HolidayResponseDTO response = new HolidayResponseDTO();
        response.setHolidayId(holiday.getHolidayId());
        response.setName(holiday.getName());

        // Convert LocalDate → String (yyyy-MM-dd & dd/MM/yyyy)
        if (holiday.getFromDate() != null) {
            response.setFromDate(holiday.getFromDate().format(DB_FORMATTER));
            response.setFromDateFormatted(holiday.getFromDate().format(DISPLAY_FORMATTER));
        }
        if (holiday.getToDate() != null) {
            response.setToDate(holiday.getToDate().format(DB_FORMATTER));
            response.setToDateFormatted(holiday.getToDate().format(DISPLAY_FORMATTER));
        }

        response.setDescription(holiday.getDescription());
        response.setRestrictedHoliday(holiday.isRestrictedHoliday());
        response.setCanEdit(true); // Hardcoded for now as per your response payload

        // Locations mapping
        response.setLocations(holiday.getLocations() != null
                ? holiday.getLocations().stream().collect(Collectors.toSet())
                : null);

        return response;
    }
}
