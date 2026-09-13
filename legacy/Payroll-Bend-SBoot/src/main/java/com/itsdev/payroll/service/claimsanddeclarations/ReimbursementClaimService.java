package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ReimbursementClaimDTO;

public interface ReimbursementClaimService {
    ReimbursementClaimDTO getReimbursementClaim(String organizationId);
    ReimbursementClaimDTO updateReimbursementClaim(String organizationId, ReimbursementClaimDTO dto);
}
