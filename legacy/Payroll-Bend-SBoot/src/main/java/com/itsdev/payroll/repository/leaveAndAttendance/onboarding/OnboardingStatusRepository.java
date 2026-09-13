package com.itsdev.payroll.repository.leaveAndAttendance.onboarding;



import com.itsdev.payroll.entity.leaveAndAttedance.onboarding.OnboardingStatus;

import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingStatusRepository extends JpaRepository<OnboardingStatus, Long> {

    // Fetch onboarding status for a given organization
    Optional<OnboardingStatus> findByOrganization(Organization organization);
}

