package com.infinevo.core.billing;

import java.util.List;
import java.util.UUID;

/** Service contract for subscription GST invoice generation and queries (W-94.1). */
public interface BillingInvoiceService {

    BillingInvoiceResponse generate(GenerateInvoiceRequest request);

    BillingInvoiceResponse getInvoice(UUID invoiceId);

    List<BillingInvoiceResponse> listInvoices(UUID tenantIdFilter);
}
