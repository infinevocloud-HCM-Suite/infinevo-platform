package com.infinevo.core.billing;

import com.infinevo.core.subscription.SubscriptionNotFoundException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for subscription GST invoices and payment recording (W-94.1).
 */
@RestController
@RequestMapping("/api/v1/core/billing/invoices")
public class BillingInvoiceController {

    private final BillingInvoiceService invoiceService;
    private final BillingPaymentService paymentService;

    public BillingInvoiceController(BillingInvoiceService invoiceService, BillingPaymentService paymentService) {
        this.invoiceService = Objects.requireNonNull(invoiceService, "invoiceService must not be null");
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
    }

    @PostMapping("/generate")
    @RequiresAction("core.billing.manage")
    public ResponseEntity<BillingInvoiceResponse> generate(@RequestBody GenerateInvoiceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        BillingInvoiceResponse created = invoiceService.generate(request);
        return ResponseEntity.created(URI.create("/api/v1/core/billing/invoices/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @RequiresAction("core.tenant.read")
    public ResponseEntity<BillingInvoiceResponse> getInvoice(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id));
    }

    @GetMapping
    @RequiresAction("core.tenant.read")
    public ResponseEntity<List<BillingInvoiceResponse>> listInvoices(
            @RequestParam(value = "tenantId", required = false) UUID tenantId) {
        return ResponseEntity.ok(invoiceService.listInvoices(tenantId));
    }

    @PostMapping("/{id}/payments")
    @RequiresAction("core.billing.manage")
    public ResponseEntity<BillingPaymentResponse> recordPayment(
            @PathVariable("id") UUID id, @RequestBody RecordPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        BillingPaymentResponse created = paymentService.recordPayment(id, request);
        return ResponseEntity.created(URI.create("/api/v1/core/billing/invoices/" + id + "/payments"))
                .body(created);
    }

    @GetMapping("/{id}/payments")
    @RequiresAction("core.tenant.read")
    public ResponseEntity<List<BillingPaymentResponse>> listPayments(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(paymentService.listPayments(id));
    }

    @ExceptionHandler(BillingInvoiceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleInvoiceNotFound(BillingInvoiceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(BillingConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(BillingConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
