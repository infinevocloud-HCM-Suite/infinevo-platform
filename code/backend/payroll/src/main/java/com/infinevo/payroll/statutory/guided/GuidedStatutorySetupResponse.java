package com.infinevo.payroll.statutory.guided;

import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import java.util.List;

/**
 * Composite response payload after applying guided statutory setup (W-93).
 */
public record GuidedStatutorySetupResponse(
        EpfSettingResponse epf, EsiSettingResponse esi, List<PtStateResult> ptStates, boolean allStatutoryConfigured) {}
