package com.itsdev.payroll.controller.leaveAndAttendance.preferences;

import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesDTO;
import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesResponseDTO;
import com.itsdev.payroll.service.leaveAndAttendance.preferences.PreferencesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    // Create Preferences (one per org)
    @PostMapping
    public ResponseEntity<PreferencesDTO> createPreferences(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody PreferencesDTO dto) {
        return ResponseEntity.ok(preferencesService.createPreferencesForOrg(organizationId, dto));
    }

    // Update Preferences
    @PutMapping("/{preferencesId}")
    public ResponseEntity<PreferencesDTO> updatePreferences(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String preferencesId,
            @RequestBody PreferencesDTO dto) {
        return ResponseEntity.ok(preferencesService.updatePreferencesForOrg(organizationId, preferencesId, dto));
    }

    // Delete Preferences
    @DeleteMapping("/{preferencesId}")
    public ResponseEntity<Void> deletePreferences(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String preferencesId) {
        preferencesService.deletePreferencesForOrg(organizationId, preferencesId);
        return ResponseEntity.noContent().build();
    }

    // Get Preferences by ID
    @GetMapping("/{preferencesId}")
    public ResponseEntity<PreferencesResponseDTO> getPreferences(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String preferencesId) {
        return ResponseEntity.ok(preferencesService.getPreferencesForOrg(organizationId, preferencesId));
    }

    // Get Preferences for Organization (only one allowed per org)
    @GetMapping
    public ResponseEntity<PreferencesResponseDTO> getPreferencesByOrganization(
            @RequestHeader("organizationId") String organizationId) {
        return ResponseEntity.ok(preferencesService.getPreferencesByOrganization(organizationId));
    }
}
