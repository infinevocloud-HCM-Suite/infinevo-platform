package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.EmployeeResponse;
import java.util.List;
import java.util.UUID;

/**
 * Service managing job offers and one-click conversion into core employees (W-112).
 */
public interface JobOfferService {

    JobOfferResponse createOffer(UUID tenantId, UUID applicationId, JobOfferRequest request, String createdBy);

    JobOfferResponse getOffer(UUID tenantId, UUID id);

    List<JobOfferResponse> listOffers(UUID tenantId, JobOfferStatus status);

    JobOfferResponse sendOffer(UUID tenantId, UUID id, String updatedBy);

    JobOfferResponse acceptOffer(UUID tenantId, UUID id, String updatedBy);

    JobOfferResponse declineOffer(UUID tenantId, UUID id, String reason, String updatedBy);

    EmployeeResponse convertToEmployee(UUID tenantId, UUID id, ConvertToEmployeeRequest request, String actor);
}
