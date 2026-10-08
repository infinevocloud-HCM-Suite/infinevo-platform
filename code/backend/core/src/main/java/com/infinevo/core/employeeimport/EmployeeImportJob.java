package com.infinevo.core.employeeimport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * What a job holds in {@code core.job_status.result_payload} (W-73.7). Queued, it is the input — the file
 * and who asked; completed, the job service replaces it with the summary. The queue message itself
 * carries only the job id, so a 1,000-row file never meets the 48 KB message limit
 * ({@code QueueMessage.MAX_PAYLOAD_BYTES}).
 */
final class EmployeeImportJob {

    static final String IMPORT = "IMPORT";
    static final String INVITE_ALL = "INVITE_ALL";

    private EmployeeImportJob() {}

    /** The queued job's input. {@code csv} and {@code validOnly} are for {@link #IMPORT} only. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Input(String kind, UUID actorUserId, Boolean validOnly, String csv) {}

    /** The completed job's summary. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(
            String kind, int totalCount, int createdCount, int invitedCount, int failedCount, UUID resultDocumentId) {}
}
