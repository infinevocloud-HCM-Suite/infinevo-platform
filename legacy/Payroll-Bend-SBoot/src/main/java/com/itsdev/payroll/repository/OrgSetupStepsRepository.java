package com.itsdev.payroll.repository;

import com.itsdev.payroll.entity.OrgSetupSteps;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrgSetupStepsRepository extends JpaRepository<OrgSetupSteps, Long> {
    Optional<OrgSetupSteps> findByOrganizationId(String organizationId);
}
