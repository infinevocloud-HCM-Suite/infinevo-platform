package com.infinevo.payroll.payrun;

import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.NoPayScheduleException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-30.2 §4. The two off-cycle endpoints: create a run for named employees, and add payouts to it.
 * Lock, compute, cancel, read and list are {@link PayRunController}'s, unchanged for both run types.
 *
 * <p>Adding inputs writes the pay input ledger, so it needs {@code core.pay_input.write} as well as
 * {@code payroll.run.execute}; the aspect checks one action, the method the second.
 *
 * <p>Not ported from {@code OffCyclePayRunController.java:27-89}: the free {@code PUT} that copied
 * {@code status} from the body ({@code :37}), {@code DELETE} ({@code :67} — cancel instead), the
 * import ({@code :79} — {@code POST /inputs}, with the tenant's own vocabulary instead of earnings
 * named {@code Bonus} and {@code Commission}) and the release of withheld salary ({@code :89},
 * dropped by the founder 2026-09-25).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/payruns")
public class OffCyclePayRunController {

    static final String PAY_INPUT_WRITE = "core.pay_input.write";

    private final PayRunService payRunService;
    private final PermissionService permissionService;

    public OffCyclePayRunController(PayRunService payRunService, PermissionService permissionService) {
        this.payRunService = Objects.requireNonNull(payRunService, "payRunService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @PostMapping("/off-cycle")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> create(@RequestBody CreateOffCyclePayRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A body with pay_date and employee_ids is required");
        }
        PayRunResponse created =
                payRunService.createOffCycle(request.payDate(), request.employeeIds(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PayRunApiResponse.created("Off-cycle pay run created successfully", created));
    }

    @PostMapping("/{id}/inputs")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<List<PayRunInputResponse>>> addInputs(
            @PathVariable("id") UUID id, @RequestBody List<PayRunInputRequest> inputs) {
        permissionService.require(PAY_INPUT_WRITE);
        List<PayRunInputResponse> results = payRunService.addInputs(id, inputs);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PayRunApiResponse.created("Pay run inputs recorded", results));
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayRunNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
        NotAnOffCycleRunException.class,
        PayInputService.RunLockedException.class,
        NoPayScheduleException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
        EmployeeNotInRunException.class,
        IllegalArgumentException.class,
        PayInputService.ValidationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
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
