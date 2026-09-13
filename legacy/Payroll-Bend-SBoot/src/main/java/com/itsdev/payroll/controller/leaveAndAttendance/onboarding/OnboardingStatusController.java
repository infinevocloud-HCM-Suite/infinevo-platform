package com.itsdev.payroll.controller.leaveAndAttendance.onboarding;

import com.itsdev.payroll.dto.leaveAndAttendance.onboarding.OnboardingStatusDTO;
import com.itsdev.payroll.service.leaveAndAttendance.onboarding.OnboardingStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/leaveandattendance")
public class OnboardingStatusController {

    private final OnboardingStatusService onboardingService;
    private static final Logger log = LoggerFactory.getLogger(OnboardingStatusController.class);

    public OnboardingStatusController(OnboardingStatusService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /**
     * GET onboarding status for an organization.
     * Header: organizationId -> organization unique string (e.g. "ORG12345")
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOnboardingStatus(@RequestHeader("organizationId") String orgIdHeader) {
        String method = "getOnboardingStatus";
        log.info("[{}] 📥 Incoming request to fetch onboarding status | orgId={}", method, orgIdHeader);

        OnboardingStatusDTO dto = onboardingService.getOrCreateByOrganizationId(orgIdHeader);

        // Prepare onboarding steps list (matches Zoho preview)
        List<Map<String, Object>> onboardingList = new ArrayList<>();
        onboardingList.add(Map.of("id", "leave", "is_completed", dto.isLeaveSetupCompleted()));
        onboardingList.add(Map.of("id", "holidays", "is_completed", dto.isHolidaySetupCompleted()));
        onboardingList.add(Map.of("id", "attendance", "is_completed", dto.isAttendanceSetupCompleted()));
        onboardingList.add(Map.of("id", "preference", "is_completed", dto.isPreferencesSetupCompleted()));
        onboardingList.add(Map.of("id", "leavebalance", "is_completed", dto.isOrganizationDetailsCompleted()));

        // Response data (keeping is_hrms_configured and is_payschedule_configured hardcoded for now)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onboarding_status", onboardingList);
        data.put("is_attendance_enabled", dto.isAttendanceSetupCompleted());
        data.put("is_hrms_configured", true);        // hardcoded per current decision
        data.put("is_onboarding_completed", onboardingService.isAllCompleted(orgIdHeader));
        data.put("is_payschedule_configured", true); // hardcoded per current decision

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("data", data);

        log.info("[{}] ✅ Onboarding status fetched successfully | orgId={}", method, orgIdHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH update onboarding steps for an organization.
     * Body: OnboardingStatusDTO (use Boolean wrappers if you want patch semantics)
     */
    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateOnboardingStatus(
            @RequestHeader("organizationId") String orgIdHeader,
            @RequestBody OnboardingStatusDTO partialDto
    ) {
        String method = "updateOnboardingStatus";
        log.info("[{}] 📥 Incoming request to update onboarding status | orgId={}", method, orgIdHeader);

        OnboardingStatusDTO updatedDto = onboardingService.updateByOrganizationId(orgIdHeader, partialDto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("message", "Onboarding status updated successfully");
        response.put("data", updatedDto);

        log.info("[{}] ✅ Onboarding status updated | orgId={}", method, orgIdHeader);
        return ResponseEntity.ok(response);
    }
}
