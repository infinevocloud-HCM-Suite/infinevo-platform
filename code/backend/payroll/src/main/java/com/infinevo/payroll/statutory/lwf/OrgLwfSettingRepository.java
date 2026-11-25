package com.infinevo.payroll.statutory.lwf;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrgLwfSettingRepository extends JpaRepository<OrgLwfSetting, UUID> {

    Optional<OrgLwfSetting> findByTenantIdAndStateCode(UUID tenantId, String stateCode);

    List<OrgLwfSetting> findAllByTenantId(UUID tenantId);
}
