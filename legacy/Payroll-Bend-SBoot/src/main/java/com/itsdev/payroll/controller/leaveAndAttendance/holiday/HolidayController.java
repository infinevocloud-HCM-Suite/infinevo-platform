package com.itsdev.payroll.controller.leaveAndAttendance.holiday;



import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayRequestDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayResponseDTO;
import com.itsdev.payroll.service.leaveAndAttendance.holiday.HolidayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    // Create a holiday
    @PostMapping
    public ResponseEntity<HolidayResponseDTO> createHoliday(
            @RequestHeader("organizationId") String orgId,
            @RequestBody HolidayRequestDTO dto) {
        return ResponseEntity.ok(holidayService.createHoliday(orgId, dto));
    }

    // Update a holiday
    @PutMapping("/{holidayId}")
    public ResponseEntity<HolidayResponseDTO> updateHoliday(
            @RequestHeader("organizationId") String orgId,
            @PathVariable String holidayId,
            @RequestBody HolidayRequestDTO dto) {
        return ResponseEntity.ok(holidayService.updateHoliday(orgId, holidayId, dto));
    }

    // Delete a holiday
    @DeleteMapping("/{holidayId}")
    public ResponseEntity<Void> deleteHoliday(
            @RequestHeader("organizationId") String orgId,
            @PathVariable String holidayId) {
        holidayService.deleteHoliday(orgId, holidayId);
        return ResponseEntity.noContent().build();
    }

    // Get a single holiday
    @GetMapping("/{holidayId}")
    public ResponseEntity<Map<String, Object>> getHoliday(
            @RequestHeader("organizationId") String orgId,
            @PathVariable String holidayId) {

        HolidayResponseDTO holiday = holidayService.getHoliday(orgId, holidayId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        resp.put("holiday", holiday);

        return ResponseEntity.ok(resp);
    }

    // Get all holidays
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllHolidays(
            @RequestHeader("organizationId") String orgId) {

        List<HolidayResponseDTO> holidays = holidayService.getAllHolidays(orgId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        resp.put("holidays", holidays);

        return ResponseEntity.ok(resp);
    }
}

