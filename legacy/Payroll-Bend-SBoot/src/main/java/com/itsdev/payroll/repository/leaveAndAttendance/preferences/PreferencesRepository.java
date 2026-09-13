package com.itsdev.payroll.repository.leaveAndAttendance.preferences;



import com.itsdev.payroll.entity.leaveAndAttedance.preferences.Preferences;
import com.itsdev.payroll.entity.organization.Organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PreferencesRepository extends JpaRepository<Preferences, UUID> {

    // Fetch preferences for a given organization
    Optional<Preferences> findByOrganization(Organization organization);

    // Find preferences by ID and organization (safety in multi-org system)
    Optional<Preferences> findByIdAndOrganization(UUID id, Organization organization);

    // Check if preferences already exist for an organization (to prevent duplicates)
    boolean existsByOrganization(Organization organization);
}

