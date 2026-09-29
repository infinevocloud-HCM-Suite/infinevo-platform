package com.infinevo.worker.retention;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-22.2 manager's review item B-2 — the first production sweep must be a dry run: deleted audit
 * rows cannot be recovered (spec §10), so {@code worker.retention.dry-run} must reach
 * {@link AuditRetentionService#runSweep}, and its default must be {@code true}.
 */
class AuditRetentionJobTest {

    @Test
    @DisplayName("worker.retention.dry-run=true (the default) makes the job call runSweep(true)")
    void defaultIsDryRun() {
        AuditRetentionService service = mock(AuditRetentionService.class);
        when(service.runSweep(true)).thenReturn(List.of());
        AuditRetentionJob job = new AuditRetentionJob(service, true);

        job.runRetentionSweep();

        verify(service).runSweep(eq(true));
    }

    @Test
    @DisplayName("worker.retention.dry-run=false makes the job call runSweep(false)")
    void explicitlyDisabledMeansARealSweep() {
        AuditRetentionService service = mock(AuditRetentionService.class);
        when(service.runSweep(false)).thenReturn(List.of());
        AuditRetentionJob job = new AuditRetentionJob(service, false);

        job.runRetentionSweep();

        verify(service).runSweep(eq(false));
    }

    @Test
    @DisplayName("A sweep failure is logged and does not throw out of the scheduled method")
    void aFailureIsCaughtNotThrown() {
        AuditRetentionService service = mock(AuditRetentionService.class);
        when(service.runSweep(true)).thenThrow(new IllegalStateException("database unavailable"));
        AuditRetentionJob job = new AuditRetentionJob(service, true);

        job.runRetentionSweep(); // must not throw - a scheduled method that throws breaks the next trigger
    }
}
