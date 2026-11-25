package com.infinevo.payroll.payrun.bankfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankFileExportRepository extends JpaRepository<BankFileExport, UUID> {

    List<BankFileExport> findByTenantIdAndPayrunIdOrderByCreatedAtDesc(UUID tenantId, UUID payrunId);

    Optional<BankFileExport> findByIdAndTenantIdAndPayrunId(UUID id, UUID tenantId, UUID payrunId);
}
