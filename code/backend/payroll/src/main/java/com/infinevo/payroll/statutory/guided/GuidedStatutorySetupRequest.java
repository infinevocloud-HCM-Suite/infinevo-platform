package com.infinevo.payroll.statutory.guided;

import java.util.List;

/**
 * Composite request payload for applying guided statutory setup (W-93).
 */
public record GuidedStatutorySetupRequest(
        EpfSetupRequest epf, EsiSetupRequest esi, List<PtStateSetupRequest> ptStates) {}
