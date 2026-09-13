package com.itsdev.payroll.serviceimpl.leaveAndAttendance;

import com.itsdev.payroll.dto.leaveAndAttendance.LeaveTypeDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.LeaveType;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.leaveAndAttendance.LeaveTypeMapper;
import com.itsdev.payroll.repository.leaveAndAttendance.LeaveTypeRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.leaveAndAttendance.LeaveTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LeaveTypeServiceImpl implements LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final OrganizationRepository organizationRepository;

    public LeaveTypeServiceImpl(LeaveTypeRepository leaveTypeRepository,
                                OrganizationRepository organizationRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.organizationRepository = organizationRepository;
    }

    // ===== Helper method for org fetch =====
    private Organization getOrganizationOrThrow(String organizationId) {
        return organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found with ID: " + organizationId));
    }

    // ===== Helper method to copy fields =====
    private void copyFieldsFromDtoToEntity(LeaveTypeDTO dto, LeaveType entity) {
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());
        entity.setType(dto.getType());
        entity.setUnit(dto.getUnit());
        entity.setStatus(Optional.ofNullable(dto.getStatus()).orElse("active"));
        entity.setValidityFrom(dto.getValidityFrom());
        entity.setValidityTo(dto.getValidityTo());
        entity.setAllowHalfDay(dto.getAllowHalfDay());
        entity.setMaxLeavePerApplication(dto.getMaxLeavePerApplication());

        // Accrual
        if (dto.getAccrualConfiguration() != null) {
            entity.setAccrualEnabled(dto.getAccrualConfiguration().getEnabled());
            entity.setAccrualFrequency(dto.getAccrualConfiguration().getFrequency());
            entity.setAccrualUnits(dto.getAccrualConfiguration().getUnits());
        }

        // Reset + carry forward + encashment
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

        // Past booking
        if (dto.getPastBookingConfiguration() != null) {
            entity.setPastBookingEnabled(dto.getPastBookingConfiguration().getEnabled());
            entity.setPastBookingLimitDays(dto.getPastBookingConfiguration().getLimitDays());
        }

        // Future booking
        if (dto.getFutureBookingConfiguration() != null) {
            entity.setFutureBookingEnabled(dto.getFutureBookingConfiguration().getEnabled());
            entity.setFutureBookingLimitDays(dto.getFutureBookingConfiguration().getLimitDays());
        }

        // Exceed balance
        if (dto.getExceedBalance() != null) {
            entity.setExceedBalanceEnabled(dto.getExceedBalance().getEnabled());
            entity.setExceedBalanceMode(dto.getExceedBalance().getMode());
        }

        // Weekend rules
        if (dto.getIncludeWeekend() != null) {
            entity.setIncludeWeekendEnabled(dto.getIncludeWeekend().getEnabled());
            entity.setIncludeWeekendMinDays(dto.getIncludeWeekend().getMinDays());
        }

        // Holiday rules
        if (dto.getIncludeHoliday() != null) {
            entity.setIncludeHolidayEnabled(dto.getIncludeHoliday().getEnabled());
            entity.setIncludeHolidayMinDays(dto.getIncludeHoliday().getMinDays());
        }

        // Effective after
        if (dto.getEffectiveAfterConfiguration() != null) {
            entity.setEffectiveAfterPeriod(dto.getEffectiveAfterConfiguration().getPeriod());
            entity.setEffectiveAfterUnits(dto.getEffectiveAfterConfiguration().getUnits());
        }

        // Pro-rate
        if (dto.getProRateConfiguration() != null) {
            entity.setProRateEnabled(dto.getProRateConfiguration().getEnabled());
        }
    }

    @Override
    @Transactional
    public LeaveTypeDTO createLeaveTypeForOrg(String organizationId, LeaveTypeDTO dto) {
        Organization org = getOrganizationOrThrow(organizationId);

        // Prevent duplicate leave codes inside same org
        if (leaveTypeRepository.existsByCodeAndOrganization(dto.getCode(), org)) {
            throw new RuntimeException("Leave type with code '" + dto.getCode() + "' already exists for this organization.");
        }

        LeaveType entity = LeaveTypeMapper.toEntity(dto);
        entity.setOrganization(org);
        entity.setStatus(Optional.ofNullable(dto.getStatus()).orElse("active"));

        LeaveType saved = leaveTypeRepository.save(entity);
        return LeaveTypeMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public LeaveTypeDTO updateLeaveTypeForOrg(String organizationId, Long leaveTypeId, LeaveTypeDTO dto) {
        Organization org = getOrganizationOrThrow(organizationId);

        LeaveType existing = leaveTypeRepository.findByIdAndOrganization(leaveTypeId, org)
                .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + leaveTypeId + " for organization " + organizationId));

        // Validate code uniqueness if changed
        if (!existing.getCode().equals(dto.getCode())
                && leaveTypeRepository.existsByCodeAndOrganization(dto.getCode(), org)) {
            throw new RuntimeException("Leave type with code '" + dto.getCode() + "' already exists for this organization.");
        }

        copyFieldsFromDtoToEntity(dto, existing);

        LeaveType updated = leaveTypeRepository.save(existing);
        return LeaveTypeMapper.toDTO(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveTypeDTO getLeaveTypeForOrg(String organizationId, Long leaveTypeId) {
        Organization org = getOrganizationOrThrow(organizationId);

        LeaveType leaveType = leaveTypeRepository.findByIdAndOrganization(leaveTypeId, org)
                .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + leaveTypeId + " for organization " + organizationId));

        return LeaveTypeMapper.toDTO(leaveType);
    }

    @Override
    @Transactional
    public void deleteLeaveTypeForOrg(String organizationId, Long leaveTypeId) {
        Organization org = getOrganizationOrThrow(organizationId);

        LeaveType leaveType = leaveTypeRepository.findByIdAndOrganization(leaveTypeId, org)
                .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + leaveTypeId + " for organization " + organizationId));

        leaveType.setStatus("inactive"); // soft delete
        leaveTypeRepository.save(leaveType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveTypeDTO> getAllLeaveTypesForOrg(String organizationId) {
        Organization org = getOrganizationOrThrow(organizationId);

        return leaveTypeRepository.findByOrganization(org).stream()
                .map(LeaveTypeMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveAll(String organizationId, List<LeaveTypeDTO> leaveTypes) {
        Organization org = getOrganizationOrThrow(organizationId);

        List<LeaveType> entities = leaveTypes.stream().map(dto -> {
            if (dto.getId() != null) {
                LeaveType existing = leaveTypeRepository.findByIdAndOrganization(dto.getId(), org)
                        .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + dto.getId() + " for organization " + organizationId));

                copyFieldsFromDtoToEntity(dto, existing);
                return existing;
            } else {
                LeaveType newEntity = LeaveTypeMapper.toEntity(dto);
                newEntity.setOrganization(org);
                newEntity.setStatus(Optional.ofNullable(dto.getStatus()).orElse("active"));
                return newEntity;
            }
        }).collect(Collectors.toList());

        leaveTypeRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public LeaveTypeDTO updateLeaveTypeStatus(String organizationId, Long leaveTypeId, String status) {
        Organization org = getOrganizationOrThrow(organizationId);

        LeaveType leaveType = leaveTypeRepository.findByIdAndOrganization(leaveTypeId, org)
                .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + leaveTypeId + " for organization " + organizationId));

        leaveType.setStatus(status);

        LeaveType updated = leaveTypeRepository.save(leaveType);
        return LeaveTypeMapper.toDTO(updated);
    }
}
