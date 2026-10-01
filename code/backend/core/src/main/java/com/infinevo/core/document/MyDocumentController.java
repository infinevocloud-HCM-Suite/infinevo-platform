package com.infinevo.core.document;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller serving the authenticated caller's own documents (W-25, spec section 4).
 *
 * <p>Mounted at {@code /api/v1/me/documents} and gated by {@code core.document.read_own}.
 */
@RestController
@RequestMapping("/api/v1/me/documents")
public class MyDocumentController {

    private final DocumentService documentService;
    private final EmployeeService employeeService;

    public MyDocumentController(DocumentService documentService, EmployeeService employeeService) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping
    @RequiresAction("core.document.read_own")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments() {
        return employeeService
                .currentEmployee()
                .map(EmployeeResponse::id)
                .map(documentService::findByEmployee)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EmployeeService.NotFoundException("No employee profile linked to current user"));
    }

    /** A login with no linked employee record: {@code 404}, never an unhandled exception. */
    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
