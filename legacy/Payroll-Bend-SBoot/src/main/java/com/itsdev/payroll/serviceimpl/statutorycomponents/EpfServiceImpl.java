package com.itsdev.payroll.serviceimpl.statutorycomponents;


import com.fasterxml.jackson.databind.JsonNode;
import com.itsdev.payroll.dto.statutorycomponents.EpfDTO;
import com.itsdev.payroll.entity.statutorycomponents.Epf;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.statutorycomponents.EpfMapper;
import com.itsdev.payroll.repository.statutorycomponents.EpfRepository;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.statutorycomponents.EpfService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class EpfServiceImpl implements EpfService {

    private final EpfRepository epfRepository;
    private final OrganizationRepository organizationRepository;
    private final MasterConfigRepository masterConfigRepository;
    private final ObjectMapper objectMapper;

    public EpfServiceImpl(EpfRepository epfRepository,
                          OrganizationRepository organizationRepository,
                          MasterConfigRepository masterConfigRepository,
                          ObjectMapper objectMapper) {
        this.epfRepository = epfRepository;
        this.organizationRepository = organizationRepository;
        this.masterConfigRepository = masterConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public EpfDTO getEpfByOrganizationId(String organizationId) {
        // Fetch the organization
        Organization organization = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Try to fetch active EPF for the organization
        Optional<Epf> epfOpt = epfRepository.findByOrganization(organization);
        if (epfOpt.isPresent() && Boolean.TRUE.equals(epfOpt.get().getIsActive())) {
            return EpfMapper.toDto(epfOpt.get());
        }

        // If no active EPF exists, fetch default EPF from master config
        MasterConfig master = masterConfigRepository.findByComponentName("EPF")
                .orElseThrow(() -> new RuntimeException("EPF master config not found"));

        try {
            JsonNode node = master.getConfigData();
            return objectMapper.treeToValue(node, EpfDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing EPF master config", e);
        }
    }

    @Override
    public EpfDTO createOrUpdateEpf(String organizationId, EpfDTO dto) {
        Organization organization = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Get existing EPF for the organization or create a new one
        Epf epf = epfRepository.findByOrganization(organization)
                .orElseGet(() -> {
                    Epf newEpf = new Epf();
                    newEpf.setOrganization(organization);
                    newEpf.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
                    return newEpf;
                });

        // Map DTO values to the EPF entity individually
        epf.setIsAdminChargesIncludedCtc(dto.getIsAdminChargesIncludedCtc());
        epf.setIsEdliIncludedCtc(dto.getIsEdliIncludedCtc());
        epf.setConsiderEarnedSalaryForEpf(dto.getConsiderEarnedSalaryForEpf());
        epf.setRegistrationNumber(dto.getRegistrationNumber());
        epf.setEpfAdminChargesEmployerContribution(dto.getEpfAdminChargesEmployerContribution());
        epf.setIsEmployerContributionIncludedCtc(dto.getIsEmployerContributionIncludedCtc());
        epf.setIsEmployeeRestrictedBasicEnabled(dto.getIsEmployeeRestrictedBasicEnabled());
        epf.setIsEmployerContributionIncludedSalaryStructure(dto.getIsEmployerContributionIncludedSalaryStructure());
        epf.setIsEligibleForAbryScheme(dto.getIsEligibleForAbryScheme());
        epf.setEdliEmployerContribution(dto.getEdliEmployerContribution());
        epf.setEpfEmployeeContribution(dto.getEpfEmployeeContribution());
        epf.setCanEnableEdliPfAdminChargesInSalaryStructure(dto.getCanEnableEdliPfAdminChargesInSalaryStructure());
        epf.setCanProRateRestrictedBasic(dto.getCanProRateRestrictedBasic());
        epf.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        epf.setEpsSeniorCategoryAge(dto.getEpsSeniorCategoryAge());

        epf.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        epf.setCanOverrideRestrictedBasic(dto.getCanOverrideRestrictedBasic());
        epf.setEpsEmployeeContribution(dto.getEpsEmployeeContribution());
        epf.setEpfAdminChargesEmployeeContribution(dto.getEpfAdminChargesEmployeeContribution());
        epf.setIsEdliIncludedSalaryStructure(dto.getIsEdliIncludedSalaryStructure());
        epf.setIsAdminChargesIncludedSalaryStructure(dto.getIsAdminChargesIncludedSalaryStructure());
        epf.setEpsEmployerContribution(dto.getEpsEmployerContribution());
        epf.setDeductionCycleFormatted(dto.getDeductionCycleFormatted());
        epf.setIsSubsidyApplicableForBothContributions(dto.getIsSubsidyApplicableForBothContributions());
        epf.setRegistrationDate(dto.getRegistrationDate());
        epf.setRegistrationDateFormatted(dto.getRegistrationDateFormatted());
        epf.setEpsEmployerContributionForSeniorcategory(dto.getEpsEmployerContributionForSeniorcategory());
        epf.setName(dto.getName());
        epf.setIsEmployerRestrictedBasicEnabled(dto.getIsEmployerRestrictedBasicEnabled());
        epf.setEpfEmployerContribution(dto.getEpfEmployerContribution());
        epf.setCanEnableEdliPfAdminChargesInCtc(dto.getCanEnableEdliPfAdminChargesInCtc());
        epf.setDeductionCycle(dto.getDeductionCycle());
        epf.setEpfEmployerContributionForSeniorcategory(dto.getEpfEmployerContributionForSeniorcategory());
        epf.setEdliEmployeeContribution(dto.getEdliEmployeeContribution());

        // Save updated entity
        Epf saved = epfRepository.save(epf);

        return EpfMapper.toDto(saved);
    }

    @Override
    public EpfDTO disableEpf(String organizationId) {
        Organization organization = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Epf epf = epfRepository.findByOrganization(organization)
                .orElseThrow(() -> new RuntimeException("EPF not found for organization"));

        epf.setIsActive(false); // disable
        Epf saved = epfRepository.save(epf);

        return EpfMapper.toDto(saved);
    }
}