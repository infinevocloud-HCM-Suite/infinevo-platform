package com.infinevo.core.report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.report_definition} (W-23.1). Every read names the tenant — the rule
 * {@code RoleRepository} follows, for the same reasons.
 */
@Transactional(readOnly = true)
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {

    Optional<ReportDefinition> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ReportDefinition> findByTenantIdOrderByCodeAsc(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
