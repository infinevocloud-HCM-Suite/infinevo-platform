package com.infinevo.core.wfh;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WfhImportSummaryResponse(
        UUID logId,
        String fileName,
        String status,
        boolean dryRun,
        int rowsTotal,
        int rowsImported,
        int rowsFailed,
        List<String> errors,
        Instant startedAt,
        Instant finishedAt) {}
