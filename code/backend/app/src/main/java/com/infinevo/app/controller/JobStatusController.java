package com.infinevo.app.controller;

import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing status and progress for asynchronous background jobs (W-52).
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobStatusController {

    private final JobService jobService;

    public JobStatusController(JobService jobService) {
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponseDTO> getJobStatus(
            @PathVariable("jobId") String jobId,
            @RequestHeader(value = "organizationId", required = false) String organizationId) {

        UUID tenantId = TenantContext.current()
                .orElseGet(() -> organizationId != null && !organizationId.isBlank()
                        ? UUID.fromString(organizationId)
                        : TenantContext.require());

        return jobService
                .getJobStatus(jobId, tenantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
