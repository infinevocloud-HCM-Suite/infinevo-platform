package com.itsdev.payroll.serviceimpl.salarycomponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.itsdev.payroll.controller.payruns.EmployeePayRunController;
import com.itsdev.payroll.dto.salarycomponents.DeductionDTO;
import com.itsdev.payroll.entity.salarycomponents.Deduction;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.salarycomponents.DeductionMapper;
import com.itsdev.payroll.repository.salarycomponents.DeductionRepository;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.salarycomponents.DeductionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Transactional
public class DeductionServiceImpl implements DeductionService {

    private final DeductionRepository deductionRepository;
    private final OrganizationRepository organizationRepository;
    private final MasterConfigRepository masterConfigRepository;
    private static final Logger log = LoggerFactory.getLogger(DeductionServiceImpl.class);

    public DeductionServiceImpl(DeductionRepository deductionRepository,
                                OrganizationRepository organizationRepository,
                                MasterConfigRepository masterConfigRepository) {
        this.deductionRepository = deductionRepository;
        this.organizationRepository = organizationRepository;
        this.masterConfigRepository = masterConfigRepository;
    }

    private String generateDeductionId() {
        return String.valueOf(1000000000L + new Random().nextLong(9000000000L));
    }

    @Override
    public DeductionDTO create(String organizationId, DeductionDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Deduction deduction = DeductionMapper.toEntity(dto);
        deduction.setDeductionId(generateDeductionId());
        deduction.setCreatedTime(LocalDateTime.now());
        deduction.setOrganization(org);

        Deduction saved = deductionRepository.save(deduction);
        return DeductionMapper.toDTO(saved);
    }

    @Override
    public DeductionDTO update(String organizationId, String deductionId, DeductionDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Deduction deduction = deductionRepository.findByDeductionId(deductionId)
                .orElseThrow(() -> new RuntimeException("Deduction not found"));

        if (!deduction.getOrganization().equals(org)) {
            throw new RuntimeException("Deduction does not belong to this organization");
        }

        deduction.setDeductionName(dto.getDeductionName());
        deduction.setDeductionType(dto.getDeductionType());
        deduction.setDeductionTypeFormatted(dto.getDeductionTypeFormatted());
        deduction.setStatus(dto.getStatus());
        deduction.setStatusFormatted(dto.getStatusFormatted());
        deduction.setIsRecurring(dto.getIsRecurring());
        deduction.setIsUserConfigurable(dto.getIsUserConfigurable());
        deduction.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        deduction.setPerquisiteInterestRate(dto.getPerquisiteInterestRate());
        deduction.setEmiInterestRate(dto.getEmiInterestRate());
        deduction.setEmiType(dto.getEmiType());

        Deduction updated = deductionRepository.save(deduction);
        return DeductionMapper.toDTO(updated);
    }

    @Override
    public DeductionDTO get(String organizationId, String deductionId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Deduction deduction = deductionRepository.findByDeductionId(deductionId)
                .orElseThrow(() -> new RuntimeException("Deduction not found"));

        if (!deduction.getOrganization().equals(org)) {
            throw new RuntimeException("Deduction does not belong to this organization");
        }

        return DeductionMapper.toDTO(deduction);
    }


    @Override
    public List<DeductionDTO> getAll(String organizationId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch deductions for this org
        List<Deduction> deductions = deductionRepository.findByOrganizationAndIsDeletedFalse(org);

        // 3. If no deductions → copy from MasterConfig
        if (deductions.isEmpty()) {
            MasterConfig master = masterConfigRepository.findByComponentName("deductions")
                    .orElseThrow(() -> new RuntimeException("Master config for deductions not found"));

            JsonNode deductionsJson = master.getConfigData();
            if (deductionsJson.isArray()) {
                List<Deduction> defaults = new ArrayList<>();

                for (JsonNode node : deductionsJson) {
                    Deduction d = new Deduction();
                    d.setDeductionId(generateDeductionId());
                    d.setOrganization(org);
                    d.setCreatedTime(LocalDateTime.now());

                    // Map JSON → Entity
                    d.setDeductionName(node.path("deductionName").asText(null));
                    d.setDeductionType(node.path("deductionType").asText(null));
                    d.setDeductionTypeFormatted(node.path("deductionTypeFormatted").asText(null));

                    d.setStatus(node.path("status").asText(null));
                    d.setStatusFormatted(node.path("statusFormatted").asText(null));

                    d.setIsRecurring(node.path("isRecurring").asBoolean(false));
                    d.setIsUserConfigurable(node.path("isUserConfigurable").asBoolean(false));
                    d.setIsAssociatedWithEmployee(node.path("isAssociatedWithEmployee").asBoolean(false));

                    d.setPerquisiteInterestRate(node.path("perquisiteInterestRate").asText(null));
                    d.setEmiInterestRate(node.path("emiInterestRate").asText(null));
                    d.setEmiType(node.path("emiType").asText(null));

                    defaults.add(d);
                }

                // Save defaults
                deductions = deductionRepository.saveAll(defaults);
            }
        }

        // 4. Convert entities → DTOs
        return deductions.stream()
                .map(DeductionMapper::toDTO)
                .collect(Collectors.toList());
    }


    @Override
    public void delete(String organizationId, String deductionId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Find deduction by ID and ensure it’s not already deleted
        Deduction deduction = deductionRepository.findByOrganization(org).stream()
                .filter(d -> d.getDeductionId().equals(deductionId) && !d.getIsDeleted()) // exclude already deleted
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Deduction not found or already deleted"));

        // 3. Soft delete (mark as deleted instead of removing)
        deduction.setIsDeleted(true);

        // 4. Save update
        deductionRepository.save(deduction);
    }

    @Override
    public void inactivateDeduction(String organizationId, String deductionId) {
        String method = "inactivateDeduction";
        log.info("[{}] 🔍 Starting deduction inactivation process | orgId={}, deductionId={}", method, organizationId, deductionId);

        // Step 1: Fetch Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Organization not found | orgId={}", method, organizationId);
                    return new RuntimeException("Organization not found");
                });
        log.info("[{}] 🏢 Organization fetched successfully | orgName={}", method, org.getOrganizationName());

        // Step 2: Fetch Deduction under Organization
        Deduction deduction = deductionRepository.findByOrganization(org).stream()
                .filter(d -> d.getDeductionId().equals(deductionId) && !d.getIsDeleted())
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Deduction not found or already deleted | deductionId={}", method, deductionId);
                    return new RuntimeException("Deduction not found or already deleted");
                });
        log.info("[{}] 📄 Deduction fetched successfully | deductionName={}, currentStatus={}", method, deduction.getDeductionName(), deduction.getStatus());

        // Step 3: Update status to inactive
        deduction.setStatus("inactive");
        deductionRepository.save(deduction);
        log.info("[{}] 🟡 Deduction status updated to 'inactive' and saved successfully | deductionId={}", method, deductionId);
    }


    @Override
    public void reactivateDeduction(String organizationId, String deductionId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Deduction deduction = deductionRepository.findByOrganization(org).stream()
                .filter(d -> d.getDeductionId().equals(deductionId) && !d.getIsDeleted())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Deduction not found or already deleted"));

        deduction.setStatus("active");
        deductionRepository.save(deduction);
    }



}
