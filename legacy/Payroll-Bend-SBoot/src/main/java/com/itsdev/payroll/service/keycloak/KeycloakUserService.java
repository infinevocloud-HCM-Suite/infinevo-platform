package com.itsdev.payroll.service.keycloak;

import java.time.LocalDateTime;

import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;

public interface KeycloakUserService {
    String createUserInKeycloak(CompanyUserDTO userDTO);

    void updateUserPassword(String email, String newPassword);

    boolean isEmailExists(String email);

    // String createTemporaryUserInKeycloak(String email);

    String createTemporaryUserInKeycloak(UserInvitationDTO userDTO);

    String createEmployeeUserInKeycloak(BasicDetailsDTO employeeDTO);

    boolean isTemporaryUser(String email);

    String upgradeTemporaryUserInKeycloak(CompanyUserDTO userDTO);

    String getUserIdByEmail(String email);

    // NEW: Get last login time from Keycloak sessions
    LocalDateTime getLastLoginTime(String userId);

    boolean checkEmailInKeycloak(String email);

    void setUserEnabled(String email, boolean enabled);

    void updateUserEmail(String userId, String newEmail);
}
