package com.itsdev.payroll.repository;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.PaySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayScheduleRepository extends JpaRepository<PaySchedule, Long> {
    Optional<PaySchedule> findByOrganization(Organization organization);
    Optional<PaySchedule> findByPayScheduleId(String payScheduleId);
    Optional<PaySchedule> findByOrganizationAndPayScheduleId(Organization organization, String payScheduleId);
    boolean existsByOrganization_OrganizationId(String organizationId);
    boolean existsByOrganization(Organization organization);

}