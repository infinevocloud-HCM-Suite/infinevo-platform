package com.itsdev.payroll.serviceimpl.leaveAndAttendance.leaveImport;


import com.itsdev.payroll.dto.leaveAndAttendance.leaveImport.EmployeeLeaveImportDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.leaveImport.EmployeeLeaveImport;
import com.itsdev.payroll.entity.organization.Organization;

import com.itsdev.payroll.mapper.leaveAndAttendance.leaveImport.EmployeeLeaveImportMapper;
import com.itsdev.payroll.repository.leaveAndAttendance.leaveImport.EmployeeLeaveImportRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.leaveAndAttendance.leaveImport.EmployeeLeaveImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeLeaveImportServiceImpl implements EmployeeLeaveImportService {

    private final EmployeeLeaveImportRepository leaveImportRepository;
    private final OrganizationRepository organizationRepository;

    public EmployeeLeaveImportServiceImpl(EmployeeLeaveImportRepository leaveImportRepository,
                                          OrganizationRepository organizationRepository) {
        this.leaveImportRepository = leaveImportRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional
    public EmployeeLeaveImportDTO createLeaveImport(String organizationId, EmployeeLeaveImportDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        EmployeeLeaveImport entity = EmployeeLeaveImportMapper.toEntity(dto, org);
        EmployeeLeaveImport saved = leaveImportRepository.save(entity);
        return EmployeeLeaveImportMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public EmployeeLeaveImportDTO updateLeaveImport(String organizationId, Long id, EmployeeLeaveImportDTO dto) {
        EmployeeLeaveImport entity = leaveImportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave Import not found"));

        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Leave Import does not belong to this organization");
        }

        entity.setCount(dto.getCount());
        entity.setDate(dto.getDate());
        entity.setEmployeeNumber(dto.getEmployeeNumber());
        entity.setLeaveType(dto.getLeaveType());

        EmployeeLeaveImport updated = leaveImportRepository.save(entity);
        return EmployeeLeaveImportMapper.toDTO(updated);
    }

    @Override
    public EmployeeLeaveImportDTO getLeaveImport(String organizationId, Long id) {
        EmployeeLeaveImport entity = leaveImportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave Import not found"));

        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Leave Import does not belong to this organization");
        }

        return EmployeeLeaveImportMapper.toDTO(entity);
    }

    @Override
    public List<EmployeeLeaveImportDTO> getAllLeaveImports(String organizationId) {
        List<EmployeeLeaveImport> imports = leaveImportRepository.findByOrganization_OrganizationId(organizationId);
        return imports.stream().map(EmployeeLeaveImportMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteLeaveImport(String organizationId, Long id) {
        EmployeeLeaveImport entity = leaveImportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave Import not found"));

        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Leave Import does not belong to this organization");
        }

        leaveImportRepository.delete(entity);
    }

    @Override
    @Transactional
    public void saveAll(String organizationId, List<EmployeeLeaveImportDTO> leaveImports) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        List<EmployeeLeaveImport> entities = leaveImports.stream()
                .map(dto -> EmployeeLeaveImportMapper.toEntity(dto, org))
                .toList();

        leaveImportRepository.saveAll(entities);
    }
}

