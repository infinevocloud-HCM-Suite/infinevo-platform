package com.itsdev.payroll.repository.employeereimbursement;

import com.itsdev.payroll.entity.employeereimbursement.EmployeeReimbursementRequest;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementPaymentStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for EmployeeReimbursementRequest.
 *
 * Security contract:
 *  - Employee query methods filter by both employeeId AND organizationId.
 *  - Admin query methods filter by organizationId to maintain strict multi-tenant isolation.
 */
@Repository
public interface EmployeeReimbursementRequestRepository extends JpaRepository<EmployeeReimbursementRequest, Long> {

    // ==================== Employee queries ====================

    /**
     * Retrieve all reimbursement requests for a specific employee within an organization,
     * ordered by creation time descending (newest first).
     *
     * @param employeeId     the JWT subject (employee's Keycloak ID)
     * @param organizationId the organization ID from the request header
     * @return list of reimbursement requests for this employee
     */
    List<EmployeeReimbursementRequest> findByEmployeeIdAndOrganizationIdOrderByCreatedAtDesc(
            String employeeId, String organizationId);

    /**
     * Retrieve a single reimbursement request by ID, but only if it belongs to the
     * specified employee within the organization. Returns empty if the record does not
     * exist OR belongs to a different employee — preventing data leakage.
     *
     * @param id             the reimbursement request ID
     * @param employeeId     the JWT subject
     * @param organizationId the organization ID from the request header
     * @return the request if it exists and belongs to this employee, empty otherwise
     */
    Optional<EmployeeReimbursementRequest> findByIdAndEmployeeIdAndOrganizationId(
            Long id, String employeeId, String organizationId);

    // ==================== Admin queries ====================

    /**
     * Retrieve all reimbursement requests within an organization,
     * ordered by creation time descending (newest first).
     *
     * @param organizationId the organization ID from the request header
     * @return list of all reimbursement requests in this organization
     */
    List<EmployeeReimbursementRequest> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    /**
     * Retrieve a single reimbursement request by ID within an organization.
     *
     * @param id             the reimbursement request ID
     * @param organizationId the organization ID from the request header
     * @return the reimbursement request if found in this organization
     */
    Optional<EmployeeReimbursementRequest> findByIdAndOrganizationId(Long id, String organizationId);

    // ==================== PayRun integration queries ====================

    /**
     * Find all approved reimbursement requests for a specific employee in an organization
     * for a given reimbursement month with the specified payment status.
     * Used during payrun creation to fetch UNPAID approved reimbursements for the processing period.
     */
    List<EmployeeReimbursementRequest> findByEmployeeIdAndOrganizationIdAndReimbursementMonthAndStatusAndPaymentStatus(
            String employeeId, String organizationId, String reimbursementMonth,
            ReimbursementStatus status, ReimbursementPaymentStatus paymentStatus);

    /**
     * Find all reimbursement requests in an organization for a given month with the specified payment status.
     * Used during payrun approve/reject/delete to bulk update statuses.
     */
    List<EmployeeReimbursementRequest> findByOrganizationIdAndReimbursementMonthAndPaymentStatus(
            String organizationId, String reimbursementMonth, ReimbursementPaymentStatus paymentStatus);
}
