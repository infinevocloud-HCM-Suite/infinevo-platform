package com.itsdev.payroll.service.leaveAndAttendance.preferences;


import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesResponseDTO;

public interface PreferencesService {

    // Create preferences for an organization
    PreferencesDTO createPreferencesForOrg(String organizationId, PreferencesDTO dto);

    // Update preferences for an organization
    PreferencesDTO updatePreferencesForOrg(String organizationId, String preferencesId, PreferencesDTO dto);

    // Get preferences for an organization
//    PreferencesDTO getPreferencesForOrg(String organizationId, String preferencesId);

    // ✅ New: return rich response
    PreferencesResponseDTO getPreferencesForOrg(String organizationId, String preferencesId);
    // Delete preferences for an organization
    void deletePreferencesForOrg(String organizationId, String preferencesId);

    // Get preferences (since usually 1 per org, but keeping list optional for flexibility)
   // PreferencesDTO getPreferencesByOrganization(String organizationId);

    // ✅ New: return rich response
    PreferencesResponseDTO getPreferencesByOrganization(String organizationId);
}

