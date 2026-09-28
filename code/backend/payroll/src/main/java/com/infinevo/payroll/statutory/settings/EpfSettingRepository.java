package com.infinevo.payroll.statutory.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for EPF settings (W-31.1).
 */
public interface EpfSettingRepository extends JpaRepository<EpfSetting, UUID> {

    Optional<EpfSetting> findByTenantId(UUID tenantId);
}
