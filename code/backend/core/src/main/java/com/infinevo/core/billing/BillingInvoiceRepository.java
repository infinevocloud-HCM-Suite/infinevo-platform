package com.infinevo.core.billing;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link BillingInvoice} (W-94.1). */
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {

    @EntityGraph(attributePaths = "items")
    List<BillingInvoice> findByTenantIdOrderByInvoiceDateDescCreatedAtDesc(UUID tenantId);

    @EntityGraph(attributePaths = "items")
    Optional<BillingInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM BillingInvoice i WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<BillingInvoice> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
