package com.itsdev.payroll.serviceimpl.leaveAndAttendance.holiday;



import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayRequestDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.holiday.HolidayResponseDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.holiday.Holiday;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.leaveAndAttendance.holiday.HolidayMapper;
import com.itsdev.payroll.repository.leaveAndAttendance.holiday.HolidayRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;


import com.itsdev.payroll.service.leaveAndAttendance.holiday.HolidayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Transactional
public class HolidayServiceImpl implements HolidayService {

    private final HolidayRepository holidayRepository;
    private final OrganizationRepository organizationRepository;

    public HolidayServiceImpl(HolidayRepository holidayRepository,
                              OrganizationRepository organizationRepository) {
        this.holidayRepository = holidayRepository;
        this.organizationRepository = organizationRepository;
    }

    private String generateHolidayId() {
        return String.valueOf(1000000000L + new Random().nextInt(900000000));
    }

    @Override
    public HolidayResponseDTO createHoliday(String organizationId, HolidayRequestDTO dto) {
        // 1. Find organization by orgId
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Map DTO to Entity
        Holiday holiday = HolidayMapper.toEntity(dto, org);
        holiday.setHolidayId(generateHolidayId()); // Generate unique Holiday ID

        // 3. Save holiday
        Holiday savedHoliday = holidayRepository.save(holiday);

        // 4. Convert saved entity to ResponseDTO
        return HolidayMapper.toResponse(savedHoliday);


    }

    @Override
    public HolidayResponseDTO updateHoliday(String organizationId, String holidayId, HolidayRequestDTO dto) {
        // 1. Fetch Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch existing Holiday by holidayId & organization
        Holiday existingHoliday = holidayRepository.findByHolidayIdAndOrganization(holidayId, org)
                .orElseThrow(() -> new RuntimeException("Holiday not found"));

        // 3. Update fields
        existingHoliday.setName(dto.getName());
        existingHoliday.setFromDate(dto.getFromDate());
        existingHoliday.setToDate(dto.getToDate());
        existingHoliday.setDescription(dto.getDescription());
        existingHoliday.setRestrictedHoliday(dto.isRestrictedHoliday());
        existingHoliday.setLocations(dto.getLocations()); // for multiple locations

        // 4. Save updated holiday
        Holiday updatedHoliday = holidayRepository.save(existingHoliday);

        // 5. Convert entity to ResponseDTO
        return HolidayMapper.toResponse(updatedHoliday);
    }


    @Override
    public void deleteHoliday(String organizationId, String holidayId) {
        // 1. Fetch Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch existing Holiday by holidayId & organization
        Holiday holiday = holidayRepository.findByHolidayIdAndOrganization(holidayId, org)
                .orElseThrow(() -> new RuntimeException("Holiday not found"));

        // 3. Delete the holiday from DB
        holidayRepository.delete(holiday);
    }


    @Override
    public HolidayResponseDTO getHoliday(String organizationId, String holidayId) {
        // 1. Fetch Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch Holiday by holidayId & organization
        Holiday holiday = holidayRepository.findByHolidayIdAndOrganization(holidayId, org)
                .orElseThrow(() -> new RuntimeException("Holiday not found"));

        // 3. Convert Entity → ResponseDTO using mapper
        return HolidayMapper.toResponse(holiday);
    }

    @Override
    public List<HolidayResponseDTO> getAllHolidays(String organizationId) {
        // 1. Fetch Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch all holidays for organization
        List<Holiday> holidays = holidayRepository.findByOrganization(org);

        // 3. Convert List<Entity> → List<ResponseDTO>
        return holidays.stream()
                .map(HolidayMapper::toResponse)
                .collect(Collectors.toList());
    }



}

