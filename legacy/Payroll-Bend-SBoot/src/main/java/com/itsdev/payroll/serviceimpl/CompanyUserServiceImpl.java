package com.itsdev.payroll.serviceimpl;

import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.organization.OrganizationRoleDTO;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;
import com.itsdev.payroll.entity.CompanyUser;
import com.itsdev.payroll.entity.OrganizationUserRoleMapping;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.OrganizationUserMapping;
import com.itsdev.payroll.entity.organization.OrganizationRole;
import com.itsdev.payroll.entity.organization.UserInvitation;
import com.itsdev.payroll.mapper.CompanyUserMapper;
import com.itsdev.payroll.repository.CompanyUserRepository;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.OrganizationUserMappingRepository;
import com.itsdev.payroll.repository.organization.OrganizationRoleRepository;
import com.itsdev.payroll.repository.organization.WorkLocationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import com.itsdev.payroll.service.CompanyUserService;
import com.itsdev.payroll.service.keycloak.KeycloakUserService;
import com.itsdev.payroll.service.organization.OrganizationRoleService;
import com.itsdev.payroll.service.organization.UserInvitationService;
import com.itsdev.payroll.serviceimpl.employee.BasicDetailsServiceImpl;
import com.itsdev.payroll.util.PasswordGeneratorUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.itsdev.payroll.repository.organization.UserInvitationRepository;
import com.itsdev.payroll.entity.organization.UserInvitation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.EmployeeInvitationRepository;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.EmployeeInvitation;

@Service
public class CompanyUserServiceImpl implements CompanyUserService {

        @Autowired
        private CompanyUserRepository companyUserRepository;

        @Autowired
        private KeycloakUserService keycloakUserService;

        @Autowired
        private BrevoEmailService emailService;

        @Autowired
        private OrganizationRepository organizationRepository;

        @Autowired
        private WorkLocationRepository workLocationRepository;

        @Autowired
        private OrganizationUserMappingRepository organizationUserMappingRepository;

        @Autowired
        private OrganizationRoleService organizationRoleService;

        @Autowired
        private OrganizationRoleRepository roleRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        @Lazy
        private UserInvitationService userInvitationService;

        @Autowired
        private UserInvitationRepository invitationRepository; // ← ADD THIS LINE

        @Autowired
        private BasicDetailsRepository basicDetailsRepository;

        @Autowired
        private EmployeeInvitationRepository employeeInvitationRepository;

        private static final Logger log = LoggerFactory.getLogger(CompanyUserServiceImpl.class);

        @Override
        public boolean isEmailTaken(String email) {
                return companyUserRepository.existsByUserEmail(email);
        }

        @Override
        public CompanyUser saveUser(CompanyUser user) {
                return companyUserRepository.save(user);
        }

        @Autowired
        private OrganizationUserRoleMappingRepository organizationUserRoleMappingRepository;

        private String generateUniqueOrganizationId() {
                String id;
                do {
                        id = String.format("%06d", new Random().nextInt(900000) + 100000); // 6-digit
                } while (organizationRepository.existsByOrganizationId(id));
                return id;
        }

        @Transactional(rollbackFor = Exception.class)
        public void registerUser(CompanyUserDTO userDTO) {
                System.out.println("=== Starting user registration process ===");
                System.out.println("Received user registration DTO: " + userDTO);

                // 1. Create user in Keycloak
                System.out.println("Calling KeycloakUserService to create user in Keycloak");
                String keycloakUserId = keycloakUserService.createUserInKeycloak(userDTO);
                System.out.println("User created successfully in Keycloak");

                // 2. Save user in DB (organization is created separately after first login)
                CompanyUser user = new CompanyUser();
                user.setUserId(keycloakUserId); // from Keycloak
                user.setCompanyName(userDTO.getCompanyName());
                user.setUserEmail(userDTO.getUserEmail());
                user.setFirstName(userDTO.getFirstName());
                user.setLastName(userDTO.getLastName());
                user.setPhoneNumber(userDTO.getPhoneNumber());
                user.setCountry(userDTO.getCountry());
                user.setStates(userDTO.getStates());

                String encodedPassword = passwordEncoder.encode(userDTO.getPassword());
                user.setPassword(encodedPassword);

                user.setToc(userDTO.getToc());

                System.out.println("Saving user in MySQL database: " + userDTO.getUserEmail());
                companyUserRepository.save(user);
                System.out.println("User saved successfully in database");

                System.out.println("=== User registration process completed successfully ===");
        }

        // @Transactional(rollbackFor = Exception.class)
        // public void organizationUserRegistration(CompanyUserDTO userDTO) {
        // System.out.println("=== Starting Organization User Registration ===");
        // System.out.println("Received DTO: " + userDTO);

        // // 1. Validate organization
        // Organization org =
        // organizationRepository.findByOrganizationId(userDTO.getOrganizationId())
        // .orElseThrow(() -> new RuntimeException(
        // "Organization not found: " + userDTO.getOrganizationId()));

        // // 2. Validate role existence by roleName in this organization
        // boolean roleExists =
        // roleRepository.existsByRoleNameAndOrganizationAndIsDeletedFalse(
        // userDTO.getRoleName(), org);

        // if (!roleExists) {
        // throw new RuntimeException(
        // "Role with name [" + userDTO.getRoleName()
        // + "] does not exist in this organization");
        // }

        // // 3. Fetch role by roleId for final verification
        // OrganizationRole role = roleRepository
        // .findByRoleIdAndOrganizationAndIsDeletedFalse(userDTO.getRoleId(), org)
        // .orElseThrow(() -> new RuntimeException("Invalid roleId for this
        // organization"));

        // if (!role.getRoleName().equalsIgnoreCase(userDTO.getRoleName())) {
        // throw new RuntimeException("Role name does not match for provided roleId in
        // this organization");
        // }
        // System.out.println("Role validated: " + role.getRoleName());

        // // // 4. Create user in Keycloak
        // // String keycloakUserId = keycloakUserService.createUserInKeycloak(userDTO);
        // // System.out.println("User created in Keycloak: " + keycloakUserId);

        // // 4. Upgrade temporary user in Keycloak
        // System.out.println("🔑 Upgrading temporary user in Keycloak for email: " +
        // userDTO.getUserEmail());
        // String keycloakUserId =
        // keycloakUserService.upgradeTemporaryUserInKeycloak(userDTO);
        // System.out.println("✅ Temporary user upgraded in Keycloak: " +
        // keycloakUserId);

        // // 5. Save user in DB
        // CompanyUser user = new CompanyUser();
        // user.setUserId(keycloakUserId);
        // user.setCompanyName(userDTO.getCompanyName());
        // user.setUserEmail(userDTO.getUserEmail());
        // user.setFirstName(userDTO.getFirstName());
        // user.setLastName(userDTO.getLastName());
        // user.setPhoneNumber(userDTO.getPhoneNumber());
        // user.setCountry(userDTO.getCountry());
        // user.setStates(userDTO.getStates());

        // // Encrypt password before storing
        // String encodedPassword = passwordEncoder.encode(userDTO.getPassword());
        // user.setPassword(encodedPassword);

        // user.setToc(userDTO.getToc());

        // companyUserRepository.save(user);
        // System.out.println("Organization user saved in DB: " + user.getUserEmail());

        // // 6. Save OrganizationUserMapping
        // OrganizationUserMapping mapping = new OrganizationUserMapping();
        // mapping.setUserId(keycloakUserId);
        // mapping.setOrganizationId(org.getOrganizationId());
        // organizationUserMappingRepository.save(mapping);
        // System.out.println("Mapping saved between User and Organization");

        // // 6. Save OrganizationUserRoleMapping
        // OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
        // roleMapping.setUserId(keycloakUserId);
        // roleMapping.setOrganizationId(org.getOrganizationId());
        // roleMapping.setRoleId(role.getRoleId());
        // roleMapping.setRoleName(role.getRoleName());
        // roleMapping.setEmployeePortalEnable(false);
        // organizationUserRoleMappingRepository.save(roleMapping);
        // System.out.println("Mapping saved between User, Role and Organization");

        // System.out.println("=== Organization User Registration Completed Successfully
        // ===");
        // }

        @Transactional(rollbackFor = Exception.class)
        public String organizationUserRegistration(UserInvitationDTO userInvitationDTO) {
                String method = "organizationUserRegistration";
                log.info("[{}] Starting Organization User Registration", method);
                log.info("[{}] Incoming DTO: {}", method, userInvitationDTO);

                // 1. Validate organization
                Organization org = organizationRepository.findByOrganizationId(userInvitationDTO.getOrganizationId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Organization not found: " + userInvitationDTO.getOrganizationId()));

                // 2. Validate role existence by roleName in this organization
                boolean roleExists = roleRepository.existsByRoleNameAndOrganizationAndIsDeletedFalse(
                                userInvitationDTO.getUserRole(), org);

                if (!roleExists) {
                        throw new RuntimeException(
                                        "Role with name [" + userInvitationDTO.getUserRole()
                                                        + "] does not exist in this organization");
                }

                // 3. Fetch role by roleId for final verification
                OrganizationRole role = roleRepository
                                .findByRoleIdAndOrganizationAndIsDeletedFalse(userInvitationDTO.getRoleId(), org)
                                .orElseThrow(() -> new RuntimeException("Invalid roleId for this organization"));

                if (!role.getRoleName().equalsIgnoreCase(userInvitationDTO.getUserRole())) {
                        throw new RuntimeException("Role name does not match for provided roleId in this organization");
                }
                log.info("[{}] Role validated: {}", method, role.getRoleName());

                // 4. Create NEW user in Keycloak (not upgrade)
                log.info("[{}] Creating organization user in Keycloak for email: {}", method,
                                userInvitationDTO.getEmail());

                // This returns userId (String) from Keycloak; password may not be returned by
                // this method
                String keycloakUserId = keycloakUserService.createTemporaryUserInKeycloak(userInvitationDTO);
                String generatedPassword = null;

                log.info("[{}] Organization user created in Keycloak with ID: {}", method, keycloakUserId);

                // 5. Save user in DB
                CompanyUser user = new CompanyUser();
                user.setUserId(keycloakUserId);
                user.setCompanyName(org.getOrganizationName());
                user.setUserEmail(userInvitationDTO.getEmail());

                // Split name into first and last name
                String[] nameParts = userInvitationDTO.getName().split(" ", 2);
                user.setFirstName(nameParts[0]);
                user.setLastName(nameParts.length > 1 ? nameParts[1] : "");

                user.setPhoneNumber(userInvitationDTO.getMobile());
                user.setCountry(org.getState());
                user.setStates(org.getState());

                // Encrypt generated password before storing (if available)
                String encodedPassword = generatedPassword != null ? passwordEncoder.encode(generatedPassword) : null;
                user.setPassword(encodedPassword);
                log.info("[{}] Password encrypted for email: {}", method, userInvitationDTO.getEmail());

                user.setToc(true); // Default to true
                companyUserRepository.save(user);
                log.info("[{}] Organization user saved in DB: {}", method, user.getUserEmail());

                // 6. Save OrganizationUserMapping
                OrganizationUserMapping mapping = new OrganizationUserMapping();
                mapping.setUserId(keycloakUserId);
                mapping.setOrganizationId(org.getOrganizationId());
                organizationUserMappingRepository.save(mapping);
                log.info("[{}] Mapping saved between User and Organization", method);

                // 7. Save OrganizationUserRoleMapping
                OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
                roleMapping.setUserId(keycloakUserId);
                roleMapping.setOrganizationId(org.getOrganizationId());
                roleMapping.setRoleId(role.getRoleId());
                roleMapping.setRoleName(role.getRoleName());
                roleMapping.setEmployeePortalEnable(false);
                organizationUserRoleMappingRepository.save(roleMapping);
                log.info("[{}] Mapping saved between User, Role and Organization", method);

                log.info("[{}] Organization User Registration Completed Successfully", method);

                return keycloakUserId; // Return userId for UserInvitationServiceImpl
        }

        @Override
        public List<CompanyUserDTO> getAllUsers() {
                List<CompanyUser> users = companyUserRepository.findAll();

                return users.stream()
                                .map(CompanyUserMapper::toDTO)
                                .collect(Collectors.toList());
        }

        @Transactional(rollbackFor = Exception.class)
        public void assignOrUpdateRoleToUserInOrganization(CompanyUserDTO dto) {
                System.out.println("=== assignOrUpdateRoleToUserInOrganization called ===");
                System.out.println("Payload: " + dto);

                // 1. Validate organization
                Organization org = organizationRepository.findByOrganizationId(dto.getOrganizationId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Organization not found: " + dto.getOrganizationId()));

                // 2. Validate role existence by roleId and organization
                OrganizationRole role = roleRepository
                                .findByRoleIdAndOrganizationAndIsDeletedFalse(dto.getRoleId(), org)
                                .orElseThrow(() -> new RuntimeException("Invalid roleId for this organization"));

                if (!role.getRoleName().equalsIgnoreCase(dto.getRoleName())) {
                        throw new RuntimeException("Role name does not match for provided roleId in this organization");
                }

                // 3. Get user by email
                // CompanyUser companyUser =
                // companyUserRepository.findByUserEmail(dto.getUserEmail())
                // .orElseThrow(() -> new RuntimeException(
                // "User not found with email: " + dto.getUserEmail()));

                CompanyUser companyUser = companyUserRepository.findAllByUserEmail(dto.getUserEmail()).stream().findFirst()
                                .orElseGet(() -> {
                                        // Create entry if doesn't exist
                                        String keycloakUserId = keycloakUserService
                                                        .getUserIdByEmail(dto.getUserEmail());

                                        CompanyUser newUser = new CompanyUser();
                                        newUser.setUserId(keycloakUserId);
                                        newUser.setUserEmail(dto.getUserEmail());
                                        newUser.setFirstName("User");
                                        newUser.setLastName("");
                                        newUser.setCompanyName(org.getOrganizationName());
                                        newUser.setToc(true);
                                        newUser.setPassword("[SYSTEM]");

                                        return companyUserRepository.save(newUser);
                                });

                String userId = companyUser.getUserId();
                if (userId == null || userId.isEmpty()) {
                        throw new RuntimeException(
                                        "User does not have a linked Keycloak userId: " + dto.getUserEmail());
                }

                // 4. Ensure OrganizationUserMapping exists
                boolean mappingExists = organizationUserMappingRepository
                                .existsByUserIdAndOrganizationId(userId, org.getOrganizationId());

                if (!mappingExists) {
                        OrganizationUserMapping mapping = new OrganizationUserMapping();
                        mapping.setUserId(userId);
                        mapping.setOrganizationId(org.getOrganizationId());
                        organizationUserMappingRepository.save(mapping);
                        System.out.println(
                                        "Created OrganizationUserMapping for userId=" + userId + ", org="
                                                        + org.getOrganizationId());
                }

                // 5. Check if role mapping exists for this user and organization
                Optional<OrganizationUserRoleMapping> existingRoleMappingOpt = organizationUserRoleMappingRepository
                                .findByUserIdAndOrganizationId(userId, org.getOrganizationId());

                if (existingRoleMappingOpt.isPresent()) {
                        // Update existing mapping with new role
                        OrganizationUserRoleMapping existingRoleMapping = existingRoleMappingOpt.get();
                        existingRoleMapping.setRoleId(role.getRoleId());
                        existingRoleMapping.setRoleName(role.getRoleName());
                        organizationUserRoleMappingRepository.save(existingRoleMapping);

                        System.out.println("Updated existing OrganizationUserRoleMapping for userId=" + userId +
                                        " with new roleId=" + role.getRoleId());
                } else {
                        // Create new role mapping
                        OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
                        roleMapping.setUserId(userId);
                        roleMapping.setOrganizationId(org.getOrganizationId());
                        roleMapping.setRoleId(role.getRoleId());
                        roleMapping.setRoleName(role.getRoleName());
                        roleMapping.setEmployeePortalEnable(false);
                        organizationUserRoleMappingRepository.save(roleMapping);

                        System.out.println("Created new OrganizationUserRoleMapping for userId=" + userId +
                                        ", roleId=" + role.getRoleId());
                }
                try {
                        // Generate new password for the user
                        String generatedPassword = PasswordGeneratorUtil.generatePassword();

                        // Update password in Keycloak
                        keycloakUserService.updateUserPassword(dto.getUserEmail(), generatedPassword);

                        // Send credentials email
                        boolean emailSent = emailService.sendUserCredentialsEmail(
                                        dto.getUserEmail(),
                                        companyUser.getFirstName() + " " + companyUser.getLastName(),
                                        generatedPassword);

                        if (emailSent) {
                                System.out.println("✅ User credentials email sent to: " + dto.getUserEmail());
                        } else {
                                System.out.println("⚠️ Failed to send credentials email to: " + dto.getUserEmail());
                        }
                } catch (Exception e) {
                        System.out.println("❌ Error sending credentials email: " + e.getMessage());
                        // Don't throw - email failure shouldn't break role assignment
                }

                System.out.println("=== Role assignment completed ===");

        }

        // @Transactional
        // public void organizationEmployeeRegistration(CompanyUserDTO userDTO) {
        // String method = "organizationEmployeeRegistration";
        // log.info("[{}] Starting Organization Employee Registration", method);
        // log.info("[{}] Incoming DTO: {}", method, userDTO);

        // // 1. Validate organization
        // log.info("[{}] Validating organizationId: {}", method,
        // userDTO.getOrganizationId());
        // Organization org =
        // organizationRepository.findByOrganizationId(userDTO.getOrganizationId())
        // .orElseThrow(() -> {
        // log.error("[{}] Organization not found: {}", method,
        // userDTO.getOrganizationId());
        // return new RuntimeException("Organization not found: " +
        // userDTO.getOrganizationId());
        // });
        // log.info("[{}] Organization found: {}", method, org.getOrganizationName());

        // // // 2. Create user in Keycloak
        // // log.info("[{}] Creating user in Keycloak for email: {}", method,
        // // userDTO.getUserEmail());
        // // String keycloakUserId = keycloakUserService.createUserInKeycloak(userDTO);
        // // log.info("[{}] Employee created in Keycloak with ID: {}", method,
        // // keycloakUserId);

        // // 2. Upgrade temporary user in Keycloak (frontend guarantees it’s temporary)
        // log.info("[{}] Upgrading temporary user in Keycloak for email: {}", method,
        // userDTO.getUserEmail());
        // String keycloakUserId =
        // keycloakUserService.upgradeTemporaryUserInKeycloak(userDTO);
        // if (keycloakUserId == null) {
        // throw new RuntimeException("User upgrade failed in Keycloak for email: " +
        // userDTO.getUserEmail());
        // }
        // log.info("[{}] User upgraded in Keycloak with ID: {}", method,
        // keycloakUserId);

        // // 3. Save employee in DB (CompanyUser table)
        // log.info("[{}] Preparing CompanyUser entity for email: {}", method,
        // userDTO.getUserEmail());
        // CompanyUser employee = new CompanyUser();
        // employee.setUserId(keycloakUserId);
        // employee.setCompanyName(userDTO.getCompanyName());
        // employee.setUserEmail(userDTO.getUserEmail());
        // employee.setFirstName(userDTO.getFirstName());
        // employee.setLastName(userDTO.getLastName());
        // employee.setPhoneNumber(userDTO.getPhoneNumber());
        // employee.setCountry(userDTO.getCountry());
        // employee.setStates(userDTO.getStates());

        // // Encrypt password before storing
        // String encodedPassword = passwordEncoder.encode(userDTO.getPassword());
        // employee.setPassword(encodedPassword);
        // log.info("[{}] Password encrypted for email: {}", method,
        // userDTO.getUserEmail());

        // employee.setToc(userDTO.getToc());
        // companyUserRepository.save(employee);
        // log.info("[{}] Employee saved in DB: {}", method, employee.getUserEmail());

        // // 4. Save OrganizationUserMapping
        // log.info("[{}] Creating OrganizationUserMapping for userId: {}", method,
        // keycloakUserId);
        // OrganizationUserMapping mapping = new OrganizationUserMapping();
        // mapping.setUserId(keycloakUserId);
        // mapping.setOrganizationId(org.getOrganizationId());
        // organizationUserMappingRepository.save(mapping);
        // log.info("[{}] Mapping saved between Employee and Organization", method);

        // // 5. Save OrganizationUserRoleMapping (Employee Portal access only)
        // log.info("[{}] Creating OrganizationUserRoleMapping for portal access",
        // method);
        // OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
        // roleMapping.setUserId(keycloakUserId);
        // roleMapping.setOrganizationId(org.getOrganizationId());

        // // Role fields optional → can remain NULL
        // roleMapping.setRoleId(null);
        // roleMapping.setRoleName(null);

        // // Employee Portal access → default false if not explicitly provided
        // roleMapping.setEmployeePortalEnable(
        // userDTO.getIsEmployeePortalEnable() != null ?
        // userDTO.getIsEmployeePortalEnable() : false);

        // organizationUserRoleMappingRepository.save(roleMapping);
        // log.info("[{}] OrganizationUserRoleMapping saved (employeePortalEnable={})",
        // method, roleMapping.getEmployeePortalEnable());

        // log.info("[{}] Organization Employee Registration Completed Successfully for
        // email: {}",
        // method, userDTO.getUserEmail());
        // }

        @Transactional
        public String organizationEmployeeRegistration(BasicDetailsDTO employeeDTO) {
                String method = "organizationEmployeeRegistration";
                log.info("[{}] Starting Organization Employee Registration", method);
                log.info("[{}] Incoming DTO: {}", method, employeeDTO);

                // 1. Validate organization
                log.info("[{}] Validating organizationId: {}", method, employeeDTO.getOrganizationId());
                Organization org = organizationRepository.findByOrganizationId(employeeDTO.getOrganizationId())
                                .orElseThrow(() -> {
                                        log.error("[{}] Organization not found: {}", method,
                                                        employeeDTO.getOrganizationId());
                                        return new RuntimeException(
                                                        "Organization not found: " + employeeDTO.getOrganizationId());
                                });
                log.info("[{}] Organization found: {}", method, org.getOrganizationName());

                // 2. Create NEW user in Keycloak (not upgrade)
                log.info("[{}] Creating employee user in Keycloak for email: {}", method, employeeDTO.getWorkMail());

                // Create employee in Keycloak; method returns userId (String). generated
                // password may not be available
                String keycloakUserId = keycloakUserService.createEmployeeUserInKeycloak(employeeDTO);
                String generatedPassword = null;

                log.info("[{}] Employee created in Keycloak with ID: {}", method, keycloakUserId);

                // 3. Save employee in DB (CompanyUser table)
                log.info("[{}] Preparing CompanyUser entity for email: {}", method, employeeDTO.getWorkMail());
                CompanyUser employee = new CompanyUser();
                employee.setUserId(keycloakUserId);
                employee.setCompanyName(org.getOrganizationName()); // Use org name as company name
                employee.setUserEmail(employeeDTO.getWorkMail());
                employee.setFirstName(employeeDTO.getFirstName());
                employee.setLastName(employeeDTO.getLastName());
                employee.setPhoneNumber(employeeDTO.getMobile());
                employee.setCountry(org.getState()); // Use org state as country
                employee.setStates(org.getState());

                // Encrypt generated password before storing (if available)
                String encodedPassword = generatedPassword != null ? passwordEncoder.encode(generatedPassword) : null;
                employee.setPassword(encodedPassword);
                log.info("[{}] Password encrypted for email: {}", method, employeeDTO.getWorkMail());

                employee.setToc(true); // Default to true
                companyUserRepository.save(employee);
                log.info("[{}] Employee saved in DB: {}", method, employee.getUserEmail());

                // 4. Save OrganizationUserMapping
                log.info("[{}] Creating OrganizationUserMapping for userId: {}", method, keycloakUserId);
                OrganizationUserMapping mapping = new OrganizationUserMapping();
                mapping.setUserId(keycloakUserId);
                mapping.setOrganizationId(org.getOrganizationId());
                organizationUserMappingRepository.save(mapping);
                log.info("[{}] Mapping saved between Employee and Organization", method);

                // 5. Save OrganizationUserRoleMapping (Employee Portal access only)
                log.info("[{}] Creating OrganizationUserRoleMapping for portal access", method);
                OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
                roleMapping.setUserId(keycloakUserId);
                roleMapping.setOrganizationId(org.getOrganizationId());

                // Role fields optional → can remain NULL
                roleMapping.setRoleId(null);
                roleMapping.setRoleName(null);

                // Employee Portal access → Use from BasicDetailsDTO if available
                Boolean portalEnabled = employeeDTO.getIsPortalEnabled() != null ? employeeDTO.getIsPortalEnabled()
                                : false;
                roleMapping.setEmployeePortalEnable(portalEnabled);

                organizationUserRoleMappingRepository.save(roleMapping);
                log.info("[{}] OrganizationUserRoleMapping saved (employeePortalEnable={})",
                                method, roleMapping.getEmployeePortalEnable());

                log.info("[{}] Organization Employee Registration Completed Successfully for email: {}",
                                method, employeeDTO.getWorkMail());

                return keycloakUserId; // Return userId for BasicDetailsServiceImpl
        }

        // Adapter method to satisfy
        // CompanyUserService.organizationUserRegistration(CompanyUserDTO)
        @Override
        @Transactional(rollbackFor = Exception.class)
        public void organizationUserRegistration(CompanyUserDTO userDTO) {
                // Convert CompanyUserDTO to UserInvitationDTO and delegate to existing
                // implementation
                UserInvitationDTO invitationDTO = new UserInvitationDTO();
                invitationDTO.setOrganizationId(userDTO.getOrganizationId());
                invitationDTO.setRoleId(userDTO.getRoleId());
                invitationDTO.setName((userDTO.getFirstName() != null ? userDTO.getFirstName() : "")
                                + (userDTO.getLastName() != null && !userDTO.getLastName().isEmpty()
                                                ? " " + userDTO.getLastName()
                                                : ""));
                invitationDTO.setEmail(userDTO.getUserEmail());
                invitationDTO.setMobile(userDTO.getPhoneNumber());
                invitationDTO.setInvitationType("INVITE");
                invitationDTO.setIsSuperAdmin(false);
                invitationDTO.setStatus("PENDING");
                invitationDTO.setUserRole(userDTO.getRoleName());
                // Delegate to existing method that performs registration and returns userId
                // (ignored here)
                try {
                        organizationUserRegistration(invitationDTO);
                } catch (Exception ex) {
                        // rethrow to preserve transactional behavior
                        throw ex;
                }
        }

        // Adapter method to satisfy
        // CompanyUserService.organizationEmployeeRegistration(CompanyUserDTO)
        @Override
        @Transactional
        public void organizationEmployeeRegistration(CompanyUserDTO userDTO) {
                // Convert CompanyUserDTO to BasicDetailsDTO and delegate to existing
                // implementation
                BasicDetailsDTO basic = new BasicDetailsDTO();
                basic.setOrganizationId(userDTO.getOrganizationId());
                basic.setWorkMail(userDTO.getUserEmail());
                basic.setFirstName(userDTO.getFirstName());
                basic.setLastName(userDTO.getLastName());
                basic.setMobile(userDTO.getPhoneNumber());
                // Delegate to existing method that performs employee registration and returns
                // userId (ignored here)
                try {
                        organizationEmployeeRegistration(basic);
                } catch (Exception ex) {
                        // rethrow to preserve transactional behavior
                        throw ex;
                }
        }

        @Transactional
        public void toggleEmployeePortalAccess(CompanyUserDTO dto) {
                System.out.println("=== toggleEmployeePortalAccess called ===");
                System.out.println("Payload: " + dto);

                // 1. Validate organization
                Organization org = organizationRepository.findByOrganizationId(dto.getOrganizationId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Organization not found: " + dto.getOrganizationId()));

                // 2. Get user by email
                // CompanyUser companyUser =
                // companyUserRepository.findByUserEmail(dto.getUserEmail())
                // .orElseThrow(() -> new RuntimeException(
                // "User not found with email: " + dto.getUserEmail()));



                // ================= DEBUG START =================
                System.out.println("========================================");
                System.out.println("Incoming Email : " + dto.getUserEmail());

                List<CompanyUser> users =
                        companyUserRepository.findAllByUserEmail(dto.getUserEmail());

                System.out.println("CompanyUser Count = " + users.size());

                users.forEach(u -> System.out.println(
                        "ID=" + u.getId()
                                + ", userId=" + u.getUserId()
                                + ", email=" + u.getUserEmail()));

                System.out.println("========================================");
// ================= DEBUG END =================


                CompanyUser companyUser = companyUserRepository.findAllByUserEmail(dto.getUserEmail()).stream().findFirst()
                                .orElseGet(() -> {


                                        System.out.println(">>>>>>>>>>>>>>> INSIDE orElseGet <<<<<<<<<<<<<<<");
                                        // Create entry if doesn't exist
                                        String keycloakUserId = keycloakUserService
                                                        .getUserIdByEmail(dto.getUserEmail());

                                        CompanyUser newUser = new CompanyUser();
                                        newUser.setUserId(keycloakUserId);
                                        newUser.setUserEmail(dto.getUserEmail());
                                        newUser.setFirstName("User");
                                        newUser.setLastName("");
                                        newUser.setCompanyName(org.getOrganizationName());
                                        newUser.setToc(true);
                                        newUser.setPassword("[SYSTEM]");

                                        return companyUserRepository.save(newUser);
                                });

                String userId = companyUser.getUserId();
                if (userId == null || userId.isEmpty()) {
                        throw new RuntimeException(
                                        "User does not have a linked Keycloak userId: " + dto.getUserEmail());
                }

                // 3. Ensure OrganizationUserMapping exists
                boolean mappingExists = organizationUserMappingRepository
                                .existsByUserIdAndOrganizationId(userId, org.getOrganizationId());

                if (!mappingExists) {
                        OrganizationUserMapping mapping = new OrganizationUserMapping();
                        mapping.setUserId(userId);
                        mapping.setOrganizationId(org.getOrganizationId());
                        organizationUserMappingRepository.save(mapping);
                        System.out.println("Created OrganizationUserMapping for userId=" + userId +
                                        ", org=" + org.getOrganizationId());
                }

                // 4. Check if role mapping exists
                Optional<OrganizationUserRoleMapping> existingRoleMappingOpt = organizationUserRoleMappingRepository
                                .findByUserIdAndOrganizationId(userId, org.getOrganizationId());

                if (existingRoleMappingOpt.isPresent()) {
                        // Update existing mapping
                        OrganizationUserRoleMapping existingRoleMapping = existingRoleMappingOpt.get();
                        existingRoleMapping.setEmployeePortalEnable(dto.getIsEmployeePortalEnable());
                        organizationUserRoleMappingRepository.save(existingRoleMapping);

                        System.out.println("Updated EmployeePortalEnable for userId=" + userId +
                                        " to " + dto.getIsEmployeePortalEnable());
                } else {
                        // Create new role mapping with only portal flag
                        OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
                        roleMapping.setUserId(userId);
                        roleMapping.setOrganizationId(org.getOrganizationId());
                        roleMapping.setRoleId(null);
                        roleMapping.setRoleName(null);
                        roleMapping.setEmployeePortalEnable(dto.getIsEmployeePortalEnable());
                        organizationUserRoleMappingRepository.save(roleMapping);

                        System.out.println("Created new OrganizationUserRoleMapping with portal flag for userId="
                                        + userId);
                }
                
                // --- Revoking Access from BasicDetails and EmployeeInvitation ---
                if (Boolean.FALSE.equals(dto.getIsEmployeePortalEnable())) {
                    basicDetailsRepository.findByOrganization_OrganizationIdAndWorkMail(org.getOrganizationId(), dto.getUserEmail())
                        .ifPresent(basicDetails -> {
                            basicDetails.setPortalEnabled(false);
                            basicDetailsRepository.save(basicDetails);
                            
                            employeeInvitationRepository.findByEmployeeIdAndOrganization_OrganizationId(basicDetails.getEmployeeId(), org.getOrganizationId())
                                .ifPresent(invitation -> {
                                    invitation.setIsPortalEnabled(false);
                                    invitation.setIsInvitationAccepted(false);
                                    employeeInvitationRepository.save(invitation);
                                });
                        });
                    keycloakUserService.setUserEnabled(dto.getUserEmail(), false);
                }
                
                if (Boolean.TRUE.equals(dto.getIsEmployeePortalEnable())) {
                    basicDetailsRepository.findByOrganization_OrganizationIdAndWorkMail(org.getOrganizationId(), dto.getUserEmail())
                        .ifPresent(basicDetails -> {
                            basicDetails.setPortalEnabled(true);
                            basicDetailsRepository.save(basicDetails);
                            
                            employeeInvitationRepository.findByEmployeeIdAndOrganization_OrganizationId(basicDetails.getEmployeeId(), org.getOrganizationId())
                                .ifPresent(invitation -> {
                                    invitation.setIsPortalEnabled(true);
                                    employeeInvitationRepository.save(invitation);
                                });
                        });
                    keycloakUserService.setUserEnabled(dto.getUserEmail(), true);

                        try {
                                // Generate new password for the employee
                                String generatedPassword = PasswordGeneratorUtil.generatePassword();

                                // Update password in Keycloak
                                keycloakUserService.updateUserPassword(dto.getUserEmail(), generatedPassword);

                                // Send credentials email
                                boolean emailSent = emailService.sendEmployeeCredentialsEmail(
                                                dto.getUserEmail(),
                                                companyUser.getFirstName() + " " + companyUser.getLastName(),
                                                generatedPassword);

                                if (emailSent) {
                                        System.out.println(
                                                        "✅ Employee credentials email sent to: " + dto.getUserEmail());
                                } else {
                                        System.out.println("⚠️ Failed to send credentials email to: "
                                                        + dto.getUserEmail());
                                }
                        } catch (Exception e) {
                                System.out.println("❌ Error sending credentials email: " + e.getMessage());
                                // Don't throw - email failure shouldn't break portal access
                        }
                }

                System.out.println("=== Employee portal access updated ===");
        }

        @Override
        public List<CompanyUserDTO> getHrUsersByOrganization(String organizationId) {

                String method = "getHrUsersByOrganization";
                log.info("[{}]  Fetching HR role mappings | orgId={}", method, organizationId);

                // Step 1: Fetch Hr role mappings
                List<OrganizationUserRoleMapping> hrMappings = organizationUserRoleMappingRepository
                                .findByOrganizationIdAndRoleName(organizationId, "HR");

                log.info("[{}]  Found {} HR role mappings", method, hrMappings.size());

                // Step 2: Extract userIds
                List<String> userIds = hrMappings.stream()
                                .map(OrganizationUserRoleMapping::getUserId)
                                .distinct()
                                .toList();

                log.info("[{}]  Extracted {} unique HR userIds", method, userIds.size());

                if (userIds.isEmpty()) {
                        log.warn("[{}]  No HR users found for organizationId={}", method, organizationId);
                        return List.of();
                }

                // Step 3: Fetch company users
                List<CompanyUser> users = companyUserRepository.findByUserIdIn(userIds);
                log.info("[{}]  Loaded {} company user records", method, users.size());

                // Step 4: Convert to DTO
                List<CompanyUserDTO> dtoList = users.stream()
                                .map(CompanyUserMapper::toDTO)
                                .collect(Collectors.toList());

                log.info("[{}]  Converted company users to DTO list (size={})", method, dtoList.size());

                return dtoList;
        }

        // Local EmailService interface to satisfy compilation if top-level service is
        // missing
        public interface EmailService {
                boolean sendUserCredentialsEmail(String email, String name, String password);

                boolean sendEmployeeCredentialsEmail(String email, String name, String password);
        }
}
