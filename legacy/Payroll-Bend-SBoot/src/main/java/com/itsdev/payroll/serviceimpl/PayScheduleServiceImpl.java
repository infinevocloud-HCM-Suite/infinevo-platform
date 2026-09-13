package com.itsdev.payroll.serviceimpl;

import com.itsdev.payroll.dto.PayScheduleDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.PaySchedule;
import com.itsdev.payroll.mapper.PayScheduleMapper;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.PayScheduleRepository;
import com.itsdev.payroll.service.PayScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Random;

@Service
@Transactional
public class PayScheduleServiceImpl implements PayScheduleService {

    private final PayScheduleRepository payScheduleRepository;
    private final OrganizationRepository organizationRepository;

    public PayScheduleServiceImpl(PayScheduleRepository payScheduleRepository, OrganizationRepository organizationRepository) {
        this.payScheduleRepository = payScheduleRepository;
        this.organizationRepository = organizationRepository;
    }

    private String generateUniqueId() {
        Random random = new Random();
        String id;
        do {
            long number = 1_000_000_000L + (long) (random.nextDouble() * 9_000_000_000L);
            id = String.valueOf(number);
        } while (payScheduleRepository.findByPayScheduleId(id).isPresent());
        return id;
    }

    @Override
    public PayScheduleDTO create(String organizationId, PayScheduleDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        if (payScheduleRepository.findByOrganization(org).isPresent()) {
            throw new RuntimeException("Pay schedule already exists for this organization");
        }

        PaySchedule entity = new PaySchedule();

        // Generate and set unique ID
        entity.setPayScheduleId(generateUniqueId());

        // Set default or provided payScheduleType
        entity.setPayScheduleType(dto.getPayScheduleType() != null ? dto.getPayScheduleType() : "monthly");

        // Map all other fields individually
        entity.setPayDay(dto.getPayDay());
        entity.setPayPeriodStartDate(dto.getPayPeriodStartDate());
        entity.setPayPeriodEndDate(dto.getPayPeriodEndDate());
        entity.setPayDate(dto.getPayDate());
        entity.setWorkingDays(dto.getWorkingDays());
        entity.setNoOfWorkingDays(dto.getNoOfWorkingDays());
        entity.setIncludeHolidays(true);
        entity.setIncludeWeekends(true);
        entity.setWorkingDaysCalculationType(dto.getWorkingDaysCalculationType());

        // Link to organization
        entity.setOrganization(org);

        // Save and return DTO
        PaySchedule saved = payScheduleRepository.save(entity);
        return PayScheduleMapper.toDTO(saved);
    }


    @Override
    public PayScheduleDTO getByOrganizationId(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        PaySchedule entity = payScheduleRepository.findByOrganization(org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pay schedule not found"));

        return PayScheduleMapper.toDTO(entity);
    }

    @Override
    public PayScheduleDTO update(String organizationId, String payScheduleId, PayScheduleDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        PaySchedule entity = payScheduleRepository.findByOrganizationAndPayScheduleId(org, payScheduleId)
                .orElseThrow(() -> new RuntimeException("Pay schedule not found for given ID"));

        // Individual field mapping
        entity.setPayScheduleType(dto.getPayScheduleType() != null ? dto.getPayScheduleType() : entity.getPayScheduleType());
        entity.setPayDay(dto.getPayDay());
        entity.setPayPeriodStartDate(dto.getPayPeriodStartDate());
        entity.setPayPeriodEndDate(dto.getPayPeriodEndDate());
        entity.setPayDate(dto.getPayDate());
        entity.setWorkingDays(dto.getWorkingDays());
        entity.setNoOfWorkingDays(dto.getNoOfWorkingDays());
        entity.setIncludeHolidays(dto.getIncludeHolidays());
        entity.setIncludeWeekends(dto.getIncludeWeekends());
        entity.setWorkingDaysCalculationType(dto.getWorkingDaysCalculationType());

        return PayScheduleMapper.toDTO(payScheduleRepository.save(entity));
    }
}
