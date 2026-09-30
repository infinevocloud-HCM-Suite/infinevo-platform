package com.infinevo.payroll.reimbursement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service contract for reimbursement claim workflows (W-35.1).
 */
public interface ReimbursementClaimService {

    ReimbursementClaimResponse submit(ReimbursementClaimRequest request);

    List<ReimbursementClaimResponse> listOwn();

    ReimbursementClaimResponse getOwn(UUID id);

    Page<ReimbursementClaimResponse> list(
            UUID employeeId, ClaimStatus status, LocalDate from, LocalDate to, Pageable pageable);

    ReimbursementClaimResponse get(UUID id);
}
