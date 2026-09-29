package com.infinevo.core.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/exports} — run a definition, get a link to the file (W-23.1, spec section 4).
 *
 * <p>Supports synchronous export ({@code async=false}, returning {@code 201 Created}) and asynchronous
 * background export ({@code async=true}, returning {@code 202 Accepted} with job id on the {@code report} queue)
 * (W-23.2).
 *
 * <p>Two gates: {@code core.report.read} here, and the definition's own {@code required_action}
 * checked through {@code PermissionService.require}. A caller missing the second
 * gets the same {@code 403} as one missing the first.
 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController extends ReportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;
    private final ReportDefinitionRepository definitions;
    private final PermissionService permissions;
    private final JobService jobService;
    private final Supplier<QueueProducer> queueProducerSupplier;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExportController(
            ExportService exportService,
            ReportDefinitionRepository definitions,
            PermissionService permissions,
            JobService jobService,
            ObjectProvider<QueueProducer> queueProducers,
            ObjectMapper objectMapper) {
        this(
                exportService,
                definitions,
                permissions,
                jobService,
                () -> queueProducers.getIfAvailable(() -> null),
                objectMapper);
    }

    public ExportController(
            ExportService exportService,
            ReportDefinitionRepository definitions,
            PermissionService permissions,
            JobService jobService,
            Supplier<QueueProducer> queueProducerSupplier,
            ObjectMapper objectMapper) {
        this.exportService = Objects.requireNonNull(exportService, "exportService must not be null");
        this.definitions = definitions;
        this.permissions = permissions;
        this.jobService = jobService;
        this.queueProducerSupplier = queueProducerSupplier != null ? queueProducerSupplier : () -> null;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public ExportController(ExportService exportService) {
        this(exportService, null, null, null, () -> null, new ObjectMapper());
    }

    public record AsyncExportResponse(String jobId) {}

    /**
     * Runs export. If {@code async=true}, creates a background job and returns {@code 202 Accepted}
     * with the job id. Otherwise runs synchronously and returns {@code 201 Created} with download link.
     */
    @PostMapping
    @RequiresAction("core.report.read")
    public ResponseEntity<?> export(
            @RequestParam(name = "async", defaultValue = "false") boolean async,
            @RequestBody ExportService.ExportRequest request) {
        if (request == null) {
            throw new ReportDefinitionService.ValidationException(Map.of("request", "A request body is required"));
        }
        if (request.definitionId() == null) {
            throw new ReportDefinitionService.ValidationException(Map.of("definitionId", "definitionId is required"));
        }

        if (async) {
            UUID tenantId = TenantContext.require();
            if (definitions == null || permissions == null || jobService == null) {
                throw new IllegalStateException("Async export dependencies are not configured");
            }

            ReportDefinition definition = definitions
                    .findByIdAndTenantId(request.definitionId(), tenantId)
                    .orElseThrow(() -> new ReportDefinitionService.NotFoundException(request.definitionId()));

            // The second gate: verify caller holds the definition's required action before enqueueing
            permissions.require(definition.getRequiredAction());

            String jobId = UUID.randomUUID().toString();
            String payload;
            try {
                payload = objectMapper.writeValueAsString(request);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize export request payload", e);
            }

            // Without a producer the job would sit PENDING forever: nothing else ever runs it.
            QueueProducer producer = queueProducerSupplier.get();
            if (producer == null) {
                throw new ExportService.BackgroundExportUnavailableException(
                        "Background exports are not available here; run the export without async=true");
            }
            jobService.createJob(jobId, tenantId, "report", payload);
            try {
                producer.send("report", QueueMessage.of(jobId, tenantId, "report", payload));
            } catch (RuntimeException e) {
                log.warn("Could not queue export job {}; marking it FAILED", jobId, e);
                jobService.markFailed(jobId, "The export could not be queued");
                throw new ExportService.BackgroundExportUnavailableException(
                        "The export could not be queued; try again, or run it without async=true");
            }

            log.info(
                    "Enqueued async report export job {} for definition {} in tenant {}",
                    jobId,
                    request.definitionId(),
                    tenantId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AsyncExportResponse(jobId));
        }

        ExportService.ExportResponse response = exportService.export(request.definitionId(), request.filters());
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.documentId()))
                .body(response);
    }
}
