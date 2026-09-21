package com.infinevo.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class JobStatusControllerTest {

    private JobService jobService;
    private JobStatusController controller;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        controller = new JobStatusController(jobService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnJobStatusWhenFound() {
        String jobId = "job-999";
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);

        JobStatusResponseDTO dto =
                new JobStatusResponseDTO(jobId, "payrun", JobState.RUNNING, 45, null, Instant.now(), Instant.now());
        when(jobService.getJobStatus(jobId, tenantId)).thenReturn(Optional.of(dto));

        ResponseEntity<JobStatusResponseDTO> response = controller.getJobStatus(jobId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(jobId, response.getBody().jobId());
        assertEquals(45, response.getBody().progressPercentage());
        assertEquals(JobState.RUNNING, response.getBody().status());
    }

    @Test
    void shouldReturn404WhenNotFound() {
        String jobId = "job-not-found";
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);

        when(jobService.getJobStatus(jobId, tenantId)).thenReturn(Optional.empty());

        ResponseEntity<JobStatusResponseDTO> response = controller.getJobStatus(jobId, null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldUseOrganizationIdHeaderWhenContextNotSet() {
        String jobId = "job-header";
        UUID tenantId = UUID.randomUUID();

        JobStatusResponseDTO dto =
                new JobStatusResponseDTO(jobId, "payrun", JobState.COMPLETED, 100, null, Instant.now(), Instant.now());
        when(jobService.getJobStatus(jobId, tenantId)).thenReturn(Optional.of(dto));

        ResponseEntity<JobStatusResponseDTO> response = controller.getJobStatus(jobId, tenantId.toString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jobId, response.getBody().jobId());
    }

    @Test
    void shouldThrowWhenNoTenantBound() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> controller.getJobStatus("job-none", null));
    }
}
