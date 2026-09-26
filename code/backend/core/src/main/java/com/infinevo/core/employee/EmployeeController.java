package com.infinevo.core.employee;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.Objects;
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
 * {@code /api/v1/employees} — create, read, update and soft-delete one employee (W-13.1, spec
 * section 4).
 *
 * <p>Thin by rule: unpack, delegate, repack — {@code docs/CONVENTIONS.md} section 3. Every decision
 * about what an employee may be is in {@link EmployeeServiceImpl}.
 *
 * <p><strong>No endpoint here names a tenant.</strong> There is no {@code /tenants/{id}/employees}
 * path, no {@code organizationId} header and no tenant field on the request body. The tenant is
 * bound by {@code TenantContextFilter} from the verified token before the request reaches this
 * class, and the database enforces it again through row-level security. This is the difference from
 * the frozen system, where 57 methods take the organisation as a caller-supplied parameter
 * (DEBT-022) and 39 HRMS entities scope to none at all (BUG-002).
 *
 * <p>The three exception handlers are local rather than a {@code @RestControllerAdvice}: there is no
 * global advice in the platform yet, and inventing one here would quietly set the error contract for
 * every future controller from inside a feature branch. They return the shared
 * {@link ApiErrorResponse} envelope, so the shape is already the common one when that advice arrives.
 *
 * <p><strong>Guarded by the tenant-wide codes</strong> (W-11.2): {@code core.employee.read} and
 * {@code .update}, not the {@code _own} / {@code _team} forms. Those need the service to know which
 * employee the caller is, which nothing does yet. Until it does, a user holding only
 * {@code employee} cannot read their own record here — a known gap, not an oversight.
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @PostMapping
    @RequiresAction("core.employee.create")
    public ResponseEntity<EmployeeResponse> create(@RequestBody EmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id()))
                .body(created);
    }

    /**
     * Listing stub — returns an empty list until W-13.3 (employee search &amp; listing) lands.
     *
     * <p>The navigation catalogue's {@code core.employee} item targets {@code GET /api/v1/employees}.
     * {@code NavigationMatchesEnforcementIT} verifies the endpoint returns non-403 when the item
     * is visible. This stub satisfies that contract without pre-empting W-13.3's design (pagination,
     * filters, N+1 fix, {@code is_deleted} filter).
     */
    @GetMapping
    @RequiresAction("core.employee.read")
    public java.util.List<EmployeeResponse> list() {
        return java.util.List.of();
    }

    @GetMapping("/{id}")
    @RequiresAction("core.employee.read")
    public EmployeeResponse get(@PathVariable("id") UUID id) {
        return employeeService.get(id);
    }

    @PutMapping("/{id}")
    @RequiresAction("core.employee.update")
    public EmployeeResponse update(@PathVariable("id") UUID id, @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    /** Soft delete — {@code 204}, and the row stays. See {@link Employee#markDeleted}. */
    @DeleteMapping("/{id}")
    @RequiresAction("core.employee.delete")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(EmployeeService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    /**
     * {@code 409}, never a constraint-violation stack trace — spec section 9. The message names the
     * number and says "in this tenant", because the same number in another tenant is legal.
     */
    @ExceptionHandler(EmployeeService.DuplicateEmployeeNumberException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(EmployeeService.DuplicateEmployeeNumberException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /**
     * A body Jackson could not read at all — malformed JSON, or a {@code status} outside the
     * {@link EmploymentStatus} vocabulary, which fails during deserialisation and so never reaches
     * the service's validation.
     *
     * <p>Without this the request still failed safely with a {@code 400} and nothing reached the
     * database, but it came back in Spring's default body while every other error on this controller
     * uses {@link ApiErrorResponse}. One endpoint answering in two shapes is the kind of thing a
     * client writes a special case for and never removes.
     *
     * <p>The exception's own message is not echoed: it carries the offending JSON and the internal
     * type names, and neither belongs in a response.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed and that"
                                + " status is one of ACTIVE, SUSPENDED or TERMINATED.",
                        traceId()));
    }

    /** The correlation id the logging filter put on this request, so a client can quote it. */
    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
