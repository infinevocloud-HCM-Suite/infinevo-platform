package com.itsdev.payroll.serviceimpl.salarycomponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.itsdev.payroll.dto.salarycomponents.ReimbursementDTO;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;
import com.itsdev.payroll.mapper.salarycomponents.ReimbursementMapper;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.salarycomponents.ReimbursementRepository;
import com.itsdev.payroll.service.salarycomponents.ReimbursementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReimbursementServiceImpl implements ReimbursementService {

    private final ReimbursementRepository reimbursementRepository;
    private final OrganizationRepository organizationRepository;
    private final MasterConfigRepository masterConfigRepository;

    public ReimbursementServiceImpl(ReimbursementRepository reimbursementRepository,
                                    OrganizationRepository organizationRepository,
                                    MasterConfigRepository masterConfigRepository) {
        this.reimbursementRepository = reimbursementRepository;
        this.organizationRepository = organizationRepository;
        this.masterConfigRepository = masterConfigRepository;
    }

    private String generateReimbursementId() {
        return String.valueOf(1000000000L + new Random().nextLong(9000000000L));
    }

    @Override
    public ReimbursementDTO createReimbursement(String organizationId, ReimbursementDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        Reimbursement entity = ReimbursementMapper.toEntity(dto);
        entity.setReimbursementId(generateReimbursementId());
        entity.setOrganization(org);
        reimbursementRepository.save(entity);
        return ReimbursementMapper.toDTO(entity);
    }

    @Override
    public ReimbursementDTO updateReimbursement(String organizationId, String reimbursementId, ReimbursementDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        Reimbursement entity = reimbursementRepository.findByReimbursementIdAndOrganization(reimbursementId, org)
                .orElseThrow(() -> new RuntimeException("Reimbursement not found"));

        entity.setReimbursementName(dto.getReimbursementName());
        entity.setReimbursementType(dto.getReimbursementType());
        entity.setReimbursementTypeFormatted(dto.getReimbursementTypeFormatted());
        entity.setDisplayName(dto.getDisplayName());
        entity.setMaxLimit(dto.getMaxLimit());
        entity.setIsIncludedInCtc(dto.getIsIncludedInCtc());
        entity.setIsIncludedInSalaryStructure(dto.getIsIncludedInSalaryStructure());
        entity.setStatus(dto.getStatus());
        entity.setStatusFormatted(dto.getStatusFormatted());
        entity.setIsFbpComponent(dto.getIsFbpComponent());
        entity.setIsOptIn(dto.getIsOptIn());
        entity.setCarryForwardOption(dto.getCarryForwardOption());
        entity.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());

        reimbursementRepository.save(entity);
        return ReimbursementMapper.toDTO(entity);
    }

    @Override
    public List<ReimbursementDTO> getAllReimbursements(String organizationId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Fetch reimbursements for this org (not deleted)
        List<Reimbursement> reimbursements = reimbursementRepository.findByOrganizationAndIsDeletedFalse(org);

        // 3. If no reimbursements → copy from MasterConfig
        if (reimbursements.isEmpty()) {
            MasterConfig master = masterConfigRepository.findByComponentName("reimbursements")
                    .orElseThrow(() -> new RuntimeException("Master config for reimbursements not found"));

            JsonNode reimbursementsJson = master.getConfigData();
            if (reimbursementsJson.isArray()) {
                List<Reimbursement> defaults = new ArrayList<>();

                for (JsonNode node : reimbursementsJson) {
                    Reimbursement r = new Reimbursement();
                    r.setReimbursementId(generateReimbursementId());
                    r.setOrganization(org);

                    // Map JSON → Entity
                    r.setReimbursementName(node.path("reimbursementName").asText(null));
                    r.setReimbursementType(node.path("reimbursementType").asText(null));
                    r.setReimbursementTypeFormatted(node.path("reimbursementTypeFormatted").asText(null));
                    r.setDisplayName(node.path("displayName").asText(null));
                    r.setMaxLimit(node.path("maxLimit").decimalValue());
                    r.setIsIncludedInCtc(node.path("isIncludedInCtc").asBoolean(false));
                    r.setIsIncludedInSalaryStructure(node.path("isIncludedInSalaryStructure").asBoolean(false));
                    r.setStatus(node.path("status").asText("inactive"));
                    r.setStatusFormatted(node.path("statusFormatted").asText("Inactive"));
                    r.setIsFbpComponent(node.path("isFbpComponent").asBoolean(false));
                    r.setIsOptIn(node.path("isOptIn").asBoolean(false));
                    r.setCarryForwardOption(node.path("carryForwardOption").asText(null));
                    r.setIsAssociatedWithEmployee(node.path("isAssociatedWithEmployee").asBoolean(false));

                    r.setIsDeleted(false);

                    defaults.add(r);
                }

                // Save defaults
                reimbursements = reimbursementRepository.saveAll(defaults);
            }
        }

        // 4. Convert entities → DTOs
        return reimbursements.stream()
                .map(ReimbursementMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ReimbursementDTO getReimbursement(String organizationId, String reimbursementId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        Reimbursement reimbursement = reimbursementRepository.findByReimbursementIdAndOrganization(reimbursementId, org)
                .orElseThrow(() -> new RuntimeException("Reimbursement not found"));
        return ReimbursementMapper.toDTO(reimbursement);
    }

    @Override
    public void deleteReimbursement(String organizationId, String reimbursementId) {
        // 1. Get organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // 2. Find reimbursement by ID and ensure it’s not already deleted
        Reimbursement reimbursement = reimbursementRepository.findByOrganization(org).stream()
                .filter(r -> r.getReimbursementId().equals(reimbursementId) && !r.getIsDeleted()) // exclude already deleted
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Reimbursement not found or already deleted"));

        // 3. Soft delete (mark as deleted instead of removing)
        reimbursement.setIsDeleted(true);

        // 4. Save update
        reimbursementRepository.save(reimbursement);
    }

    @Override
    public void inactivateReimbursement(String organizationId, String reimbursementId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Reimbursement reimbursement = reimbursementRepository.findByOrganization(org).stream()
                .filter(r -> r.getReimbursementId().equals(reimbursementId) && !r.getIsDeleted())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Reimbursement not found or already deleted"));

        reimbursement.setStatus("inactive");
        reimbursement.setStatusFormatted("Inactive");
        reimbursementRepository.save(reimbursement);
    }

    @Override
    public void reactivateReimbursement(String organizationId, String reimbursementId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Reimbursement reimbursement = reimbursementRepository.findByOrganization(org).stream()
                .filter(r -> r.getReimbursementId().equals(reimbursementId) && !r.getIsDeleted())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Reimbursement not found or already deleted"));

        reimbursement.setStatus("active");
        reimbursement.setStatusFormatted("Active");
        reimbursementRepository.save(reimbursement);
    }




}