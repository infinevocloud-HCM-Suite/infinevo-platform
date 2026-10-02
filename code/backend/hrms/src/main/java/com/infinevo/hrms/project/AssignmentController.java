package com.infinevo.hrms.project;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * REST controller for project team assignments (W-41 §4).
 *
 * <p>Thin by rule: unpack, delegate, repack ({@code docs/CONVENTIONS.md} section 3).
 * Each verb carries its own {@link RequiresAction}. Adding an employee already assigned
 * to the project answers 409 CONFLICT.
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/projects/{projectId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final ProjectAccessResolver accessResolver;

    public AssignmentController(AssignmentService assignmentService, ProjectAccessResolver accessResolver) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver must not be null");
    }

    @GetMapping
    @RequiresAction(
            value = ProjectAccessResolver.ACTION_READ,
            anyOf = {ProjectAccessResolver.ACTION_READ_TEAM, ProjectAccessResolver.ACTION_READ_OWN})
    public ApiResponse<List<AssignmentResponse>> list(@PathVariable("projectId") UUID projectId) {
        accessResolver.checkReadable(projectId);
        return ApiResponse.success(assignmentService.listByProject(projectId));
    }

    @PostMapping
    @RequiresAction(ProjectAccessResolver.ACTION_MANAGE)
    public ResponseEntity<ApiResponse<AssignmentResponse>> assign(
            @PathVariable("projectId") UUID projectId, @Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse created = assignmentService.assign(projectId, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/hrms/projects/" + projectId + "/assignments/" + request.employeeId()))
                .body(ApiResponse.success(created));
    }

    @DeleteMapping("/{employeeId}")
    @RequiresAction(ProjectAccessResolver.ACTION_MANAGE)
    public ResponseEntity<Void> remove(
            @PathVariable("projectId") UUID projectId, @PathVariable("employeeId") UUID employeeId) {
        assignmentService.remove(projectId, employeeId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    @ExceptionHandler(DuplicateAssignmentException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(DuplicateAssignmentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.validation(fieldErrors, traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String paramName = e.getName() != null ? e.getName() : "parameter";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(
                        Map.of(paramName, "Invalid value for parameter: " + e.getValue()), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
