package com.itsdev.payroll.service.leaveAndAttendance.onboarding;

import com.itsdev.payroll.dto.leaveAndAttendance.onboarding.OnboardingStatusDTO;

public interface OnboardingStatusService {

    OnboardingStatusDTO getOrCreateByOrganizationId(String organizationId);

    OnboardingStatusDTO getByOrganizationId(String organizationId);

    OnboardingStatusDTO updateByOrganizationId(String organizationId, OnboardingStatusDTO partialDto);

    boolean isAllCompleted(String organizationId);
}
