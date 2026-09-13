package com.itsdev.payroll.serviceimpl.leaveAndAttendance.attendance;

import com.itsdev.payroll.dto.leaveAndAttendance.attendance.AttendancePreferenceDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.attendance.AttendancePreference;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.leaveAndAttendance.attendance.AttendancePreferenceMapper;
import com.itsdev.payroll.repository.leaveAndAttendance.attendance.AttendancePreferenceRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.leaveAndAttendance.attendance.AttendancePreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttendancePreferenceServiceImpl implements AttendancePreferenceService {

    private final AttendancePreferenceRepository attendancePreferenceRepository;
    private final OrganizationRepository organizationRepository;

    public AttendancePreferenceServiceImpl(AttendancePreferenceRepository attendancePreferenceRepository,
                                           OrganizationRepository organizationRepository) {
        this.attendancePreferenceRepository = attendancePreferenceRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public AttendancePreferenceDTO createAttendancePreference(String orgId, AttendancePreferenceDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        AttendancePreference entity = AttendancePreferenceMapper.toEntity(dto);
        entity.setId(null); // let DB generate the ID
        entity.setOrganization(org);

        AttendancePreference saved = attendancePreferenceRepository.save(entity);
        return AttendancePreferenceMapper.toDTO(saved);
    }

    @Override
    public AttendancePreferenceDTO updateAttendancePreference(String orgId, Long attendancePreferenceId, AttendancePreferenceDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        AttendancePreference entity = attendancePreferenceRepository
                .findByIdAndOrganization(attendancePreferenceId, org)  // no need Long.valueOf
                .orElseThrow(() -> new RuntimeException("Attendance Preference not found"));

        AttendancePreferenceMapper.updateEntityFromDTO(dto, entity);

        AttendancePreference updated = attendancePreferenceRepository.save(entity);
        return AttendancePreferenceMapper.toDTO(updated);
    }


    @Override
    public void deleteAttendancePreference(String orgId, Long attendancePreferenceId) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        AttendancePreference entity = attendancePreferenceRepository
                .findByIdAndOrganization(attendancePreferenceId, org)
                .orElseThrow(() -> new RuntimeException("Attendance Preference not found"));

        attendancePreferenceRepository.delete(entity);
    }

    @Override
    public AttendancePreferenceDTO getAttendancePreference(String orgId, Long attendancePreferenceId) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        AttendancePreference entity = attendancePreferenceRepository
                .findByIdAndOrganization(attendancePreferenceId, org)
                .orElseThrow(() -> new RuntimeException("Attendance Preference not found"));

        return AttendancePreferenceMapper.toDTO(entity);
    }

    @Override
    public List<AttendancePreferenceDTO> getAllAttendancePreferences(String orgId) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        return attendancePreferenceRepository.findByOrganization(org).stream()
                .map(AttendancePreferenceMapper::toDTO)
                .collect(Collectors.toList());
    }
}
