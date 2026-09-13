package com.itsdev.payroll.repository.salarycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, Long> {
    List<Reimbursement> findByOrganization(Organization organization);
    Optional<Reimbursement> findByReimbursementIdAndOrganization(String reimbursementId, Organization organization);
    List<Reimbursement> findByOrganizationAndIsDeletedFalse(Organization organization);


    Optional<Reimbursement> findByReimbursementId(String reimbursementId);




}