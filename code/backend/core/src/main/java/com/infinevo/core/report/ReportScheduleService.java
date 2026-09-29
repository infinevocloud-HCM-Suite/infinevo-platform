package com.infinevo.core.report;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /api/v1/report-schedules} (W-23.2, spec section 4): a definition run on a cadence and emailed
 * as a seven-day signed link. The worker's {@code ReportScheduleEvaluator} runs them; this is where
 * they are created, changed and removed.
 *
 * <p>Errors are {@link ReportDefinitionService}'s: a bad request is its {@code ValidationException}, an
 * unknown schedule its {@code NotFoundException}.
 */
public interface ReportScheduleService {

    /** Most recipients one schedule may send to. */
    int MAX_RECIPIENTS = 20;

    List<ReportScheduleResponse> list();

    /**
     * Creates the schedule under {@code id}, or replaces the one already there.
     *
     * <p>The caller must hold the definition's {@code required_action} as well as
     * {@code core.report_schedule.manage}, and is stored as the schedule's owner: the worker runs the
     * schedule later with no caller to check, so it re-checks this account's actions instead, every
     * time, rather than trusting this one check to still hold (manager's review item B-4).
     */
    ReportScheduleResponse put(UUID id, ReportScheduleRequest request);

    void delete(UUID id);

    /**
     * @param recipientEmails addresses separated by commas, semicolons or white space
     * @param isActive null means true
     */
    record ReportScheduleRequest(
            UUID definitionId,
            String cadence,
            Integer dayOfPeriod,
            LocalTime sendAtLocalTime,
            Map<String, String> filters,
            String recipientEmails,
            Boolean isActive) {}

    record ReportScheduleResponse(
            UUID id,
            UUID definitionId,
            String cadence,
            Integer dayOfPeriod,
            LocalTime sendAtLocalTime,
            Map<String, String> filters,
            List<String> recipients,
            boolean isActive,
            Instant lastRunAt,
            String lastRunStatus,
            UUID lastDocumentId) {}
}
