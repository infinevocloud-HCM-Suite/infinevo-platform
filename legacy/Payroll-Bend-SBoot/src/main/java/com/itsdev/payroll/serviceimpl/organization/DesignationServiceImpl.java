package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.DesignationDTO;
import com.itsdev.payroll.entity.organization.Designation;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.organization.DesignationMapper;
import com.itsdev.payroll.repository.organization.DesignationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.organization.DesignationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DesignationServiceImpl implements DesignationService {

    private final DesignationRepository designationRepository;
    private final OrganizationRepository organizationRepository;

    public DesignationServiceImpl(DesignationRepository designationRepository, OrganizationRepository organizationRepository) {
        this.designationRepository = designationRepository;
        this.organizationRepository = organizationRepository;
    }

    private String generateDesignationId() {
        Random random = new Random();
        return String.format("%010d", random.nextInt(1_000_000_000));
    }

    @Override
    @Transactional
    public DesignationDTO createDesignation(String organizationId, DesignationDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Designation designation = new Designation();
        designation.setDesignationId(generateDesignationId());
        designation.setName(dto.getName());
        designation.setStatus(true);
        designation.setOrganization(org);

        Designation saved = designationRepository.save(designation);
        return DesignationMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public DesignationDTO updateDesignation(String organizationId, String designationId, DesignationDTO dto) {
        Designation designation = designationRepository.findByDesignationId(designationId)
                .orElseThrow(() -> new RuntimeException("Designation not found"));

        if (!designation.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Designation does not belong to this organization");
        }

        designation.setName(dto.getName());

        Designation saved = designationRepository.save(designation);
        return DesignationMapper.toDTO(saved);
    }

    @Override
    public DesignationDTO getDesignation(String organizationId, String designationId) {
        Designation designation = designationRepository.findByDesignationId(designationId)
                .orElseThrow(() -> new RuntimeException("Designation not found"));

        if (!designation.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Designation does not belong to this organization");
        }

        return DesignationMapper.toDTO(designation);
    }

    @Override
    public List<DesignationDTO> getAllDesignations(String organizationId) {
        List<Designation> designations = designationRepository.findByOrganization_OrganizationIdAndStatusTrue(organizationId);
        return designations.stream().map(DesignationMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDesignation(String organizationId, String designationId) {
        designationRepository.softDeleteByDesignationIdAndOrganizationId(designationId, organizationId);
    }

    @Override
    public void saveAll(String organizationId, List<DesignationDTO> designations) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        List<Designation> entities = designations.stream().map(dto -> {
            Designation des = new Designation();
            des.setDesignationId(generateDesignationId());
            des.setName(dto.getName());
            des.setOrganization(org);
            return des;
        }).toList();

        designationRepository.saveAll(entities);
    }
}
