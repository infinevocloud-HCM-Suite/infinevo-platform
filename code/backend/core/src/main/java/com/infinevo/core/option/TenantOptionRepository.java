package com.infinevo.core.option;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantOptionRepository extends JpaRepository<TenantOption, UUID> {
    Optional<TenantOption> findByTenantIdAndOptionKey(UUID tenantId, String optionKey);

    List<TenantOption> findByTenantId(UUID tenantId);

    void deleteByTenantIdAndOptionKey(UUID tenantId, String optionKey);
}
