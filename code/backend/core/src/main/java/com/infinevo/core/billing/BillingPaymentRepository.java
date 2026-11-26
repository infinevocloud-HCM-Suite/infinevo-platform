package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link BillingPayment} (W-94.1). */
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {

    List<BillingPayment> findByInvoiceIdAndTenantIdOrderByPaymentDateAscCreatedAtAsc(UUID invoiceId, UUID tenantId);

    boolean existsByTenantIdAndTransactionReference(UUID tenantId, String transactionReference);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM BillingPayment p "
            + "WHERE p.invoiceId = :invoiceId AND p.tenantId = :tenantId AND p.status = 'COMPLETED'")
    BigDecimal sumCompletedAmount(@Param("invoiceId") UUID invoiceId, @Param("tenantId") UUID tenantId);
}
