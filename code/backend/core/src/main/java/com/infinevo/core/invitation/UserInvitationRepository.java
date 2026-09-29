package com.infinevo.core.invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link UserInvitation}.
 */
@Repository
public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    List<UserInvitation> findByTenantId(UUID tenantId);

    List<UserInvitation> findByTenantIdAndStatus(UUID tenantId, InvitationStatus status);

    Optional<UserInvitation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    @Query(
            "SELECT u FROM UserInvitation u WHERE u.tenantId = :tenantId AND u.email = :email AND u.status = com.infinevo.core.invitation.InvitationStatus.PENDING AND u.expiresAt > :now")
    Optional<UserInvitation> findActivePendingByEmail(
            @Param("tenantId") UUID tenantId, @Param("email") String email, @Param("now") Instant now);

    @Query(value = "SELECT * FROM core.find_user_invitation_by_token_hash(:tokenHash)", nativeQuery = true)
    Optional<UserInvitation> findByTokenHashSecurityDefiner(@Param("tokenHash") String tokenHash);
}
