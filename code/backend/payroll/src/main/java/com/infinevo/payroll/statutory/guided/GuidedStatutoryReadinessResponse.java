package com.infinevo.payroll.statutory.guided;

import java.util.List;

/**
 * Response payload describing tenant statutory readiness and defaults (W-93).
 */
public record GuidedStatutoryReadinessResponse(
        boolean epfConfigured,
        EpfReadiness epf,
        boolean esiConfigured,
        EsiReadiness esi,
        List<PtStateReadiness> operatingStates,
        StatutoryDefaults defaults) {}
