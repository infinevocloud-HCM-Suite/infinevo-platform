package com.itsdev.payroll.repository.payRun.oneTImePayout;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payRun.oneTimePayout.OneTimePayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OneTimePayoutRepository extends JpaRepository<OneTimePayout, Long> {

    // Find all payouts for an organization
    List<OneTimePayout> findByOrganization(Organization org);

    // Find payout by DB id and organization (multi-org safety)
    Optional<OneTimePayout> findByIdAndOrganization(Long id, Organization org);

    // Find payouts by earningId (business id from Earning entity) and org
    List<OneTimePayout> findByEarning_EarningIdAndOrganization(String earningId, Organization org);

    // Find payouts by employee id and org
    List<OneTimePayout> findByEmployee_IdAndOrganization(Long employeeId, Organization org);

    // Find payouts by pay date and org
    List<OneTimePayout> findByPayDateAndOrganization(LocalDate payDate, Organization org);

    // Optional: prevent duplicates → search by earningId + employee + org
    Optional<OneTimePayout> findByEarning_EarningIdAndEmployee_IdAndOrganization(
            String earningId,
            Long employeeId,
            Organization org
    );



}
