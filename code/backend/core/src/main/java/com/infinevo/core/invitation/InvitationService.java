package com.infinevo.core.invitation;

import java.util.List;
import java.util.UUID;

/**
 * Service managing user and employee invitations (W-24.2, spec section 4).
 */
public interface InvitationService {

    UserInvitationResponse createUserInvitation(UserInvitationRequest request, UUID actorUserId);

    /**
     * True when an invitation email can be composed: {@code invitation.link.base-url} is set. Without it an
     * invitation row is written but no email ever leaves, so callers that promise an email check this first.
     */
    boolean canSendInvitationEmail();

    List<UserInvitationResponse> listUserInvitations(InvitationStatus status);

    UserInvitationResponse resendUserInvitation(UUID invitationId, UUID actorUserId);

    void revokeUserInvitation(UUID invitationId, UUID actorUserId);

    EmployeeInvitationResponse createEmployeeInvitation(EmployeeInvitationRequest request, UUID actorUserId);

    List<EmployeeInvitationResponse> listEmployeeInvitations(InvitationStatus status, UUID employeeId);

    EmployeeInvitationResponse resendEmployeeInvitation(UUID invitationId, UUID actorUserId);

    void revokeEmployeeInvitation(UUID invitationId, UUID actorUserId);

    /**
     * The employee's portal access (W-73.3 §4): {@code ACTIVE} when linked to an account, with its roles;
     * {@code INVITED} when a live pending invitation exists, with the roles acceptance will grant;
     * otherwise {@code NONE}.
     *
     * @throws IllegalArgumentException when the employee does not exist in the current tenant
     */
    EmployeeAccessResponse employeeAccess(UUID employeeId);

    /**
     * The live, active employees of the bound tenant who have a work email, no linked account and no live
     * pending invitation — whom "Invite all without access" invites (W-73.7). Ordered by employee number.
     * Each is then invited through {@link #createEmployeeInvitation}, one transaction each.
     */
    List<UUID> employeesWithoutAccess();

    /**
     * Accepts, sets the password the invitee chose (D-88) and returns what they do next — the accept page
     * words its message from it (D-62).
     *
     * @throws PasswordPolicyException if Keycloak refuses the password; the invitation stays {@code PENDING}
     */
    AcceptOutcome acceptInvitation(String token, String password);

    void declineInvitation(String token, String reason);
}
