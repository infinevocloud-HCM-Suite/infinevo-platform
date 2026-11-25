package com.infinevo.payroll.payrun.bankfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankFileTemplateRepository extends JpaRepository<BankFileTemplate, UUID> {

    List<BankFileTemplate> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<BankFileTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
