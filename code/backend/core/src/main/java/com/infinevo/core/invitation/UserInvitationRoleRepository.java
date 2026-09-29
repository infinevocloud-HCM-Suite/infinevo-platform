package com.infinevo.core.invitation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link UserInvitationRole}.
 */
@Repository
public interface UserInvitationRoleRepository extends JpaRepository<UserInvitationRole, UUID> {

    List<UserInvitationRole> findByTenantIdAndInvitationId(UUID tenantId, UUID invitationId);

    void deleteByTenantIdAndInvitationId(UUID tenantId, UUID invitationId);
}
