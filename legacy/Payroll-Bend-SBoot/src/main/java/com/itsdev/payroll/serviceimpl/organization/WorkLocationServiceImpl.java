package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.WorkLocationDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.WorkLocation;
import com.itsdev.payroll.mapper.organization.WorkLocationMapper;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.organization.WorkLocationRepository;
import com.itsdev.payroll.service.organization.WorkLocationService;
import com.itsdev.payroll.service.statutorycomponents.ProfessionalTaxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class WorkLocationServiceImpl implements WorkLocationService {

    @Autowired
    private WorkLocationRepository workLocationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProfessionalTaxService professionalTaxService;

    private String generateUniqueWorkLocationId() {
        String id;
        do {
            id = String.format("%07d", new Random().nextInt(9000000) + 1000000); // 7-digit
        } while (workLocationRepository.existsByWorkLocationId(id));
        return id;
    }

    private Organization findOrgByOrganizationIdOrThrow(String organizationId) {
        return organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
    }

    @Override
    @Transactional
    public WorkLocationDTO createWorkLocationForOrg(String organizationId, WorkLocationDTO dto) {

        Organization org = findOrgByOrganizationIdOrThrow(organizationId);

        // Map DTO to entity
        WorkLocation wl = new WorkLocation();
        wl.setWorkLocationName(dto.getWorkLocationName());
        wl.setStreetAddress1(dto.getStreetAddress1());
        wl.setStreetAddress2(dto.getStreetAddress2());
        wl.setCity(dto.getCity());
        wl.setState(dto.getState());
        wl.setZipCode(dto.getZipCode());
        wl.setCountry(dto.getCountry());
        wl.setIsFilingAddress(dto.getIsFilingAddress());

        // Generate unique Work Location ID
        wl.setWorkLocationId(generateUniqueWorkLocationId());

        // Set the organization reference
        wl.setOrganization(org);

        // Save and return DTO
        WorkLocation saved = workLocationRepository.save(wl);

        // Auto create ProfessionalTax for this org based on state
       // professionalTaxService.createDefaultTax(org, saved.getState());

        return WorkLocationMapper.toDTO(saved);
    }


    @Override
    @Transactional
    public WorkLocationDTO updateWorkLocationForOrg(String organizationId, String workLocationId, WorkLocationDTO dto) {
        Organization org = findOrgByOrganizationIdOrThrow(organizationId);
        WorkLocation existing = workLocationRepository.findByWorkLocationId(workLocationId)
                .orElseThrow(() -> new RuntimeException("WorkLocation not found"));

        if (!existing.getOrganization().getId().equals(org.getId())) {
            throw new RuntimeException("WorkLocation does not belong to the organization");
        }

        existing.setWorkLocationName(dto.getWorkLocationName());
        existing.setStreetAddress1(dto.getStreetAddress1());
        existing.setStreetAddress2(dto.getStreetAddress2());
        existing.setCity(dto.getCity());
        existing.setState(dto.getState());
        existing.setZipCode(dto.getZipCode());
        existing.setCountry(dto.getCountry());
        existing.setIsFilingAddress(dto.getIsFilingAddress());

        WorkLocation saved = workLocationRepository.save(existing);
        return WorkLocationMapper.toDTO(saved);
    }

    @Override
    public WorkLocationDTO getWorkLocationForOrg(String organizationId, String workLocationId) {
        Organization org = findOrgByOrganizationIdOrThrow(organizationId);
        WorkLocation wl = workLocationRepository.findByWorkLocationId(workLocationId)
                .orElseThrow(() -> new RuntimeException("WorkLocation not found"));
        if (!wl.getOrganization().getId().equals(org.getId())) {
            throw new RuntimeException("WorkLocation does not belong to the organization");
        }
        return WorkLocationMapper.toDTO(wl);
    }

    @Override
    public List<WorkLocationDTO> getAllWorkLocationsForOrg(String organizationId) {
        Organization org = findOrgByOrganizationIdOrThrow(organizationId);
        List<WorkLocation> list = workLocationRepository.findByStatusTrue().stream()
                .filter(w -> w.getOrganization().getId().equals(org.getId()))
                .collect(Collectors.toList());
        return list.stream().map(WorkLocationMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteWorkLocationForOrg(String organizationId, String workLocationId) {
        workLocationRepository.softDeleteByWorkLocationIdAndOrganizationId(workLocationId, organizationId);
    }

    @Override
    public void saveAll(String organizationId, List<WorkLocationDTO> workLocations) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        List<WorkLocation> entities = workLocations.stream().map(dto -> {
            WorkLocation wl = new WorkLocation();
            wl.setWorkLocationId(generateUniqueWorkLocationId());
            wl.setWorkLocationName(dto.getWorkLocationName());
            wl.setStreetAddress1(dto.getStreetAddress1());
            wl.setStreetAddress2(dto.getStreetAddress2());
            wl.setCity(dto.getCity());
            wl.setState(dto.getState());
            wl.setZipCode(dto.getZipCode());
            wl.setCountry(dto.getCountry());
            wl.setIsFilingAddress(dto.getIsFilingAddress());
            wl.setOrganization(org);
            return wl;
        }).toList();

        workLocationRepository.saveAll(entities);
    }

}
