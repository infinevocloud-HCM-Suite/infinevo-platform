package com.itsdev.payroll.serviceimpl.leaveAndAttendance.onboarding;

import com.itsdev.payroll.dto.leaveAndAttendance.onboarding.OnboardingStatusDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.onboarding.OnboardingStatus;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.leaveAndAttendance.onboarding.OnboardingStatusMapper;
import com.itsdev.payroll.repository.leaveAndAttendance.onboarding.OnboardingStatusRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.leaveAndAttendance.onboarding.OnboardingStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class OnboardingStatusServiceImpl implements OnboardingStatusService {

    private final OnboardingStatusRepository onboardingStatusRepository;
    private final OrganizationRepository organizationRepository;

    public OnboardingStatusServiceImpl(OnboardingStatusRepository onboardingStatusRepository,
                                       OrganizationRepository organizationRepository) {
        this.onboardingStatusRepository = onboardingStatusRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public OnboardingStatusDTO getOrCreateByOrganizationId(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        Optional<OnboardingStatus> opt = onboardingStatusRepository.findByOrganization(org);

        OnboardingStatus entity;
        if (opt.isPresent()) {
            entity = opt.get();
        } else {
            entity = new OnboardingStatus();
            entity.setOrganization(org);
            onboardingStatusRepository.save(entity);
        }

        return OnboardingStatusMapper.toDTO(entity);
    }

    @Override
    public OnboardingStatusDTO getByOrganizationId(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        return onboardingStatusRepository.findByOrganization(org)
                .map(OnboardingStatusMapper::toDTO)
                .orElse(null);
    }

    @Override
    public OnboardingStatusDTO updateByOrganizationId(String organizationId, OnboardingStatusDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        OnboardingStatus entity = onboardingStatusRepository.findByOrganization(org)
                .orElseGet(() -> {
                    OnboardingStatus newEntity = new OnboardingStatus();
                    newEntity.setOrganization(org);
                    return newEntity;
                });

        OnboardingStatusMapper.updateEntityFromDTO(dto, entity);

        OnboardingStatus saved = onboardingStatusRepository.save(entity);
        return OnboardingStatusMapper.toDTO(saved);
    }

    @Override
    public boolean isAllCompleted(String organizationId) {
        OnboardingStatusDTO dto = getByOrganizationId(organizationId);
        if (dto == null) return false;

        return dto.isOrganizationDetailsCompleted() &&
                dto.isLeaveSetupCompleted() &&
                dto.isHolidaySetupCompleted() &&
                dto.isAttendanceSetupCompleted() &&
                dto.isPreferencesSetupCompleted();
    }
}
