package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.FBPDTO;

public interface FBPService {
    FBPDTO getFBP(String organizationId);
    FBPDTO updateFBP(String organizationId, FBPDTO dto);
}
