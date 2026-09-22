package com.infinevo.core.identity.repository;

import com.infinevo.core.identity.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for core.user_account.
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByKeycloakSub(String keycloakSub);

    Optional<UserAccount> findByTenantIdAndEmail(UUID tenantId, String email);
}
