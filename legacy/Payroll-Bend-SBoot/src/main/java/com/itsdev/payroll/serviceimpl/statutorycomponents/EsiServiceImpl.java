package com.itsdev.payroll.serviceimpl.statutorycomponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.statutorycomponents.EsiDTO;
import com.itsdev.payroll.entity.statutorycomponents.Esi;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.statutorycomponents.EsiMapper;
import com.itsdev.payroll.repository.statutorycomponents.EsiRepository;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.statutorycomponents.EsiService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EsiServiceImpl implements EsiService {

    private final EsiRepository esiRepository;
    private final OrganizationRepository organizationRepository;
    private final MasterConfigRepository masterConfigRepository;
    private final ObjectMapper objectMapper;

    public EsiServiceImpl(EsiRepository esiRepository,
                          OrganizationRepository organizationRepository,
                          MasterConfigRepository masterConfigRepository,
                          ObjectMapper objectMapper) {
        this.esiRepository = esiRepository;
        this.organizationRepository = organizationRepository;
        this.masterConfigRepository = masterConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public EsiDTO getEsiByOrganizationId(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Optional<Esi> esiOpt = esiRepository.findByOrganization(org);
        if (esiOpt.isPresent() && Boolean.TRUE.equals(esiOpt.get().getIsActive())) {
            return EsiMapper.toDto(esiOpt.get());
        }

        // Fetch default from master config
        MasterConfig master = masterConfigRepository.findByComponentName("ESI")
                .orElseThrow(() -> new RuntimeException("ESI master config not found"));

        try {
            JsonNode node = master.getConfigData();
            return objectMapper.treeToValue(node, EsiDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing ESI master config", e);
        }
    }

    @Override
    public EsiDTO saveOrUpdateEsi(String organizationId, EsiDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Esi esi = esiRepository.findByOrganization(org)
                .map(existing -> {
                    // Update existing entity
                    existing.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
                    existing.setEmployeeContribution(dto.getEmployeeContribution());
                    existing.setEmployerContribution(dto.getEmployerContribution());
                    existing.setRegistrationNumber(dto.getRegistrationNumber());
                    existing.setRegistrationDate(dto.getRegistrationDate());
                    existing.setCanEnableEmployerEsiInCtc(dto.getCanEnableEmployerEsiInCtc());
                    existing.setIsIncludedInSalaryStructure(dto.getIsIncludedInSalaryStructure());
                    existing.setDeductionCycle(dto.getDeductionCycle());
                    existing.setDeductionCycleFormatted(dto.getDeductionCycleFormatted());
                    existing.setRegistrationDateFormatted(dto.getRegistrationDateFormatted());
                    existing.setIsIncludedInCtc(dto.getIsIncludedInCtc());
                    existing.setName(dto.getName());
                    existing.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
                    return existing;
                })
                .orElseGet(() -> {
                    Esi newEsi = EsiMapper.toEntity(dto);
                    newEsi.setOrganization(org);
                    newEsi.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
                    return newEsi;
                });

        Esi saved = esiRepository.save(esi);
        return EsiMapper.toDto(saved);
    }

    @Override
    public EsiDTO disableEsi(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Esi esi = esiRepository.findByOrganization(org)
                .orElseThrow(() -> new RuntimeException("ESI not found for organization"));

        esi.setIsActive(false); // disable
        Esi saved = esiRepository.save(esi);

        return EsiMapper.toDto(saved);
    }
}