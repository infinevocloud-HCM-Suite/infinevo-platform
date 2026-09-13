package com.itsdev.payroll.serviceimpl.keycloak;

import com.itsdev.payroll.config.KeycloakAdminConfig;
import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;
import com.itsdev.payroll.repository.CompanyUserRepository;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.organization.UserInvitationRepository;
import com.itsdev.payroll.service.keycloak.KeycloakUserService;
import com.itsdev.payroll.serviceimpl.employee.BasicDetailsServiceImpl;
import com.itsdev.payroll.util.PasswordGeneratorUtil;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.representations.idm.UserSessionRepresentation;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class KeycloakUserServiceImpl implements KeycloakUserService {

    @Autowired
    private KeycloakAdminConfig config;

    @Autowired
    private Keycloak keycloak;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired // ← ADD THIS
    private BasicDetailsRepository basicDetailsRepository;

    @Autowired // ← ADD THIS
    private UserInvitationRepository invitationRepository;

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserServiceImpl.class);

    // for getting last login time
    @Override
    public LocalDateTime getLastLoginTime(String userId) {
        String method = "getLastLoginTime";
        log.info("[{}] 🔍 Fetching last login from user sessions for userId: {}", method, userId);

        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            // Get user sessions - these contain actual session start times
            List<UserSessionRepresentation> sessions = usersResource.get(userId).getUserSessions();

            if (sessions == null || sessions.isEmpty()) {
                log.info("[{}] ℹ️ No active sessions found for userId: {}", method, userId);
                return null;
            }

            // Sort by start time (most recent first)
            sessions.sort(Comparator.comparingLong(UserSessionRepresentation::getStart).reversed());

            UserSessionRepresentation latestSession = sessions.get(0);
            long sessionStartTime = latestSession.getStart();

            LocalDateTime lastLogin = Instant.ofEpochMilli(sessionStartTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            log.info("[{}] ✅ Last login found: {} (session: {})",
                    method, lastLogin, latestSession.getId());

            return lastLogin;

        } catch (Exception e) {
            log.error("[{}] ❌ Error fetching user sessions for userId={}: {}",
                    method, userId, e.getMessage(), e);
            // Return null instead of throwing exception to avoid breaking the entire list
            return null;
        }
    }

    @Override
    public String createUserInKeycloak(CompanyUserDTO userDTO) {
        try {
            System.out.println("Starting user creation in Keycloak for: " + userDTO.getUserEmail());

            System.out.println("Keycloak instance initialized.");

            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setEnabled(true);
            kcUser.setUsername(userDTO.getUserEmail());
            kcUser.setEmail(userDTO.getUserEmail());
            kcUser.setFirstName(userDTO.getFirstName());
            kcUser.setLastName(userDTO.getLastName());
            kcUser.setEmailVerified(true);
            kcUser.setRequiredActions(List.of());

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(userDTO.getPassword());

            kcUser.setCredentials(List.of(credential));

            System.out.println("UserRepresentation and credentials set for: " + userDTO.getUserEmail());

            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            System.out.println("Sending request to create user in realm: " + config.getRealm());
            Response response = usersResource.create(kcUser);

            System.out.println("response: " + response);

            if (response.getStatus() != 201) {
                String errorResponse = response.readEntity(String.class);
                System.out.println("Failed to create Keycloak user. Status: " + response.getStatus());
                System.out.println("Error: " + errorResponse);
                throw new RuntimeException("Keycloak user creation failed. Status: " +
                        response.getStatus() + ", Error: " + errorResponse);
            }

            // Get user ID from the response location header
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            System.out.println("User created in Keycloak successfully. User ID: " + userId);
            return userId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Keycloak user: " + e.getMessage(), e);
        }
    }

    public void updateUserPassword(String email, String newPassword) {
        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            // Find user by email exactly
            List<UserRepresentation> users = usersResource.search(email, true);
            if (users.isEmpty()) {
                throw new RuntimeException("User with email " + email + " not found.");
            }

            String userId = users.get(0).getId();

            // Set new password
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            usersResource.get(userId).resetPassword(credential);

            System.out.println("Password updated successfully for user: " + email);

        } catch (Exception e) {
            throw new RuntimeException("Failed to update password for user " + email + ": " + e.getMessage(), e);
        }
    }

    // @Override
    // public boolean isEmailExists(String email) {
    // boolean inDb = companyUserRepository.existsByUserEmail(email);
    // boolean inKeycloak = !keycloak.realm(config.getRealm()).users().search(email,
    // 0, 1).isEmpty();

    // // Internal logging/debugging
    // System.out.println("Email " + email + " -> In DB: " + inDb + ", In Keycloak:
    // " + inKeycloak);

    // return inDb && inKeycloak;
    // }

    @Override
    public boolean isEmailExists(String email) {
        // Check ALL database tables where email can exist
        boolean inCompanyUser = companyUserRepository.existsByUserEmail(email);

        // For BasicDetails - check by workMail (use findByWorkMail, not
        // existsByWorkMail)
        boolean inBasicDetails = basicDetailsRepository.findByWorkMail(email).isPresent();

        // For UserInvitation - need to check ALL organizations, not just one
        // Since existsByEmail doesn't exist, use a custom query or check all
        // invitations
        boolean inUserInvitation = !invitationRepository.findAll().stream()
                .filter(inv -> email.equals(inv.getEmail()))
                .findFirst()
                .isEmpty();

        // Email exists in DB if it's in ANY table
        boolean inDb = inCompanyUser || inBasicDetails || inUserInvitation;

        boolean inKeycloak = !keycloak.realm(config.getRealm()).users().search(email, 0, 1).isEmpty();

        System.out.println("Email " + email + " -> In CompanyUser: " + inCompanyUser +
                ", In BasicDetails: " + inBasicDetails +
                ", In UserInvitation: " + inUserInvitation +
                ", In DB (Overall): " + inDb +
                ", In Keycloak: " + inKeycloak);

        return inDb && inKeycloak;
    }

    @Override
    public String getUserIdByEmail(String email) {
        String method = "getUserIdByEmail";
        log.info("[{}] 🔍 Fetching Keycloak userId for email: {}", method, email);

        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(email, true);
            if (users.isEmpty()) {
                log.error("[{}] ❌ No Keycloak user found for email: {}", method, email);
                throw new RuntimeException("No Keycloak user found for email: " + email);
            }

            UserRepresentation user = users.get(0);
            log.info("[{}] ✅ Found Keycloak userId={} for email={}", method, user.getId(), email);
            return user.getId();

        } catch (Exception e) {
            log.error("[{}] ❌ Error fetching Keycloak userId for email={}: {}", method, email, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch Keycloak userId for email: " + email, e);
        }
    }

    /**
     * Create a temporary user with only email.
     */
    // @Override
    // public String createTemporaryUserInKeycloak(String email) {
    // try {
    // System.out.println("Starting temporary user creation in Keycloak for: " +
    // email);

    // UserRepresentation kcUser = new UserRepresentation();
    // kcUser.setEnabled(true);
    // kcUser.setUsername(email);
    // kcUser.setEmail(email);
    // kcUser.setEmailVerified(false); // not verified initially

    // RealmResource realmResource = keycloak.realm(config.getRealm());
    // UsersResource usersResource = realmResource.users();

    // Response response = usersResource.create(kcUser);

    // if (response.getStatus() != 201) {
    // String errorResponse = response.readEntity(String.class);
    // throw new RuntimeException("Temporary Keycloak user creation failed. Status:"
    // +
    // response.getStatus() + ", Error: " + errorResponse);
    // }

    // String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$",
    // "$1");
    // System.out.println("Temporary user created in Keycloak successfully. User
    // ID:" + userId);
    // return userId;

    // } catch (Exception e) {
    // throw new RuntimeException("Failed to create temporary Keycloak user: " +
    // e.getMessage(), e);
    // }
    // }

    /**
     * Create a temporary user for organization users (updated with
     * UserInvitationDTO)
     */
    @Override
    public String createTemporaryUserInKeycloak(UserInvitationDTO userDTO) {
        try {
            System.out.println("Starting temporary user creation in Keycloak for: " + userDTO.getEmail());

            String generatedPassword = PasswordGeneratorUtil.generatePassword();

            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setEnabled(true);
            kcUser.setUsername(userDTO.getEmail());
            kcUser.setEmail(userDTO.getEmail());
            kcUser.setFirstName(userDTO.getName());
            kcUser.setLastName("");
            kcUser.setEmailVerified(true);
            kcUser.setRequiredActions(List.of());

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(generatedPassword);

            kcUser.setCredentials(List.of(credential));

            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            System.out.println("Sending request to create user in realm: " + config.getRealm());
            Response response = usersResource.create(kcUser);

            // ✅ SUCCESS: user created
            if (response.getStatus() == 201) {
                String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                System.out.println("User created in Keycloak successfully. User ID: " + userId);
                return userId;
            }

            // ✅ KEY FIX: user already exists → reuse
            if (response.getStatus() == 409) {
                System.out.println("User already exists in Keycloak. Fetching existing userId for email: "
                        + userDTO.getEmail());
                return getUserIdByEmail(userDTO.getEmail());
            }

            // ❌ Unexpected status
            String errorResponse = response.readEntity(String.class);
            throw new RuntimeException(
                    "Keycloak user creation failed. Status: " + response.getStatus() +
                            ", Error: " + errorResponse);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create or fetch Keycloak user: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a user is temporary by validating profile completeness.
     * Temporary = no firstname, no lastname, no password
     */
    @Override
    public boolean isTemporaryUser(String email) {
        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(email, true);
            if (users.isEmpty()) {
                throw new RuntimeException("User not found with email: " + email);
            }

            UserRepresentation user = users.get(0);

            boolean missingName = (user.getFirstName() == null || user.getFirstName().isBlank()) &&
                    (user.getLastName() == null || user.getLastName().isBlank());

            // Check credentials
            boolean missingPassword = true;
            try {
                List<CredentialRepresentation> storedCreds = realmResource.users()
                        .get(user.getId()).credentials();
                if (storedCreds != null && !storedCreds.isEmpty()) {
                    missingPassword = false;
                }
            } catch (Exception ignored) {
            }

            return missingName && missingPassword;

        } catch (Exception e) {
            throw new RuntimeException("Failed to check temporary user: " + e.getMessage(), e);
        }
    }

    @Override
    public String upgradeTemporaryUserInKeycloak(CompanyUserDTO userDTO) {
        String method = "upgradeTemporaryUserInKeycloak";
        log.info("[{}] 🔹 Start upgrading temporary Keycloak user for email: {}", method, userDTO.getUserEmail());

        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            log.info("[{}] 🔍 Searching Keycloak user by email: {}", method, userDTO.getUserEmail());
            List<UserRepresentation> users = usersResource.search(userDTO.getUserEmail(), true);

            if (users.isEmpty()) {
                log.info("[{}] ❌ No temporary user found in Keycloak for email: {}", method, userDTO.getUserEmail());
                throw new RuntimeException("No temporary user found with email: " + userDTO.getUserEmail());
            }

            UserRepresentation user = users.get(0);
            log.info("[{}] ✅ Temporary user found. UserID: {}", method, user.getId());

            UserResource userResource = usersResource.get(user.getId());

            // --- Update profile ---
            log.info("[{}] ✏️ Updating firstName={}, lastName={} and marking email as verified",
                    method, userDTO.getFirstName(), userDTO.getLastName());
            user.setFirstName(userDTO.getFirstName());
            user.setLastName(userDTO.getLastName());
            user.setEmailVerified(true);
            user.setRequiredActions(List.of());

            // --- Set password ---
            log.info("[{}] 🔒 Setting password for user", method);
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(userDTO.getPassword());
            user.setCredentials(List.of(credential));

            // --- Update user in Keycloak ---
            log.info("[{}] 🔄 Sending update request to Keycloak for userID: {}", method, user.getId());
            userResource.update(user);

            log.info("[{}] ✅ Temporary user successfully upgraded in Keycloak. UserID: {}", method, user.getId());
            return user.getId();

        } catch (Exception e) {
            log.info("[{}] ❌ Failed to upgrade temporary Keycloak user for email: {}. Error: {}",
                    method, userDTO.getUserEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to upgrade temporary Keycloak user: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Keycloak user for employees with BasicDetailsDTO
     */
    @Override
    public String createEmployeeUserInKeycloak(BasicDetailsDTO employeeDTO) {
        try {
            System.out.println("Starting employee user creation in Keycloak for: " + employeeDTO.getWorkMail());

            // Generate password using utility
            String generatedPassword = PasswordGeneratorUtil.generatePassword();

            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setEnabled(true);
            kcUser.setUsername(employeeDTO.getWorkMail());
            kcUser.setEmail(employeeDTO.getWorkMail());
            kcUser.setFirstName(employeeDTO.getFirstName());
            kcUser.setLastName(employeeDTO.getLastName());
            kcUser.setEmailVerified(true);
            kcUser.setRequiredActions(List.of());

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false); // User must change on first login
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(generatedPassword);

            kcUser.setCredentials(List.of(credential));

            System.out.println("UserRepresentation and credentials set for: " + employeeDTO.getWorkMail());
            System.out.println("Generated password for: " + employeeDTO.getWorkMail());

            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            System.out.println("Sending request to create user in realm: " + config.getRealm());
            Response response = usersResource.create(kcUser);

            System.out.println("response: " + response);

            if (response.getStatus() != 201) {
                String errorResponse = response.readEntity(String.class);
                System.out.println("Failed to create Keycloak user. Status: " + response.getStatus());
                System.out.println("Error: " + errorResponse);
                throw new RuntimeException("Keycloak user creation failed. Status: " +
                        response.getStatus() + ", Error: " + errorResponse);
            }

            // Get user ID from the response location header
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            System.out.println("User created in Keycloak successfully. User ID: " + userId);
            return userId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Keycloak user: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkEmailInKeycloak(String email) {
        try {
            List<UserRepresentation> users = keycloak.realm(config.getRealm())
                    .users()
                    .search(email, true);
            return !users.isEmpty();
        } catch (Exception e) {
            log.error("Error checking email in Keycloak: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void setUserEnabled(String email, boolean enabled) {
        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(email, true);
            if (!users.isEmpty()) {
                String userId = users.get(0).getId();
                UserResource userResource = usersResource.get(userId);
                UserRepresentation kcUser = userResource.toRepresentation();
                kcUser.setEnabled(enabled);
                userResource.update(kcUser);
                log.info("Set user enabled status to {} in Keycloak for email: {}", enabled, email);
            } else {
                log.warn("User not found in Keycloak to toggle status: {}", email);
            }
        } catch (Exception e) {
            log.error("Failed to update user enabled status in Keycloak for email={}: {}", email, e.getMessage(), e);
        }
    }

    @Override
    public void updateUserEmail(String userId, String newEmail) {
        String method = "updateUserEmail";
        log.info("[{}] 🔄 Updating Keycloak email for userId={} to newEmail={}", method, userId, newEmail);
        try {
            RealmResource realmResource = keycloak.realm(config.getRealm());
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation kcUser = userResource.toRepresentation();
            kcUser.setEmail(newEmail);
            kcUser.setUsername(newEmail); // Keycloak username = email convention
            kcUser.setEmailVerified(true);
            userResource.update(kcUser);
            log.info("[{}] ✅ Keycloak email updated successfully for userId={}", method, userId);
        } catch (Exception e) {
            log.error("[{}] ❌ Failed to update Keycloak email for userId={}: {}", method, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update Keycloak user email for userId: " + userId, e);
        }
    }
}

