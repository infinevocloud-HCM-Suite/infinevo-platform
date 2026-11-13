package com.infinevo.payroll.statutory.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the company TDS switch (D-141).
 */
public interface TdsSettingRepository extends JpaRepository<TdsSetting, UUID> {

    Optional<TdsSetting> findByTenantId(UUID tenantId);
}
