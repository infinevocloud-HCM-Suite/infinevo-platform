package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank file templates (W-88.2 spec section 4.4). Reading needs {@code payroll.bank_file.export}, the same action
 * that makes a file; changing a template needs {@code payroll.settings.manage}.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/bank-file-templates")
public class BankFileTemplateController {

    private final BankFileTemplateService service;

    public BankFileTemplateController(BankFileTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresAction("payroll.bank_file.export")
    public List<BankFileTemplateResponse> list() {
        return service.list();
    }

    @GetMapping("/fields")
    @RequiresAction("payroll.bank_file.export")
    public List<BankFileFieldResponse> fields() {
        return service.fields();
    }

    @GetMapping("/{id}")
    @RequiresAction("payroll.bank_file.export")
    public BankFileTemplateResponse get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<BankFileTemplateResponse> create(@RequestBody BankFileTemplateRequest request) {
        BankFileTemplateResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/bank-file-templates/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @RequiresAction("payroll.settings.manage")
    public BankFileTemplateResponse update(
            @PathVariable("id") String id, @RequestBody BankFileTemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/copy")
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<BankFileTemplateResponse> copy(@PathVariable("id") String id) {
        BankFileTemplateResponse created = service.copy(id);
        return ResponseEntity.created(URI.create("/api/v1/payroll/bank-file-templates/" + created.id()))
                .body(created);
    }

    @ExceptionHandler(BankFileTemplateException.class)
    public ResponseEntity<ApiErrorResponse> handleTemplate(BankFileTemplateException e) {
        return e.kind() == BankFileTemplateException.Kind.NOT_FOUND
                ? error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage())
                : error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId));
    }
}
