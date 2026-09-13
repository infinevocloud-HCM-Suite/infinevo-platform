package com.itsdev.payroll.service.leaveAndAttendance.holiday;



import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayRequestDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayResponseDTO;

import java.util.List;

public interface HolidayService {

    // Create a holiday for an organization
    HolidayResponseDTO createHoliday(String organizationId, HolidayRequestDTO dto);

    // Update a holiday by holidayId
    HolidayResponseDTO updateHoliday(String organizationId, String holidayId, HolidayRequestDTO dto);

    // Delete a holiday (soft delete)
    void deleteHoliday(String organizationId, String holidayId);

    // Get a single holiday
    HolidayResponseDTO getHoliday(String organizationId, String holidayId);

    // Get all holidays for an organization
    List<HolidayResponseDTO> getAllHolidays(String organizationId);

//    // Inactivate holiday (mark as inactive)
//    void inactivateHoliday(String organizationId, String holidayId);
//
//    // Reactivate holiday (mark as active)
//    void reactivateHoliday(String organizationId, String holidayId);
}

