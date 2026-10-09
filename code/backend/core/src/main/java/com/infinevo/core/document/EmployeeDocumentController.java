package com.infinevo.core.document;

import com.infinevo.core.employee.EmployeeDocumentView;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/employees/{id}/documents} — the employee page's Documents tab (W-73.5, spec
 * section 4): the employee's live {@link DocumentKind#EMPLOYEE_DOCUMENT} rows, newest first, each
 * naming its uploader ({@link EmployeeDocumentQuery}).
 *
 * <p><strong>Two codes, both required:</strong> {@code core.employee.read} on the annotation and
 * {@code core.document.read} through {@link PermissionService#require}. {@code payroll-officer} holds the
 * first and not the second ({@code V025__catalogue_correction.sql:187}, {@code V037__document.sql:94-98});
 * with the first alone it could read every employee's document names and labels. {@code @RequiresAction}
 * takes one code plus alternatives, never two that must both hold, so the second is checked here — before
 * the employee is looked up, so a refusal says nothing about whether the employee exists.
 *
 * <p>Only the list lives here. Upload and delete stay on {@code DocumentController}, guarded as today —
 * {@code core.document.upload} and {@code core.document.delete}; the tab hides its Delete button behind
 * {@code core.employee.update} on top of that. {@code /me/documents} is not reused: it returns every
 * kind the caller owns, a leave attachment and a payslip among them, where this tab shows employee
 * documents only.
 *
 * <p>The employee is read first, so an unknown id — or one in another tenant — is a {@code 404}, not an
 * empty list that would read as "this employee has no documents".
 *
 * <p>Thin by rule: unpack, delegate, repack — {@code docs/CONVENTIONS.md} section 3. The exception
 * handler is local, for the reason {@code EmployeeController} gives.
 *
 * <p><strong>Why this package and not {@code core.employee}</strong>, where the path suggests it: a
 * dozen test contexts — and {@code payroll}'s {@code PayScheduleTestApp} — scan {@code core.employee}
 * with no {@link DocumentService} bean, and a controller there would stop every one of them starting.
 * Every context that scans this package already supplies both services, because
 * {@link MyDocumentController} needs the same two.
 */
@RestController
@RequestMapping("/api/v1/employees/{id}/documents")
public class EmployeeDocumentController {

    static final String DOCUMENT_READ = "core.document.read";

    private final EmployeeService employeeService;
    private final EmployeeDocumentQuery query;
    private final PermissionService permissions;

    public EmployeeDocumentController(
            EmployeeService employeeService, EmployeeDocumentQuery query, PermissionService permissions) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
    }

    @GetMapping
    @RequiresAction("core.employee.read")
    public List<EmployeeDocumentView> list(@PathVariable("id") UUID id) {
        permissions.require(DOCUMENT_READ);
        employeeService.get(id);
        return query.list(id);
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    /** The correlation id the logging filter put on this request, so a client can quote it. */
    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
