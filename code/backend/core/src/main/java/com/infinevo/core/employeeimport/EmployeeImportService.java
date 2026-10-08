package com.infinevo.core.employeeimport;

import com.infinevo.core.document.DocumentService;
import java.io.Serial;
import java.util.List;
import java.util.UUID;

/**
 * Bulk employee import and "Invite all without access" (W-73.7 §4).
 *
 * <p>Both run as a job on the {@value #QUEUE_NAME} queue: the web role validates and enqueues, the worker
 * runs {@link #runJob}. Every row is its own transaction, so one bad row never rolls back the others, and
 * every invitation is made through {@code InvitationService.createEmployeeInvitation} — the one path
 * W-73.3's single invite takes. The tenant is always {@code TenantContext}'s.
 */
public interface EmployeeImportService {

    /** The queue both jobs run on — provisioned as {@code import} in {@code infra/azure/modules/storage.bicep}. */
    String QUEUE_NAME = "import";

    /** Every row checked against the tenant and the rest of the file; nothing written. */
    List<ImportRowResult> dryRun(List<EmployeeImportRow> rows);

    /**
     * Checks the file as {@link #dryRun} does, then queues the import and returns the job id.
     *
     * @param validOnly import the clean rows and skip the rest; when false, any row in error refuses the
     *     whole file
     * @throws EmployeeImportFileException when rows are in error and {@code validOnly} is false, or no row
     *     is clean
     * @throws ImportUnavailableException when no queue is configured
     */
    String enqueueImport(String csv, List<EmployeeImportRow> rows, boolean validOnly, UUID actorUserId);

    /** Queues "Invite all without access" and returns the job id. */
    String enqueueInviteAll(UUID actorUserId);

    /** How many employees "Invite all without access" would invite now. */
    int countWithoutAccess();

    /** The tenant's recent import and invite-all jobs, newest first. */
    List<EmployeeImportJobResponse> recentJobs();

    /**
     * The result file of a finished job.
     *
     * @throws JobNotFoundException when the job is not the tenant's import job, is not finished, or stored
     *     no file
     */
    DocumentService.DocumentContent resultFile(String jobId);

    /**
     * Runs a queued job to the end — the worker's one call. Returns the summary the job completes with.
     * Never throws for a row; a row's failure is in the result file.
     */
    String runJob(String jobId);

    /** Creates the clean rows' employees and invitations, one transaction per row. */
    List<ImportRowResult> importRows(List<EmployeeImportRow> rows, UUID actorUserId, ProgressListener progress);

    /** Invites every employee {@link #countWithoutAccess} counts, with the {@code employee} role only. */
    List<ImportRowResult> inviteAllWithoutAccess(UUID actorUserId, ProgressListener progress);

    /** Called with the share done, 0 to 100. */
    @FunctionalInterface
    interface ProgressListener {
        void percent(int done);

        ProgressListener NONE = done -> {};
    }

    /** No such import job in the bound tenant, or no result file on it. Maps to {@code 404}. */
    class JobNotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public JobNotFoundException(String message) {
            super(message);
        }
    }

    /** No queue to run the job on. Maps to {@code 503}. */
    class ImportUnavailableException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ImportUnavailableException(String message) {
            super(message);
        }
    }
}
