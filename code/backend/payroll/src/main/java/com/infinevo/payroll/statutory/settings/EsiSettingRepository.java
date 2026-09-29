package com.infinevo.payroll.statutory.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for ESI settings (W-31.1).
 */
public interface EsiSettingRepository extends JpaRepository<EsiSetting, UUID> {

    Optional<EsiSetting> findByTenantId(UUID tenantId);
}
