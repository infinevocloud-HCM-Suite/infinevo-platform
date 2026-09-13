package com.phegondev.usersmanagementsystem.controller;
import com.phegondev.usersmanagementsystem.entity.Holiday;
import com.phegondev.usersmanagementsystem.service.HolidayService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class HolidayController {
    
    @Autowired
    private HolidayService holidayService;
    
    @GetMapping("/holidays")
    public List<Holiday> getAllHolidays() {
        return holidayService.getAllHolidays();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Holiday> getHolidayById(@PathVariable Long id) {
        Holiday holiday = holidayService.getHolidayById(id);
        if (holiday != null) {
            return ResponseEntity.ok(holiday);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/holidays/add")
    public Holiday createHoliday(@RequestBody Holiday holiday) {
        return holidayService.createHoliday(holiday);
    }
    
    @PutMapping("/holidays/{id}")
    public ResponseEntity<Holiday> updateHoliday(@PathVariable Long id, @RequestBody Holiday holidayDetails) {
        Holiday updatedHoliday = holidayService.updateHoliday(id, holidayDetails);
        if (updatedHoliday != null) {
            return ResponseEntity.ok(updatedHoliday);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/holidays/search")
    public List<Holiday> searchHolidays(@RequestParam String query) {
        return holidayService.searchHolidays(query);
    }
    
    @GetMapping("/holidays/upcoming")
    public List<Holiday> getUpcomingHolidays() {
        return holidayService.getUpcomingHolidays();
    }
}