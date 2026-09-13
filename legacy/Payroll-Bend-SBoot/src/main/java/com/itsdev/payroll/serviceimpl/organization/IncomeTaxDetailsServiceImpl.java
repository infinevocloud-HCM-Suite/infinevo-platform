package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.IncomeTaxDetailsDTO;
import com.itsdev.payroll.entity.organization.IncomeTaxDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.organization.IncomeTaxDetailsMapper;
import com.itsdev.payroll.repository.organization.IncomeTaxDetailsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.organization.IncomeTaxDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncomeTaxDetailsServiceImpl implements IncomeTaxDetailsService {

    private final IncomeTaxDetailsRepository repository;
    private final OrganizationRepository organizationRepository;

    public IncomeTaxDetailsServiceImpl(IncomeTaxDetailsRepository repository, OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public IncomeTaxDetailsDTO update(String organizationId, IncomeTaxDetailsDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        IncomeTaxDetails entity = repository.findByOrganization(org)
                .orElseGet(() -> {
                    IncomeTaxDetails newEntity = new IncomeTaxDetails();
                    newEntity.setOrganization(org);
                    return newEntity;
                });

        entity.setTanNumber(dto.getTanNumber());
        entity.setPanNumber(dto.getPanNumber());
        entity.setTdsCircle(dto.getTdsCircle());
        entity.setAuthorizedPersonName(dto.getAuthorizedPersonName());
        entity.setAuthorizedPersonParent(dto.getAuthorizedPersonParent());
        entity.setAuthorizedPersonDesignation(dto.getAuthorizedPersonDesignation());
        entity.setDepositSchedule(dto.getDepositSchedule());
        entity.setEmployeeId(dto.getEmployeeId());

        IncomeTaxDetails saved = repository.save(entity);
        return IncomeTaxDetailsMapper.toDTO(saved);
    }

    @Override
    public IncomeTaxDetailsDTO get(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        return repository.findByOrganization(org)
                .map(IncomeTaxDetailsMapper::toDTO)
                .orElseGet(() -> {
                    // Return empty DTO so frontend gets field names
                    IncomeTaxDetailsDTO emptyDto = new IncomeTaxDetailsDTO();
                    emptyDto.setTanNumber("");
                    emptyDto.setPanNumber("");
                    emptyDto.setTdsCircle("");
                    emptyDto.setAuthorizedPersonName("");
                    emptyDto.setAuthorizedPersonParent("");
                    emptyDto.setAuthorizedPersonDesignation("");
                    emptyDto.setDepositSchedule("");
                    emptyDto.setEmployeeId("");
                    return emptyDto;
                });
    }
}