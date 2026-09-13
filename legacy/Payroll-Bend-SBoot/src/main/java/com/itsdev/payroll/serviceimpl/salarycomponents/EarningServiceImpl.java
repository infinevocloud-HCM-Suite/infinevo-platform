package com.itsdev.payroll.serviceimpl.salarycomponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.itsdev.payroll.dto.salarycomponents.EarningDTO;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.salarycomponents.EarningMapper;
import com.itsdev.payroll.repository.salarycomponents.EarningRepository;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.salarycomponents.EarningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class EarningServiceImpl implements EarningService {

    private final EarningRepository earningRepository;
    private final OrganizationRepository organizationRepository;
    private final MasterConfigRepository masterConfigRepository;

    public EarningServiceImpl(EarningRepository earningRepository,
                              OrganizationRepository organizationRepository,
                              MasterConfigRepository masterConfigRepository) {
        this.earningRepository = earningRepository;
        this.organizationRepository = organizationRepository;
        this.masterConfigRepository = masterConfigRepository;
    }

    private String generateEarningId() {
        Random random = new Random();
        String id;
        do {
            id = String.format("%010d", random.nextInt(1_000_000_000));
        } while (earningRepository.existsByEarningId(id));
        return id;
    }

    @Override
    public EarningDTO create(String organizationId, EarningDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = new Earning();
        earning.setOrganization(org);
        earning.setEarningId(generateEarningId());

        earning.setCanCalculateTaxWithoutProjection(dto.getCanCalculateTaxWithoutProjection());
        earning.setComponentType(dto.getComponentType());
        earning.setDisplayName(dto.getDisplayName());
        earning.setEarningName(dto.getEarningName());
        earning.setEarningType(dto.getEarningType());
        earning.setIsFbpComponent(dto.getIsFbpComponent());
        earning.setIsIncludedInEpf(dto.getIsIncludedInEpf());
        earning.setIsIncludedInEsi(dto.getIsIncludedInEsi());
        earning.setIsIncludedInSalaryStructure(dto.getIsIncludedInSalaryStructure());
        earning.setIsOptIn(dto.getIsOptIn());
        earning.setIsProRata(dto.getIsProRata());
        earning.setIsTaxable(dto.getIsTaxable());
        earning.setIsVariable(dto.getIsVariable());
        earning.setShowInPayslip(dto.getShowInPayslip());
        earning.setStatus(dto.getStatus() != null ? dto.getStatus() : "active");
        earning.setIsDeleted(false);
        earning.setValue(dto.getValue());
        earning.setValueType(dto.getValueType());

        // Save and return DTO
        return EarningMapper.toDTO(earningRepository.save(earning));
    }

    @Override
    public EarningDTO update(String organizationId, String earningId, EarningDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByOrganization(org).stream()
                .filter(e -> e.getEarningId().equals(earningId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        // update fields
        Earning updated = EarningMapper.toEntity(dto);
        updated.setId(earning.getId());
        updated.setEarningId(earning.getEarningId());
        updated.setOrganization(org);

        return EarningMapper.toDTO(earningRepository.save(updated));
    }

    @Override
    public EarningDTO get(String organizationId, String earningId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByOrganization(org).stream()
                .filter(e -> e.getEarningId().equals(earningId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        return EarningMapper.toDTO(earning);
    }

    @Override
    public List<EarningDTO> getAllEarnings(String organizationId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch earnings for this org
        List<Earning> earnings = earningRepository.findByOrganizationAndIsDeletedFalse(org);

        // 3. If no earnings → copy from MasterConfig
        if (earnings.isEmpty()) {
            MasterConfig master = masterConfigRepository.findByComponentName("earnings")
                    .orElseThrow(() -> new RuntimeException("Master config for earnings not found"));

            JsonNode earningsJson = master.getConfigData();
            if (earningsJson.isArray()) {
                List<Earning> defaults = new ArrayList<>();

                for (JsonNode node : earningsJson) {
                    Earning e = new Earning();
                    e.setEarningId(generateEarningId()); // 🔑 Auto-generate 10 digit
                    e.setOrganization(org);

                    // Map JSON → Entity
                    e.setEarningName(node.path("earningName").asText(null));
                    e.setEarningType(node.path("earningType").asText(null));
                    e.setEarningTypeFormatted(node.path("earningTypeFormatted").asText(null));
                    e.setDisplayName(node.path("displayName").asText(null));

                    if (node.has("amount")) {
                        e.setAmount(node.get("amount").decimalValue());
                    }
                    e.setAmountFormatted(node.path("amountFormatted").asText(null));

                    if (node.has("value")) {
                        e.setValue(node.get("value").decimalValue());
                    }
                    e.setValueFormatted(node.path("valueFormatted").asText(null));

                    e.setValueType(node.path("valueType").asText(null));
                    e.setMaxLimit(node.path("maxLimit").asText(null));
                    e.setIsAmountInPercentage(node.path("isAmountInPercentage").asBoolean(false));
                    e.setIsProRata(node.path("isProRata").asBoolean(false));
                    e.setIsIncludedInCtc(node.path("isIncludedInCtc").asBoolean(false));
                    e.setIsIncludedInSalaryStructure(node.path("isIncludedInSalaryStructure").asBoolean(false));

                    e.setStatus(node.path("status").asText(null));
                    e.setStatusFormatted(node.path("statusFormatted").asText(null));
                    e.setIsFbpComponent(node.path("isFbpComponent").asBoolean(false));
                    e.setIsVariable(node.path("isVariable").asBoolean(false));
                    e.setIsUserConfigurable(node.path("isUserConfigurable").asBoolean(false));
                    e.setComponentType(node.path("componentType").asText(null));

                    e.setIsIncludedInEpf(node.path("isIncludedInEpf").asBoolean(false));
                    e.setEpfInclusionType(node.path("epfInclusionType").asText(null));
                    e.setEpfInclusionTypeFormatted(node.path("epfInclusionTypeFormatted").asText(null));

                    e.setIsIncludedInEsi(node.path("isIncludedInEsi").asBoolean(false));
                    e.setIsTaxable(node.path("isTaxable").asBoolean(false));
                    e.setShowInPayslip(node.path("showInPayslip").asBoolean(false));
                    e.setCanCalculateTaxWithoutProjection(node.path("canCalculateTaxWithoutProjection").asBoolean(false));

                    e.setFormulaBasedOn(node.path("formulaBasedOn").asText(null));
                    e.setIsFormulaBasedCalculationSupported(node.path("isFormulaBasedCalculationSupported").asBoolean(false));

                    e.setEarningFrequency(node.path("earningFrequency").asText("default"));

                    defaults.add(e);
                }

                // Save defaults
                earnings = earningRepository.saveAll(defaults);
            }
        }

        // 4. Convert entities → DTOs
        return earnings.stream()
                .map(EarningMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(String organizationId, String earningId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByOrganization(org).stream()
                .filter(e -> e.getEarningId().equals(earningId) && !e.getIsDeleted()) // exclude already deleted
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Earning not found or already deleted"));

        // Instead of hard delete, mark as deleted
        earning.setIsDeleted(true);

        earningRepository.save(earning);
    }

    @Override
    @Transactional
    public void inactivateEarning(String organizationId, String earningId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByOrganization(org).stream()
                .filter(e -> e.getEarningId().equals(earningId) && !e.getIsDeleted())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        earning.setStatus("inactive");
        earningRepository.save(earning);
    }

    @Override
    public void reactivateEarning(String organizationId, String earningId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByOrganization(org).stream()
                .filter(e -> e.getEarningId().equals(earningId) && !e.getIsDeleted())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        earning.setStatus("active");
        earningRepository.save(earning);
    }
}
