package com.infinevo.payroll.payrun;

import com.infinevo.payroll.schedule.NoPayScheduleException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * W-29.1 §4. Create, read, list, lock, cancel. Not ported: the legacy free {@code PUT} that copied
 * {@code status} from the body, {@code DELETE} (cancel instead — the row stays) and
 * {@code GET /completed} (a {@code status} filter).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/payruns")
public class PayRunController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PayRunService payRunService;

    public PayRunController(PayRunService payRunService) {
        this.payRunService = Objects.requireNonNull(payRunService, "payRunService must not be null");
    }

    @PostMapping
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> create(@RequestBody CreatePayRunRequest request) {
        PayRunResponse created = payRunService.create(parsePeriod(request == null ? null : request.period()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PayRunApiResponse.created("Pay run created successfully", created));
    }

    @GetMapping
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<Page<PayRunResponse>>> list(
            @RequestParam(name = "status", required = false) PayRunStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "period").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(
                PayRunApiResponse.ok("Pay runs retrieved successfully", payRunService.list(status, pageable)));
    }

    @GetMapping("/{id}")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run retrieved successfully", payRunService.get(id)));
    }

    @GetMapping("/{id}/employees")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<Page<EmployeePayRunResponse>>> employees(
            @PathVariable("id") UUID id,
            @RequestParam(name = "inclusion", required = false) InclusionStatus inclusion,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        PageRequest pageable =
                PageRequest.of(Math.max(0, page), clampSize(size), Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        return ResponseEntity.ok(PayRunApiResponse.ok(
                "Pay run employees retrieved successfully", payRunService.employees(id, inclusion, pageable)));
    }

    @PostMapping("/{id}/lock")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> lock(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run locked successfully", payRunService.lock(id)));
    }

    @PostMapping("/{id}/cancel")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> cancel(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run cancelled successfully", payRunService.cancel(id)));
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayRunNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicatePayRunException.class, IllegalPayRunTransitionException.class})
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(NoPayScheduleException.class)
    public ResponseEntity<ApiErrorResponse> handleNoSchedule(NoPayScheduleException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
    }

    private static YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period is required, as YYYY-MM");
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("period must be YYYY-MM, was " + period, e);
        }
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(
                        code,
                        message,
                        traceId != null ? traceId : UUID.randomUUID().toString()));
    }
}
