package com.phegondev.usersmanagementsystem.service;
import com.phegondev.usersmanagementsystem.entity.Holiday;
import com.phegondev.usersmanagementsystem.repository.HolidayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
public class HolidayService {
    
    @Autowired
    private HolidayRepository holidayRepository;
    
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }
    
    public Holiday getHolidayById(Long id) {
        return holidayRepository.findById(id).orElse(null);
    }
    
    public Holiday createHoliday(Holiday holiday) {
        // Calculate day name from date
        DayOfWeek dayOfWeek = holiday.getDate().getDayOfWeek();
        String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        holiday.setDay(dayName);
        
        return holidayRepository.save(holiday);
    }
    
    public Holiday updateHoliday(Long id, Holiday holidayDetails) {
        Holiday holiday = holidayRepository.findById(id).orElse(null);
        if (holiday != null) {
            holiday.setName(holidayDetails.getName());
            holiday.setDate(holidayDetails.getDate());
            
            // Recalculate day name
            DayOfWeek dayOfWeek = holiday.getDate().getDayOfWeek();
            String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            holiday.setDay(dayName);
            
            return holidayRepository.save(holiday);
        }
        return null;
    }
    
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }
    
    public List<Holiday> searchHolidays(String query) {
        return holidayRepository.findByNameContainingIgnoreCase(query);
    }
    
    public List<Holiday> getUpcomingHolidays() {
        LocalDate today = LocalDate.now();
        LocalDate endOfYear = LocalDate.of(today.getYear(), 12, 31);
        return holidayRepository.findByDateBetween(today, endOfYear);
    }
}