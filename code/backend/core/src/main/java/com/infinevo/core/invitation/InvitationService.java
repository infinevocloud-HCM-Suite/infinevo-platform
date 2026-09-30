package com.infinevo.core.invitation;

import java.util.List;
import java.util.UUID;

/**
 * Service managing user and employee invitations (W-24.2, spec section 4).
 */
public interface InvitationService {

    UserInvitationResponse createUserInvitation(UserInvitationRequest request, UUID actorUserId);

    List<UserInvitationResponse> listUserInvitations(InvitationStatus status);

    UserInvitationResponse resendUserInvitation(UUID invitationId, UUID actorUserId);

    void revokeUserInvitation(UUID invitationId, UUID actorUserId);

    EmployeeInvitationResponse createEmployeeInvitation(EmployeeInvitationRequest request, UUID actorUserId);

    List<EmployeeInvitationResponse> listEmployeeInvitations(InvitationStatus status, UUID employeeId);

    EmployeeInvitationResponse resendEmployeeInvitation(UUID invitationId, UUID actorUserId);

    void revokeEmployeeInvitation(UUID invitationId, UUID actorUserId);

    void acceptInvitation(String token);

    void declineInvitation(String token, String reason);
}
