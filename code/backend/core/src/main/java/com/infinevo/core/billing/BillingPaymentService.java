package com.infinevo.core.billing;

import java.util.List;
import java.util.UUID;

/** Service contract for recording and reading subscription invoice payments (W-94.1). */
public interface BillingPaymentService {

    BillingPaymentResponse recordPayment(UUID invoiceId, RecordPaymentRequest request);

    List<BillingPaymentResponse> listPayments(UUID invoiceId);
}
