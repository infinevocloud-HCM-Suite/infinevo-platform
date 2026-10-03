package com.infinevo.core.overtime;

import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/overtime} (W-39.2 §4). {@code manage} and {@code read} are two codes, not one —
 * {@code core.attendance}'s guard, and {@code PayInputController}'s reason: a caller holding only
 * {@code core.overtime.read} must not be able to record or cancel an entry.
 *
 * <p>Thin by rule: unpack, delegate, repack ({@code docs/CONVENTIONS.md} section 3). Local
 * exception handlers, no global advice, the same reason {@code PayInputController} gives.
 */
@RestController
@RequestMapping("/api/v1/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = Objects.requireNonNull(overtimeService, "overtimeService must not be null");
    }

    @PostMapping
    @RequiresAction("core.overtime.manage")
    public ResponseEntity<OvertimeResponse> record(@RequestBody OvertimeEntry entry) {
        OvertimeResponse created = overtimeService.record(entry);
        return ResponseEntity.created(URI.create("/api/v1/overtime/" + created.id()))
                .body(created);
    }

    @GetMapping
    @RequiresAction("core.overtime.read")
    public List<OvertimeResponse> list(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to,
            @RequestParam(value = "employeeId", required = false) UUID employeeId) {
        return overtimeService.list(from, to, employeeId);
    }

    @DeleteMapping("/{id}")
    @RequiresAction("core.overtime.manage")
    public ResponseEntity<Void> cancel(@PathVariable("id") UUID id) {
        overtimeService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(OvertimeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(OvertimeService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OvertimeService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(OvertimeService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(OvertimeService.AlreadyCancelledException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyCancelled(OvertimeService.AlreadyCancelledException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OvertimeService.NotCancellableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotCancellable(OvertimeService.NotCancellableException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    /**
     * Two cancels of the same entry at the same moment: both read {@code APPROVED}, the ledger lets
     * only one reversal through (W-19, {@code uk_pay_input_tenant_reverses}), and the loser's whole
     * transaction rolls back. The same 409 as a sequential second cancel.
     */
    @ExceptionHandler(PayInputService.AlreadyReversedException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyReversed(PayInputService.AlreadyReversedException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
