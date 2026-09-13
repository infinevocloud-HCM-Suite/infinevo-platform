package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.UserInvitationDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.UserInvitation;
import com.itsdev.payroll.mapper.organization.UserInvitationMapper;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.organization.UserInvitationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import com.itsdev.payroll.service.CompanyUserService;
import com.itsdev.payroll.service.keycloak.KeycloakUserService;
import com.itsdev.payroll.service.organization.UserInvitationService;
import com.itsdev.payroll.serviceimpl.keycloak.KeycloakUserServiceImpl;
import com.itsdev.payroll.util.JWTUtil;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itsdev.payroll.dto.organization.CombinedUserDTO;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.entity.employee.BasicDetails;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class UserInvitationServiceImpl implements UserInvitationService {

    private final UserInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final KeycloakUserService keycloakService;
    private final BasicDetailsRepository basicDetailsRepository;
    private final CompanyUserService companyUserService;
    private final BrevoEmailService emailService;

    private static final Logger log = LoggerFactory.getLogger(UserInvitationServiceImpl.class);

    public UserInvitationServiceImpl(UserInvitationRepository invitationRepository,
            OrganizationRepository organizationRepository,
            KeycloakUserService keycloakService,
            BasicDetailsRepository basicDetailsRepository,
            CompanyUserService companyUserService,
            BrevoEmailService emailService) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.keycloakService = keycloakService;
        this.basicDetailsRepository = basicDetailsRepository;
        this.companyUserService = companyUserService;
        this.emailService = emailService;
    }

    private String generateUnique10DigitUserId() {
        Random random = new Random();
        String candidate;
        int tries = 0;
        do {
            long number = 1_000_000_000L + (Math.abs(random.nextLong()) % 9_000_000_000L);
            candidate = String.valueOf(number);
            tries++;
            if (tries > 50) {
                candidate = String.valueOf(System.currentTimeMillis()).substring(0, 10);
                break;
            }
        } while (invitationRepository.existsByUserId(candidate));
        return candidate;
    }

    // @Override
    // @Transactional
    // public UserInvitationDTO createInvitation(String organizationId,
    // UserInvitationDTO dto) {
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> new RuntimeException("Organization not found: " +
    // organizationId));
    //
    // if (invitationRepository.existsByEmailAndOrganization(dto.getEmail(), org)) {
    // throw new RuntimeException("Invitation for email [" + dto.getEmail() + "]
    // already exists in this organization");
    // }
    //
    // String generatedUserId = generateUnique10DigitUserId();
    //
    // UserInvitation entity = UserInvitationMapper.toEntity(dto, org);
    // entity.setUserId(generatedUserId);
    // entity.setCreatedAt(LocalDateTime.now());
    // entity.setDeleted(false);
    //
    // UserInvitation saved = invitationRepository.save(entity);
    // return UserInvitationMapper.toDto(saved);
    // }

    @Override
    @Transactional
    public UserInvitationDTO createInvitation(String organizationId, UserInvitationDTO dto) {
        String method = "createInvitation";
        log.info("[{}] 📥 Incoming request: organizationId={}, dto={}", method, organizationId, dto);

        // --- Fetch organization ---
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Organization not found for ID: {}", method, organizationId);
                    return new RuntimeException("Organization not found: " + organizationId);
                });
        log.info("[{}] ✅ Organization found: {}", method, org.getOrganizationName());

        // --- Check if invitation already exists in DB ---
        if (invitationRepository.existsByEmailAndOrganization(dto.getEmail(), org)) {
            log.info("[{}] ❌ Invitation for email [{}] already exists in this organization", method, dto.getEmail());
            throw new RuntimeException(
                    "Invitation for email [" + dto.getEmail() + "] already exists in this organization");
        }

        String keycloakUserId;

        // Check if email exists in Keycloak
        boolean emailExists = keycloakService.isEmailExists(dto.getEmail());

        if (emailExists) {
            // User already exists in Keycloak
            keycloakUserId = keycloakService.getUserIdByEmail(dto.getEmail());
            log.info("[{}] ✅ Email already exists in Keycloak. Using existing userId: {}", method, keycloakUserId);
        } else {
            // Call CompanyUserServiceImpl to create user in both Keycloak AND company_user
            // table
            log.info("[{}] 🔑 Creating temporary Keycloak user for email: {}", method,
                    dto.getEmail());

            // Create temporary user in Keycloak
            keycloakUserId = keycloakService.createTemporaryUserInKeycloak(dto);

            log.info("[{}] ✅ Temporary Keycloak user created, userId={}", method, keycloakUserId);
        }

        // --- Prepare entity ---
        UserInvitation entity = new UserInvitation();
        entity.setUserId(keycloakUserId);
        entity.setRoleId(dto.getRoleId());
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        entity.setMobile(dto.getMobile());
        entity.setInvitationType(dto.getInvitationType());
        entity.setIsSuperAdmin(dto.getIsSuperAdmin());
        entity.setStatus(dto.getStatus());
        entity.setUserRole(dto.getUserRole());
        entity.setOrganization(org);
        entity.setIsEditable(dto.getIsEditable());
        entity.setIsInvitationAccepted(dto.getIsInvitationAccepted());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(false);

        // --- Save entity ---
        UserInvitation saved = invitationRepository.save(entity);
        log.info("[{}] ✅ Invitation saved with ID: {}", method, saved.getId());

        // --- Send invitation email ---
        try {
            log.info("[{}] 📧 Sending user invitation email to: {}", method, dto.getEmail());
            String inviterName = JWTUtil.getCurrentUserName();

            boolean emailSent = emailService.sendUserInvitationEmail(
                    dto.getEmail(),
                    dto.getName(),
                    org.getOrganizationName(),
                    inviterName, // ← ACTUAL INVITER NAME FROM JWT
                    dto.getUserRole(),
                    emailService.calculateExpiryDate(),
                    saved.getAcceptanceToken(),
                    org.getOrganizationId());

            if (emailSent) {
                log.info("[{}] ✅ User invitation email sent successfully to: {}", method, dto.getEmail());
            } else {
                log.warn("[{}] ⚠️ Failed to send invitation email to: {}", method, dto.getEmail());
            }
        } catch (Exception e) {
            log.error("[{}] ❌ Error sending invitation email: {}", method, e.getMessage());
            // Don't throw exception - email failure shouldn't break invitation creation
        }

        return UserInvitationMapper.toDto(saved);
    }

    // @Override
    // @Transactional
    // public UserInvitationDTO createInvitation(String organizationId,
    // UserInvitationDTO dto) {
    // String method = "createInvitation";
    // log.info("[{}] 📥 Incoming request: organizationId={}, dto={}", method,
    // organizationId, dto);

    // // --- Fetch organization ---
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> {
    // log.error("[{}] ❌ Organization not found for ID: {}", method,
    // organizationId);
    // return new RuntimeException("Organization not found: " + organizationId);
    // });
    // log.info("[{}] ✅ Organization found: {}", method, org.getOrganizationName());

    // // --- Check if invitation already exists in DB ---
    // if (invitationRepository.existsByEmailAndOrganization(dto.getEmail(), org)) {
    // log.info("[{}] ❌ Invitation for email [{}] already exists in this
    // organization", method, dto.getEmail());
    // throw new RuntimeException(
    // "Invitation for email [" + dto.getEmail() + "] already exists in this
    // organization");
    // }

    // // --- Check if email exists in Keycloak ---
    // String keycloakUserId;
    // boolean emailExists = keycloakService.isEmailExists(dto.getEmail());

    // if (emailExists) {
    // log.info("[{}] ✅ Email already exists in Keycloak. Fetching userId for email:
    // {}", method, dto.getEmail());
    // keycloakUserId = keycloakService.getUserIdByEmail(dto.getEmail());
    // log.info("[{}] ✅ Fetched existing Keycloak userId={}", method,
    // keycloakUserId);
    // } else {
    // log.info("[{}] 🔑 Email not found in Keycloak. Creating temporary user for
    // email={}", method,
    // dto.getEmail());
    // keycloakUserId = keycloakService.createTemporaryUserInKeycloak(dto);
    // log.info("[{}] ✅ Temporary Keycloak user created, userId={}", method,
    // keycloakUserId);
    // }

    // // --- Prepare entity ---
    // UserInvitation entity = new UserInvitation();
    // entity.setUserId(keycloakUserId);
    // entity.setRoleId(dto.getRoleId());
    // entity.setName(dto.getName());
    // entity.setEmail(dto.getEmail());
    // entity.setMobile(dto.getMobile());
    // entity.setInvitationType(dto.getInvitationType());
    // entity.setIsSuperAdmin(dto.getIsSuperAdmin());
    // entity.setStatus(dto.getStatus());
    // entity.setUserRole(dto.getUserRole());
    // entity.setOrganization(org);
    // entity.setIsEditable(dto.getIsEditable());
    // entity.setIsInvitationAccepted(dto.getIsInvitationAccepted());
    // entity.setCreatedAt(LocalDateTime.now());
    // entity.setDeleted(false);

    // // --- Save entity ---
    // UserInvitation saved = invitationRepository.save(entity);
    // log.info("[{}] ✅ Invitation saved with ID: {}", method, saved.getId());

    // return UserInvitationMapper.toDto(saved);
    // }

    @Override
    @Transactional
    public UserInvitationDTO updateInvitation(String organizationId, String userId, UserInvitationDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        UserInvitation entity = invitationRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("Invitation not found for userId: " + userId));

        if (!entity.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Invitation does not belong to this organization");
        }

        entity.setRoleId(dto.getRoleId());
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        entity.setMobile(dto.getMobile());
        entity.setInvitationType(dto.getInvitationType());
        entity.setIsSuperAdmin(dto.getIsSuperAdmin());
        entity.setStatus(dto.getStatus());
        entity.setUserRole(dto.getUserRole());

        UserInvitation updated = invitationRepository.save(entity);
        return UserInvitationMapper.toDto(updated);
    }

    @Override
    public UserInvitationDTO getInvitation(String organizationId, String userId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        UserInvitation entity = invitationRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("Invitation not found for userId: " + userId));

        if (!entity.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Invitation does not belong to this organization");
        }

        return UserInvitationMapper.toDto(entity);
    }

    @Override
    public List<UserInvitationDTO> getAllInvitations(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        List<UserInvitation> entities = invitationRepository.findAllByOrganizationAndIsDeletedFalse(org);
        return entities.stream().map(UserInvitationMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteInvitation(String organizationId, String userId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        UserInvitation entity = invitationRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("Invitation not found for userId: " + userId));

        if (!entity.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Invitation does not belong to this organization");
        }

        entity.setDeleted(true);
        invitationRepository.save(entity);
    }

    @Override
    @Transactional
    public void inactivateInvitation(String organizationId, String userId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        UserInvitation invitation = invitationRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("Invitation not found for userId: " + userId));

        if (!invitation.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Invitation does not belong to this organization");
        }

        invitation.setStatus("inactive");
        invitationRepository.save(invitation);
    }

    @Override
    @Transactional
    public void reactivateInvitation(String organizationId, String userId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        UserInvitation invitation = invitationRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("Invitation not found for userId: " + userId));

        if (!invitation.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Invitation does not belong to this organization");
        }

        invitation.setStatus("active");
        invitationRepository.save(invitation);
    }

    @Override
    public List<CombinedUserDTO> getAllUsersWithLoginInfo(String organizationId, String currentUserId) {
        String method = "getAllUsersWithLoginInfo";
        log.info("[{}] 📥 Fetching ONLY organization users for organization: {}, currentUserId: {}",
                method, organizationId, currentUserId);

        // Validate organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Organization not found: {}", method, organizationId);
                    return new RuntimeException("Organization not found: " + organizationId);
                });

        List<CombinedUserDTO> organizationUsers = new ArrayList<>();

        // ✅ ONLY Fetch Organization Users (User Invitations) - NO EMPLOYEES
        log.info("[{}] 🔍 Fetching organization users only...", method);
        List<UserInvitation> users = invitationRepository.findAllByOrganizationAndIsDeletedFalse(org);

        for (UserInvitation user : users) {
            CombinedUserDTO dto = new CombinedUserDTO();
            dto.setUserType("ORGANIZATION_USER");
            dto.setUserId(user.getUserId());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setStatus(user.getStatus());
            dto.setRoleId(user.getRoleId());
            dto.setUserRole(user.getUserRole());
            dto.setMobile(user.getMobile());
            dto.setIsSuperAdmin(user.getIsSuperAdmin());
            dto.setIsEditable(user.getIsEditable());
            dto.setIsInvitationAccepted(user.getIsInvitationAccepted());

            // Set current user flag
            dto.setIsCurrentUser(user.getUserId().equals(currentUserId));

            // Get last login time from Keycloak
            try {
                LocalDateTime lastLogin = keycloakService.getLastLoginTime(user.getUserId());
                dto.setLastLoginTime(lastLogin);
            } catch (Exception e) {
                log.warn("[{}] ⚠️ Could not fetch last login for user {}: {}",
                        method, user.getUserId(), e.getMessage());
                dto.setLastLoginTime(null);
            }

            organizationUsers.add(dto);
        }

        log.info("[{}] ✅ Organization users list created. Total: {} users (NO EMPLOYEES)",
                method, organizationUsers.size());

        return organizationUsers;
    }

    @Override
    @Transactional
    public UserInvitationDTO createSuperAdminInvitation(String organizationId, String email, String name,
            String userId, Boolean isEditable, Boolean isInvitationAccepted) {
        String method = "createSuperAdminInvitation";
        log.info("[{}] 🎯 Creating SuperAdmin invitation: orgId={}, email={}, name={}",
                method, organizationId, email, name);

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        // Create SuperAdmin invitation
        UserInvitation superAdminInvitation = new UserInvitation();
        superAdminInvitation.setUserId(userId);
        superAdminInvitation.setEmail(email);
        superAdminInvitation.setName(name);
        superAdminInvitation.setOrganization(org);
        superAdminInvitation.setIsSuperAdmin(true);
        superAdminInvitation.setIsEditable(false); // Non-editable
        superAdminInvitation.setIsInvitationAccepted(true); // Auto-accepted
        superAdminInvitation.setStatus("active");
        superAdminInvitation.setUserRole("Super Admin");
        superAdminInvitation.setCreatedAt(LocalDateTime.now());
        superAdminInvitation.setDeleted(false);

        UserInvitation saved = invitationRepository.save(superAdminInvitation);
        log.info("[{}] ✅ SuperAdmin invitation created successfully: {}", method, saved.getId());

        return UserInvitationMapper.toDto(saved);
    }

    @Override
    public UserInvitationDTO findByEmailAndOrganization(String email, String organizationId) {
        log.info("🔍 [UserInvitationService] Searching invitation: email={}, organizationId={}", email, organizationId);

        try {
            // Check if organization exists first
            log.info("📋 Checking if organization exists: {}", organizationId);
            Optional<Organization> orgOpt = organizationRepository.findByOrganizationId(organizationId);
            if (orgOpt.isEmpty()) {
                log.error("❌ Organization not found: {}", organizationId);
                throw new RuntimeException("Organization not found: " + organizationId);
            }
            Organization org = orgOpt.get();
            log.info("✅ Organization found: id={}, organizationId={}", org.getId(), org.getOrganizationId());

            // Try different repository methods
            log.info("🔍 Trying repository method 1: findByEmailAndOrganization_OrganizationIdAndIsDeletedFalse");
            Optional<UserInvitation> invitation1 = invitationRepository
                    .findByEmailAndOrganization_OrganizationIdAndIsDeletedFalse(email, organizationId);
            log.info("📊 Result 1: {}", invitation1.isPresent() ? "FOUND" : "NOT FOUND");

            if (invitation1.isPresent()) {
                log.info("✅ Found invitation via method 1");
                return UserInvitationMapper.toDto(invitation1.get());
            }

            log.info("🔍 Trying repository method 2: findByEmailAndOrganization_OrganizationId");
            Optional<UserInvitation> invitation2 = invitationRepository
                    .findByEmailAndOrganization_OrganizationId(email, organizationId);
            log.info("📊 Result 2: {}", invitation2.isPresent() ? "FOUND" : "NOT FOUND");

            if (invitation2.isPresent()) {
                log.info("✅ Found invitation via method 2");
                return UserInvitationMapper.toDto(invitation2.get());
            }

            log.info("🔍 Trying repository method 3: findByEmailAndOrganizationAndIsDeletedFalse");
            Optional<UserInvitation> invitation3 = invitationRepository
                    .findByEmailAndOrganizationAndIsDeletedFalse(email, org);
            log.info("📊 Result 3: {}", invitation3.isPresent() ? "FOUND" : "NOT FOUND");

            if (invitation3.isPresent()) {
                log.info("✅ Found invitation via method 3");
                return UserInvitationMapper.toDto(invitation3.get());
            }

            // If all methods fail, check raw SQL
            log.error("❌ No invitation found by any query method");
            log.error("📊 Checking database manually:");
            log.error("   - Email: {}", email);
            log.error("   - Organization ID (string): {}", organizationId);
            log.error("   - Organization entity ID: {}", org.getId());

            throw new RuntimeException(
                    "User invitation not found for email: " + email + " in organization: " + organizationId);

        } catch (Exception e) {
            log.error("💥 Error in findByEmailAndOrganization: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void markAsAccepted(String email, String organizationId) {
        Organization org = organizationRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        UserInvitation entity = invitationRepository
                .findByEmailAndOrganizationAndIsDeletedFalse(email, org)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        entity.setIsInvitationAccepted(true);
        invitationRepository.save(entity);
    }

    @Override
    @Transactional
    public void markAsAccepted(String acceptanceToken) {
        log.info("Marking user invitation as accepted, token: {}", acceptanceToken);

        UserInvitation entity = invitationRepository.findByAcceptanceToken(acceptanceToken)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        entity.setIsInvitationAccepted(true);
        invitationRepository.save(entity);

        log.info("User invitation marked as accepted for email: {}", entity.getEmail());
    }

    @Transactional
    public void markAsRejected(String email, String organizationId, String rejectionReason) {
        UserInvitation entity = invitationRepository.findByEmailAndOrganization_OrganizationId(email, organizationId)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));
        entity.setIsInvitationAccepted(false);
        entity.setRejectionReason(rejectionReason);
        entity.setRejectionDate(LocalDateTime.now());
        invitationRepository.save(entity);
    }

    @Transactional
    public void markAsRejected(String token, String rejectionReason) {
        log.info("Marking user invitation as rejected with reason, token: {}", token);

        UserInvitation entity = invitationRepository.findByAcceptanceToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        entity.setIsInvitationAccepted(false);
        entity.setRejectionReason(rejectionReason);
        entity.setRejectionDate(LocalDateTime.now());
        invitationRepository.save(entity);

        log.info("User invitation marked as rejected with reason for email: {}", entity.getEmail());
    }

    @Override
    public UserInvitationDTO findByAcceptanceToken(String acceptanceToken) {
        UserInvitation entity = invitationRepository.findByAcceptanceToken(acceptanceToken)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        return UserInvitationMapper.toDto(entity);
    }

}
