package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ProofOfInvestmentDTO;

public interface ProofOfInvestmentService {
    ProofOfInvestmentDTO getProofOfInvestment(String organizationId);
    ProofOfInvestmentDTO updateProofOfInvestment(String organizationId, ProofOfInvestmentDTO dto);
}
