package com.itsdev.payroll.service.organization;

import com.itsdev.payroll.dto.organization.CombinedUserDTO;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;

import java.util.List;

public interface UserInvitationService {
    UserInvitationDTO createInvitation(String organizationId, UserInvitationDTO dto);

    UserInvitationDTO updateInvitation(String organizationId, String userId, UserInvitationDTO dto);

    UserInvitationDTO getInvitation(String organizationId, String userId);

    List<UserInvitationDTO> getAllInvitations(String organizationId);

    void deleteInvitation(String organizationId, String userId);

    void inactivateInvitation(String organizationId, String userId);

    void reactivateInvitation(String organizationId, String userId);

    // NEW METHOD: Get combined users and employees with login info
    List<CombinedUserDTO> getAllUsersWithLoginInfo(String organizationId, String currentUserId);

    // Add this method to UserInvitationService interface
    UserInvitationDTO createSuperAdminInvitation(String organizationId, String email, String name, String userId,
            Boolean isEditable, Boolean isInvitationAccepted);

    // UserInvitationDTO findByAcceptanceToken(String token);

    // void markAsAccepted(String token);

    // void markAsRejected(String token);

    UserInvitationDTO findByEmailAndOrganization(String email, String organizationId);

    void markAsAccepted(String email, String organizationId);

    void markAsRejected(String email, String organizationId, String rejectionReason);

    void markAsRejected(String token, String rejectionReason); // Overloaded with reason

    void markAsAccepted(String acceptanceToken);

    UserInvitationDTO findByAcceptanceToken(String acceptanceToken);
}