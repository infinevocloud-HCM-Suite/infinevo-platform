package com.itsdev.payroll.service.payRun.oneTImePayout;


//import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutResponseDTO;

import java.util.List;

public interface OneTimePayoutService {

    // Step 1: Create draft payout (earningId + payDate, no employees yet)
    OneTimePayoutResponseDTO createDraftPayout(String organizationId, OneTimePayoutRequestDTO requestDto);

    // Step 2: Add employees to payout (amount, taxes, etc.)
    List<OneTimePayoutResponseDTO> addEmployeesToPayout(String organizationId, OneTimePayoutRequestDTO requestDto);

    // Fetch a single payout by id
    OneTimePayoutResponseDTO getPayout(String organizationId, Long payoutId);

    // Fetch all payouts for an org
    List<OneTimePayoutResponseDTO> getAllPayouts(String organizationId);

    // Fetch all payouts for a given earning component
    List<OneTimePayoutResponseDTO> getPayoutsByEarning(String organizationId, String earningId);

    // Fetch all payouts for a given employee
    List<OneTimePayoutResponseDTO> getPayoutsByEmployee(String organizationId, Long employeeId);

    // Delete a payout
    void deletePayout(String organizationId, Long payoutId);

   List<OneTimePayoutResponseDTO> importEmployeesToPayout(String organizationId, OneTimePayoutImportRequestDTO requestDto);

    OneTimePayoutResponseDTO updatePayout(String organizationId, Long payoutId, OneTimePayoutRequestDTO requestDto);


}

