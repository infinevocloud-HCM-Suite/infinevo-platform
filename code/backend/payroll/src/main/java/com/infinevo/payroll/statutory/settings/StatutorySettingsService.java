package com.infinevo.payroll.statutory.settings;

import java.util.UUID;

/**
 * Service interface for tenant-scoped statutory settings (EPF and ESI) (W-31.1).
 */
public interface StatutorySettingsService {

    /**
     * Retrieves the EPF setting for the given tenant, or statutory defaults if no setting exists.
     * Does NOT write anything to the database when returning defaults.
     */
    EpfSettingResponse epf(UUID tenantId);

    /**
     * Retrieves the ESI setting for the given tenant, or statutory defaults if no setting exists.
     * Does NOT write anything to the database when returning defaults.
     */
    EsiSettingResponse esi(UUID tenantId);

    /**
     * Saves or updates the EPF setting for the current tenant context.
     */
    EpfSettingResponse saveEpf(EpfSettingRequest request);

    /**
     * Saves or updates the ESI setting for the current tenant context.
     */
    EsiSettingResponse saveEsi(EsiSettingRequest request);

    /**
     * D-141: the company TDS switch for the given tenant, or the default (enabled) if none is saved.
     * Does NOT write anything to the database when returning the default.
     */
    TdsSettingResponse tds(UUID tenantId);

    /**
     * D-141: saves or updates the company TDS switch for the current tenant context.
     */
    TdsSettingResponse saveTds(TdsSettingRequest request);
}
