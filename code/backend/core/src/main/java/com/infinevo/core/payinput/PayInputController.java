package com.infinevo.core.payinput;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.money.Money;
import java.net.URI;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/pay-inputs} (W-19 §4). Three actions, three codes — a caller holding only
 * {@code core.pay_input.read} must not be able to write or lock, so each verb carries its own
 * {@link RequiresAction} rather than one on the class ({@code PayInputGuardIT}).
 *
 * <p>Thin by rule: unpack, delegate, repack ({@code docs/CONVENTIONS.md} section 3). The exception
 * handlers are local, for the reason {@code DocumentController} gives: there is no global advice,
 * and one feature branch should not set the error contract for every later controller.
 */
@RestController
@RequestMapping("/api/v1/pay-inputs")
public class PayInputController {

    private final PayInputService payInputService;

    public PayInputController(PayInputService payInputService) {
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
    }

    @PostMapping
    @RequiresAction("core.pay_input.write")
    public ResponseEntity<PayInputResponse> record(@RequestBody PayInputRequest request) {
        PayInputCommand command = new PayInputCommand(
                request.employeeId(),
                request.period(),
                request.kind(),
                request.quantity(),
                request.amount() != null ? Money.of(request.amount()) : null,
                "core",
                request.sourceRef(),
                request.runRef());
        PayInputResponse created = payInputService.record(command);
        return ResponseEntity.created(URI.create("/api/v1/pay-inputs/" + created.id()))
                .body(created);
    }

    /**
     * {@code runRef} given: a {@link PayInputRunResponse}, that run's tagged rows with totals per
     * employee, and {@code period} is ignored (W-30.1). Otherwise a {@link PayInputListResponse}:
     * {@code employeeId} given, that employee's untagged rows for {@code period}; omitted, the whole
     * period's untagged rows, one statement. Two shapes, the one path spec §4 gives this endpoint —
     * {@code Object} rather than a common supertype invented just to hold both.
     */
    @GetMapping
    @RequiresAction("core.pay_input.read")
    public Object list(
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "runRef", required = false) UUID runRef) {
        if (runRef != null) {
            return payInputService.forRun(runRef);
        }
        if (period == null) {
            throw new PayInputService.ValidationException(
                    Map.of("period", "period is required when runRef is not given"));
        }
        YearMonth parsed = parsePeriod(period);
        return employeeId != null ? payInputService.forEmployee(employeeId, parsed) : payInputService.forPeriod(parsed);
    }

    @PostMapping("/periods/{period}/lock")
    @RequiresAction("core.pay_input.lock")
    public ResponseEntity<Void> lockPeriod(@PathVariable("period") String period) {
        payInputService.lock(parsePeriod(period));
        return ResponseEntity.ok().build();
    }

    /**
     * {@code YearMonth} in {@code YYYY-MM}. Spring's default conversion service has no built-in
     * {@code YearMonth} converter (unlike {@code LocalDate}), so this is parsed by hand rather than
     * bound directly on the method parameter — the alternative is a {@code WebMvcConfigurer} bean
     * for the one type this ticket introduces.
     */
    private static YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeException e) {
            throw new PayInputService.ValidationException(Map.of("period", "period must be YYYY-MM"));
        }
    }

    @PostMapping("/{id}/reverse")
    @RequiresAction("core.pay_input.write")
    public ResponseEntity<PayInputResponse> reverse(
            @PathVariable("id") UUID id, @RequestBody(required = false) PayInputReverseRequest request) {
        PayInputResponse reversal = payInputService.reverse(id, request != null ? request.reason() : null);
        return ResponseEntity.created(URI.create("/api/v1/pay-inputs/" + reversal.id()))
                .body(reversal);
    }

    @ExceptionHandler(PayInputService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayInputService.NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PayInputService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(PayInputService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(PayInputService.DuplicatePayInputException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(PayInputService.DuplicatePayInputException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PayInputService.RunLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleRunLocked(PayInputService.RunLockedException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

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
