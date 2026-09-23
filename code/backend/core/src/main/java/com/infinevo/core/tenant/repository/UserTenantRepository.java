package com.infinevo.core.tenant.repository;

import com.infinevo.core.tenant.entity.UserTenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTenantRepository extends JpaRepository<UserTenant, UUID> {
    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);
}
