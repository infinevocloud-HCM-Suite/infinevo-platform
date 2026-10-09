package com.infinevo.core.employeeimport;

import com.infinevo.core.document.DocumentService;
import com.infinevo.core.invitation.EmployeeInvitationController;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Bulk employee import and "Invite all without access" (W-73.7 §4). Every endpoint needs
 * {@code core.employee.create}.
 *
 * <p><strong>A file in which any row grants a role beyond {@code employee} also needs
 * {@code core.role.assign}</strong> — W-73.3's rule on {@code POST /api/v1/employee-invitations}
 * ({@link EmployeeInvitationController#ROLE_ASSIGN_ACTION}), applied to the dry run and to the import
 * alike, here and before anything is queued. Otherwise HR without that action could hand a new hire
 * {@code tenant-admin} through a file it may not hand through the single invite. Invite-all grants
 * {@code employee} only, so it needs nothing more.
 */
@RestController
public class EmployeeImportController {

    /** About 1,000 rows of the template with room to spare; the store's own limit is 10 MB. */
    static final long MAX_FILE_BYTES = 1024L * 1024L;

    private final EmployeeImportService importService;
    private final PermissionService permissionService;

    public EmployeeImportController(EmployeeImportService importService, PermissionService permissionService) {
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @GetMapping(value = "/api/v1/employees/import/template", produces = "text/csv")
    @RequiresAction("core.employee.create")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("employee-import-template.csv")
                                .build()
                                .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(EmployeeImportParser.TEMPLATE);
    }

    @PostMapping("/api/v1/employees/import/dry-run")
    @RequiresAction("core.employee.create")
    public List<ImportRowResult> dryRun(@RequestParam("file") MultipartFile file) {
        List<EmployeeImportRow> rows = EmployeeImportParser.parse(read(file));
        requireRoleAssignIfGranting(rows);
        return importService.dryRun(rows);
    }

    @PostMapping("/api/v1/employees/import")
    @RequiresAction("core.employee.create")
    public ResponseEntity<Map<String, String>> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "validOnly", defaultValue = "false") boolean validOnly) {
        String csv = read(file);
        List<EmployeeImportRow> rows = EmployeeImportParser.parse(csv);
        requireRoleAssignIfGranting(rows);
        String jobId = importService.enqueueImport(csv, rows, validOnly, currentActorUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
    }

    @GetMapping("/api/v1/employees/import/jobs")
    @RequiresAction("core.employee.create")
    public List<EmployeeImportJobResponse> jobs() {
        return importService.recentJobs();
    }

    @GetMapping("/api/v1/employees/import/jobs/{jobId}/result")
    @RequiresAction("core.employee.create")
    public ResponseEntity<InputStreamResource> result(@PathVariable("jobId") String jobId) {
        DocumentService.DocumentContent content = importService.resultFile(jobId);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.metadata().fileName())
                                .build()
                                .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(new InputStreamResource(content.content()));
    }

    @PostMapping("/api/v1/employee-invitations/invite-all")
    @RequiresAction("core.employee.create")
    public ResponseEntity<Map<String, String>> inviteAll() {
        String jobId = importService.enqueueInviteAll(currentActorUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
    }

    /** How many "Invite all without access" would invite now — the count its confirm dialog shows. */
    @GetMapping("/api/v1/employee-invitations/invite-all/count")
    @RequiresAction("core.employee.create")
    public Map<String, Integer> inviteAllCount() {
        return Map.of("count", importService.countWithoutAccess());
    }

    private void requireRoleAssignIfGranting(List<EmployeeImportRow> rows) {
        if (EmployeeImportServiceImpl.grantsRoles(rows)) {
            permissionService.require(EmployeeInvitationController.ROLE_ASSIGN_ACTION);
        }
    }

    /** The upload as UTF-8 text: refused when missing, over {@link #MAX_FILE_BYTES}, or not UTF-8. */
    static String read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmployeeImportFileException("Choose a CSV file to upload");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new EmployeeImportFileException("The file is larger than 1 MB; split it and import each part");
        }
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new EmployeeImportFileException("The file could not be read");
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new EmployeeImportFileException("The file is not UTF-8 text; save it as CSV UTF-8 and try again");
        }
    }

    /** The token's subject, as W-73.3's single invite records it; null when none can be read. */
    private static UUID currentActorUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return UUID.fromString(subject);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @ExceptionHandler(EmployeeImportFileException.class)
    public ResponseEntity<ApiErrorResponse> handleFile(EmployeeImportFileException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingFile(MissingServletRequestPartException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Choose a CSV file to upload");
    }

    @ExceptionHandler(EmployeeImportService.ImportUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(EmployeeImportService.ImportUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, e.getMessage());
    }

    @ExceptionHandler({EmployeeImportService.JobNotFoundException.class, DocumentService.NotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId));
    }
}
