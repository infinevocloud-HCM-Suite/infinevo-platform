package com.itsdev.payroll.serviceimpl.leaveAndAttendance.preferences;



import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesResponseDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.preferences.Preferences;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.PaySchedule;
import com.itsdev.payroll.entity.leaveAndAttedance.LeaveType;
import com.itsdev.payroll.mapper.leaveAndAttendance.preferences.PreferencesMapper;

import com.itsdev.payroll.repository.leaveAndAttendance.preferences.PreferencesRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.PayScheduleRepository;
import com.itsdev.payroll.repository.leaveAndAttendance.LeaveTypeRepository;
import com.itsdev.payroll.service.leaveAndAttendance.preferences.PreferencesService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PreferencesServiceImpl implements PreferencesService {

    private final PreferencesRepository preferencesRepository;
    private final OrganizationRepository organizationRepository;
    private final PayScheduleRepository payScheduleRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public PreferencesServiceImpl(
            PreferencesRepository preferencesRepository,
            OrganizationRepository organizationRepository,
            PayScheduleRepository payScheduleRepository,
            LeaveTypeRepository leaveTypeRepository
    ) {
        this.preferencesRepository = preferencesRepository;
        this.organizationRepository = organizationRepository;
        this.payScheduleRepository = payScheduleRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Override
    @Transactional
    public PreferencesDTO createPreferencesForOrg(String organizationId, PreferencesDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        if (preferencesRepository.existsByOrganization(org)) {
            throw new RuntimeException("Preferences already exist for this organization.");
        }

        Preferences entity = PreferencesMapper.toEntity(dto);
        entity.setOrganization(org);

        Preferences saved = preferencesRepository.save(entity);
        return PreferencesMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public PreferencesDTO updatePreferencesForOrg(String organizationId, String preferencesId, PreferencesDTO dto) {
        Preferences existing = preferencesRepository.findById(UUID.fromString(preferencesId))
                .orElseThrow(() -> new RuntimeException("Preferences not found"));

        PreferencesMapper.updateEntityFromDTO(dto, existing);
        Preferences saved = preferencesRepository.save(existing);

        return PreferencesMapper.toDTO(saved);
    }

    @Override
    public PreferencesResponseDTO getPreferencesForOrg(String organizationId, String preferencesId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Preferences preferences = preferencesRepository.findById(UUID.fromString(preferencesId))
                .orElseThrow(() -> new RuntimeException("Preferences not found"));

        // Fetch PaySchedule (if exists)
        Optional<PaySchedule> payScheduleOpt = payScheduleRepository.findByOrganization(org);

        // Fetch LeaveType (e.g., first leave type, or add logic as needed)
        Optional<LeaveType> leaveTypeOpt = leaveTypeRepository.findByOrganization(org).stream().findFirst();

        // Build response
        PreferencesResponseDTO response = new PreferencesResponseDTO();
        response.setEndDay(preferences.getEndDay());
        response.setPayrollReportDay(preferences.getPayrollReportDay());
        response.setLeaveEncashmentEnabled(preferences.getLeaveEncashmentEnabled());

        response.setPayScheduleType(payScheduleOpt.map(PaySchedule::getPayScheduleType).orElse("monthly"));
        response.setLeaveEncashmentSupported(leaveTypeOpt.map(LeaveType::getEncashmentEnabled).orElse(false));

        // Add defaults for other flags not yet in DB
        response.setLopDay("10"); // Example hardcoded / or fetch later
        response.setPayScheduleConfigured(true);
        response.setOvertimeAllowanceSupported(false);
        response.setOvertimeAllowanceEnabled(false);
        response.setPayScheduleTypeFormatted("Every month"); // derive from payScheduleType
        response.setLopSettingsConfigured(true);

        return response;
    }

    @Override
    public void deletePreferencesForOrg(String organizationId, String preferencesId) {
        preferencesRepository.deleteById(UUID.fromString(preferencesId));
    }

    @Override
    public PreferencesResponseDTO getPreferencesByOrganization(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Preferences preferences = preferencesRepository.findByOrganization(org)
                .orElseThrow(() -> new RuntimeException("Preferences not found for org"));

        return getPreferencesForOrg(organizationId, preferences.getId().toString());
    }
}
