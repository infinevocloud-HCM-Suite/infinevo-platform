package com.infinevo.core.tenant.repository;

import com.infinevo.core.tenant.entity.TenantEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
    Optional<TenantEntity> findByTenantId(UUID tenantId);
}
