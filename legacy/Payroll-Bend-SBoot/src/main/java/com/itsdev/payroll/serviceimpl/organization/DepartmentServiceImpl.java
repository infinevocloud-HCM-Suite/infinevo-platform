package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.DepartmentDTO;
import com.itsdev.payroll.entity.organization.Department;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.organization.DepartmentMapper;
import com.itsdev.payroll.repository.organization.DepartmentRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.organization.DepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final OrganizationRepository organizationRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository, OrganizationRepository organizationRepository) {
        this.departmentRepository = departmentRepository;
        this.organizationRepository = organizationRepository;
    }

    private String generateDepartmentId() {
        Random random = new Random();
        return String.format("%010d", random.nextInt(1_000_000_000));
    }

    @Override
    @Transactional
    public DepartmentDTO createDepartment(String organizationId, DepartmentDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Department department = new Department();
        department.setDepartmentId(generateDepartmentId());
        department.setName(dto.getName());
        department.setDescription(dto.getDescription());
        department.setDepartmentCode(dto.getDepartmentCode());
        department.setStatus(true);
        department.setOrganization(org);

        Department saved = departmentRepository.save(department);
        return DepartmentMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public DepartmentDTO updateDepartment(String organizationId, String departmentId, DepartmentDTO dto) {
        Department department = departmentRepository.findByDepartmentId(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        if (!department.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Department does not belong to this organization");
        }

        department.setName(dto.getName());
        department.setDescription(dto.getDescription());
        department.setDepartmentCode(dto.getDepartmentCode());

        Department saved = departmentRepository.save(department);
        return DepartmentMapper.toDTO(saved);
    }

    @Override
    public DepartmentDTO getDepartment(String organizationId, String departmentId) {
        Department department = departmentRepository.findByDepartmentId(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        if (!department.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Department does not belong to this organization");
        }

        return DepartmentMapper.toDTO(department);
    }

    @Override
    public List<DepartmentDTO> getAllDepartments(String organizationId) {
        List<Department> departments = departmentRepository.findByOrganization_OrganizationIdAndStatusTrue(organizationId);
        return departments.stream().map(DepartmentMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDepartment(String organizationId, String departmentId) {
        departmentRepository.softDeleteByDepartmentIdAndOrganizationId(departmentId, organizationId);
    }

    @Override
    public void saveAll(String organizationId, List<DepartmentDTO> departments) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        List<Department> entities = departments.stream().map(dto -> {
            Department dep = new Department();
            dep.setDepartmentId(generateDepartmentId());
            dep.setName(dto.getName());
            dep.setDescription(dto.getDescription());
            dep.setDepartmentCode(dto.getDepartmentCode());
            dep.setOrganization(org);
            return dep;
        }).toList();

        departmentRepository.saveAll(entities);
    }
}