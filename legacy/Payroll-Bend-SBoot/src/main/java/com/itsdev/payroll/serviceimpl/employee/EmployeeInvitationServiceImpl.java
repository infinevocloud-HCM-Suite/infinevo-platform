package com.itsdev.payroll.serviceimpl.employee;

import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;
import com.itsdev.payroll.entity.employee.EmployeeInvitation;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.employee.EmployeeInvitationMapper;
import com.itsdev.payroll.repository.employee.EmployeeInvitationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.employee.EmployeeInvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class EmployeeInvitationServiceImpl implements EmployeeInvitationService {

    private final EmployeeInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;

    private static final Logger log = LoggerFactory.getLogger(EmployeeInvitationServiceImpl.class);

    public EmployeeInvitationServiceImpl(EmployeeInvitationRepository invitationRepository,
            OrganizationRepository organizationRepository) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
    }

    private String generateInvitationId() {
        Random random = new Random();
        String id;
        do {
            id = String.valueOf(1000000000L + (long) (random.nextDouble() * 8999999999L)); // 10 digits
        } while (invitationRepository.findByInvitationIdAndOrganization_OrganizationId(id, null).isPresent());
        return id;
    }

    @Override
    public EmployeeInvitationDTO createInvitation(String organizationId, EmployeeInvitationDTO dto) {
        String method = "createInvitation";
        log.info("[{}] Incoming request: organizationId={}, dto={}", method, organizationId, dto);

        // --- Organization lookup ---
        log.info("[{}] Fetching Organization by ID: {}", method, organizationId);
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] Organization not found for ID: {}", method, organizationId);
                    return new RuntimeException("Organization not found");
                });
        log.info("[{}] Organization found: {}", method, org.getOrganizationName());

        // --- Map DTO to entity ---
        log.info("[{}] Mapping DTO to EmployeeInvitation entity", method);
        EmployeeInvitation entity = EmployeeInvitationMapper.toEntity(dto);
        entity.setOrganization(org);

        // --- Generate invitation ID ---
        String invitationId = generateInvitationId();
        entity.setInvitationId(invitationId);
       // entity.setExpiryDate(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(5));
        entity.setExpiryDate(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(7));
        log.info("[{}] Generated invitationId: {}", method, invitationId);

        // --- Save entity ---
        log.info("[{}] Saving EmployeeInvitation entity...", method);
        EmployeeInvitation saved = invitationRepository.save(entity);
        log.info("[{}] Saved EmployeeInvitation with DB ID: {}, invitationId: {}", method, saved.getId(),
                saved.getInvitationId());

        // --- Convert to DTO for response ---
        EmployeeInvitationDTO response = EmployeeInvitationMapper.toDto(saved);
        log.info("[{}] Returning response DTO: {}", method, response);
        return response;
    }

    @Override
    public EmployeeInvitationDTO updateInvitation(String organizationId, String invitationId,
            EmployeeInvitationDTO dto) {
        EmployeeInvitation entity = invitationRepository
                .findByInvitationIdAndOrganization_OrganizationId(invitationId, organizationId)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        entity.setEmail(dto.getEmail());
        entity.setEmployeeId(dto.getEmployeeId());
        entity.setIsPortalEnabled(dto.getIsPortalEnabled());
        entity.setIsInvitationAccepted(false);
        entity.setAcceptanceToken(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
       // entity.setExpiryDate(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(5));

        entity.setExpiryDate(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(7));

        return EmployeeInvitationMapper.toDto(invitationRepository.save(entity));
    }

    @Override
    public List<EmployeeInvitationDTO> getInvitations(String organizationId) {
        return invitationRepository.findByOrganization_OrganizationId(organizationId)
                .stream()
                .map(EmployeeInvitationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public EmployeeInvitationDTO getInvitation(String organizationId, String invitationId) {
        return invitationRepository.findByInvitationIdAndOrganization_OrganizationId(invitationId, organizationId)
                .map(EmployeeInvitationMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));
    }

    @Override
    public EmployeeInvitationDTO acceptInvitation(String organizationId, String invitationId) {
        EmployeeInvitation entity = invitationRepository
                .findByInvitationIdAndOrganization_OrganizationId(invitationId, organizationId)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        entity.setIsInvitationAccepted(true);
        return EmployeeInvitationMapper.toDto(invitationRepository.save(entity));
    }

    @Override
    public Optional<EmployeeInvitationDTO> findByEmployeeId(String organizationId, String employeeId) {
        return invitationRepository.findByEmployeeIdAndOrganization_OrganizationId(employeeId, organizationId)
                .map(EmployeeInvitationMapper::toDto);
    }
    // Add these 2 methods to your existing EmployeeInvitationServiceImpl class:

    @Override
    public EmployeeInvitationDTO findByEmailAndOrganization(String email, String organizationId) {
        List<EmployeeInvitation> entities = invitationRepository
                .findAllByEmailAndOrganization_OrganizationId(email, organizationId);
        if (entities.isEmpty()) {
            throw new RuntimeException("Employee invitation not found");
        }
        // Prefer first non-accepted invitation, otherwise get the last one
        EmployeeInvitation entity = entities.stream()
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsInvitationAccepted()))
                .findFirst()
                .orElse(entities.get(entities.size() - 1));
        return EmployeeInvitationMapper.toDto(entity);
    }

    @Transactional
    public void markAsAccepted(String email, String organizationId) {
        List<EmployeeInvitation> entities = invitationRepository
                .findAllByEmailAndOrganization_OrganizationId(email, organizationId);
        if (entities.isEmpty()) {
            throw new RuntimeException("Invitation not found");
        }
        boolean marked = false;
        for (EmployeeInvitation entity : entities) {
            if (!Boolean.TRUE.equals(entity.getIsInvitationAccepted())) {
                entity.setIsInvitationAccepted(true);
                invitationRepository.save(entity);
                marked = true;
            }
        }
        if (!marked) {
            EmployeeInvitation entity = entities.get(entities.size() - 1);
            entity.setIsInvitationAccepted(true);
            invitationRepository.save(entity);
        }
    }

    @Override
    @Transactional
    public void markAsAccepted(String acceptanceToken) {
        EmployeeInvitation entity = invitationRepository.findByAcceptanceToken(acceptanceToken)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        entity.setIsInvitationAccepted(true);
        invitationRepository.save(entity);
    }

    @Transactional
    public void markAsRejected(String email, String organizationId, String rejectionReason) {
        List<EmployeeInvitation> entities = invitationRepository
                .findAllByEmailAndOrganization_OrganizationId(email, organizationId);
        if (entities.isEmpty()) {
            throw new RuntimeException("Invitation not found");
        }
        EmployeeInvitation entity = entities.stream()
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsInvitationAccepted()))
                .findFirst()
                .orElse(entities.get(entities.size() - 1));
        entity.setIsInvitationAccepted(false);
        entity.setRejectionReason(rejectionReason);
        entity.setRejectionDate(LocalDateTime.now());
        invitationRepository.save(entity);
    }

    @Override
    @Transactional
    public void markAsRejected(String token, String rejectionReason) {
        log.info("Marking employee invitation as rejected with reason, token: {}", token);

        EmployeeInvitation entity = invitationRepository.findByAcceptanceToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        entity.setIsInvitationAccepted(false);
        entity.setRejectionReason(rejectionReason);
        entity.setRejectionDate(LocalDateTime.now());
        invitationRepository.save(entity);

        log.info("Employee invitation marked as rejected with reason for email: {}", entity.getEmail());
    }

    @Override
    public EmployeeInvitationDTO findByAcceptanceToken(String token) {
        EmployeeInvitation entity = invitationRepository.findByAcceptanceToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));
        return EmployeeInvitationMapper.toDto(entity);
    }
}
