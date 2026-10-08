package com.infinevo.core.employeeimport;

import com.infinevo.core.job.JobState;
import java.time.Instant;
import java.util.UUID;

/**
 * One import or invite-all job in the history (W-73.7 §4, {@code GET /api/v1/employees/import/jobs}).
 * The counts and the result file are set once the job has completed.
 *
 * @param kind {@code IMPORT} or {@code INVITE_ALL}
 */
public record EmployeeImportJobResponse(
        String jobId,
        String kind,
        JobState status,
        Integer progressPercentage,
        Integer totalCount,
        Integer createdCount,
        Integer invitedCount,
        Integer failedCount,
        UUID resultDocumentId,
        String errorMessage,
        Instant startedAt,
        Instant updatedAt) {}
