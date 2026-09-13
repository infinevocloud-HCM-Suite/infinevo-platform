package com.itsdev.payroll.serviceimpl.salarycomponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.itsdev.payroll.dto.salarycomponents.BenefitDTO;
import com.itsdev.payroll.entity.salarycomponents.Benefit;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.salarycomponents.BenefitMapper;
import com.itsdev.payroll.repository.salarycomponents.BenefitRepository;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.salarycomponents.BenefitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Transactional
public class BenefitServiceImpl implements BenefitService {

    private final BenefitRepository benefitRepository;
    private final MasterConfigRepository masterConfigRepository;
    private final OrganizationRepository organizationRepository;

    public BenefitServiceImpl(BenefitRepository benefitRepository,
                              MasterConfigRepository masterConfigRepository,
                              OrganizationRepository organizationRepository) {
        this.benefitRepository = benefitRepository;
        this.masterConfigRepository = masterConfigRepository;
        this.organizationRepository = organizationRepository;
    }

    private String generateBenefitId() {
        return String.valueOf(1000000000L + new Random().nextInt(900000000));
    }

    @Override
    public BenefitDTO createBenefit(String orgId, BenefitDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Benefit entity = BenefitMapper.toEntity(dto);
        entity.setBenefitId(generateBenefitId());
        entity.setOrganization(org);

        return BenefitMapper.toDTO(benefitRepository.save(entity));
    }

    @Override
    public BenefitDTO updateBenefit(String orgId, String benefitId, BenefitDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Benefit entity = benefitRepository.findByBenefitIdAndOrganization(benefitId, org)
                .orElseThrow(() -> new RuntimeException("Benefit not found"));

        // update fields
        entity.setBenefitName(dto.getBenefitName());
        entity.setBenefitPlan(dto.getBenefitPlan());
        entity.setBenefitPlanNameFormatted(dto.getBenefitPlanNameFormatted());
        entity.setBenefitCategory(dto.getBenefitCategory());
        entity.setPreTax(dto.isPreTax());
        entity.setProRata(dto.isProRata());
        entity.setSuperannuationBenefit(dto.isSuperannuationBenefit());
        entity.setIncludedInCtc(dto.isIncludedInCtc());
        entity.setIncludedInSalaryStructure(dto.isIncludedInSalaryStructure());
        entity.setStatus(dto.getStatus());
        entity.setTaxExemptionSubType(dto.getTaxExemptionSubType());
        entity.setTaxExemptionSubTypeFormatted(dto.getTaxExemptionSubTypeFormatted());
        entity.setTaxExemptSection(dto.getTaxExemptSection());
        entity.setCanAllowEmployerContribution(dto.isCanAllowEmployerContribution());
        entity.setCanAllowEmployeeContribution(dto.isCanAllowEmployeeContribution());

        return BenefitMapper.toDTO(benefitRepository.save(entity));
    }

    @Override
    public BenefitDTO getBenefit(String orgId, String benefitId) {
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        Benefit entity = benefitRepository.findByBenefitIdAndOrganization(benefitId, org)
                .orElseThrow(() -> new RuntimeException("Benefit not found"));
        return BenefitMapper.toDTO(entity);
    }

    @Override
    public List<BenefitDTO> getAllBenefits(String organizationId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch benefits for this org (not deleted)
        List<Benefit> benefits = benefitRepository.findByOrganizationAndIsDeletedFalse(org);

        // 3. If no benefits → copy from MasterConfig
        if (benefits.isEmpty()) {
            MasterConfig master = masterConfigRepository.findByComponentName("benefits")
                    .orElseThrow(() -> new RuntimeException("Master config for benefits not found"));

            JsonNode benefitsJson = master.getConfigData();
            if (benefitsJson.isArray()) {
                List<Benefit> defaults = new ArrayList<>();

                for (JsonNode node : benefitsJson) {
                    Benefit b = new Benefit();
                    b.setBenefitId(generateBenefitId()); // 🔑 Auto-generate 10 digit
                    b.setOrganization(org);

                    // Map JSON → Entity (only required fields)
                    b.setBenefitName(node.path("benefitName").asText(null));
                    b.setBenefitPlan(node.path("benefitPlan").asText(null));
                    b.setBenefitPlanNameFormatted(node.path("benefitPlanNameFormatted").asText(null));
                    b.setBenefitCategory(node.path("benefitCategory").asText(null));
                    b.setPreTax(node.path("isPreTax").asBoolean(false));
                    b.setEmployeeCount(node.path("employeeCount").asInt(0));
                    b.setStatus(node.path("status").asText(null));
                    b.setOneTime(node.path("isOneTime").asBoolean(false));
                    b.setUserConfigurable(node.path("isUserConfigurable").asBoolean(false));
                    b.setProRata(node.path("isProRata").asBoolean(false));
                    b.setSuperannuationBenefit(node.path("isSuperannuationBenefit").asBoolean(false));
                    b.setIncludedInCtc(node.path("isIncludedInCtc").asBoolean(false));
                    b.setIncludedInSalaryStructure(node.path("isIncludedInSalaryStructure").asBoolean(false));
                    b.setTaxExemptionSubType(node.path("taxExemptionSubType").asText(null));
                    b.setTaxExemptionSubTypeFormatted(node.path("taxExemptionSubTypeFormatted").asText(null));
                    b.setTaxExemptSection(node.path("taxExemptSection").asText(null));
                    b.setCanAllowEmployerContribution(node.path("canAllowEmployerContribution").asBoolean(false));
                    b.setCanAllowEmployeeContribution(node.path("canAllowEmployeeContribution").asBoolean(false));

                    b.setIsDeleted(false); // default

                    defaults.add(b);
                }

                // Save defaults
                benefits = benefitRepository.saveAll(defaults);
            }
        }

        // 4. Convert entities → DTOs
        return benefits.stream()
                .map(BenefitMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteBenefit(String orgId, String benefitId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Find benefit by ID and ensure it’s not already deleted
        Benefit benefit = benefitRepository.findByOrganization(org).stream()
                .filter(b -> b.getBenefitId().equals(benefitId) && !Boolean.TRUE.equals(b.getIsDeleted()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Benefit not found or already deleted"));

        // 3. Soft delete (mark as deleted instead of removing)
        benefit.setIsDeleted(true);

        // 4. Save update
        benefitRepository.save(benefit);
    }

    @Override
    public void inactivateBenefit(String organizationId, String benefitId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Benefit benefit = benefitRepository.findByOrganization(org).stream()
                .filter(b -> b.getBenefitId().equals(benefitId) && !"deleted".equalsIgnoreCase(b.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Benefit not found or already deleted"));

        benefit.setStatus("inactive");
        benefitRepository.save(benefit);
    }

    @Override
    public void reactivateBenefit(String organizationId, String benefitId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Benefit benefit = benefitRepository.findByOrganization(org).stream()
                .filter(b -> b.getBenefitId().equals(benefitId) && !"deleted".equalsIgnoreCase(b.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Benefit not found or already deleted"));

        benefit.setStatus("active");
        benefitRepository.save(benefit);
    }



}
