package com.infinevo.core.report;

import com.infinevo.shared.authz.RequiresAction;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/exports} — run a definition, get a link to the file (W-23.1, spec section 4).
 *
 * <p>Two gates: {@code core.report.read} here, and the definition's own {@code required_action}
 * inside {@link ExportService}, through {@code PermissionService.require}. A caller missing the second
 * gets the same {@code 403} as one missing the first.
 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController extends ReportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = Objects.requireNonNull(exportService, "exportService must not be null");
    }

    /** {@code 201}, pointing at the stored document; the body carries a fifteen-minute link to it. */
    @PostMapping
    @RequiresAction("core.report.read")
    public ResponseEntity<ExportService.ExportResponse> export(@RequestBody ExportService.ExportRequest request) {
        if (request == null) {
            throw new ReportDefinitionService.ValidationException(
                    java.util.Map.of("request", "A request body is required"));
        }
        ExportService.ExportResponse response = exportService.export(request.definitionId(), request.filters());
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.documentId()))
                .body(response);
    }
}
